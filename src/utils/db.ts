import { AudioRecording, CCTVSettings, CCTVEvent } from '../types';

const DB_NAME = 'sound_sentinel_cctv_db';
const DB_VERSION = 1;

const STORES = {
  RECORDINGS: 'recordings',
  EVENTS: 'events',
  SETTINGS: 'settings',
};

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB not supported in this environment'));
      return;
    }

    const request = window.indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;

      if (!db.objectStoreNames.contains(STORES.RECORDINGS)) {
        const recStore = db.createObjectStore(STORES.RECORDINGS, { keyPath: 'id' });
        recStore.createIndex('timestamp', 'timestamp', { unique: false });
        recStore.createIndex('isProtected', 'isProtected', { unique: false });
      }

      if (!db.objectStoreNames.contains(STORES.EVENTS)) {
        const eventStore = db.createObjectStore(STORES.EVENTS, { keyPath: 'id' });
        eventStore.createIndex('timestamp', 'timestamp', { unique: false });
      }

      if (!db.objectStoreNames.contains(STORES.SETTINGS)) {
        db.createObjectStore(STORES.SETTINGS, { keyPath: 'key' });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function saveRecordingToDB(recording: AudioRecording): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORES.RECORDINGS, 'readwrite');
    const store = tx.objectStore(STORES.RECORDINGS);
    const req = store.put(recording);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function getAllRecordingsFromDB(): Promise<AudioRecording[]> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORES.RECORDINGS, 'readonly');
    const store = tx.objectStore(STORES.RECORDINGS);
    const index = store.index('timestamp');
    // Open cursor in reverse order (newest first)
    const req = index.openCursor(null, 'prev');
    const results: AudioRecording[] = [];

    req.onsuccess = (e) => {
      const cursor = (e.target as IDBRequest<IDBCursorWithValue>).result;
      if (cursor) {
        results.push(cursor.value);
        cursor.continue();
      } else {
        resolve(results);
      }
    };
    req.onerror = () => reject(req.error);
  });
}

export async function deleteRecordingFromDB(id: string): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORES.RECORDINGS, 'readwrite');
    const store = tx.objectStore(STORES.RECORDINGS);
    const req = store.delete(id);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function deleteMultipleRecordingsFromDB(ids: string[]): Promise<number> {
  if (ids.length === 0) return 0;
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORES.RECORDINGS, 'readwrite');
    const store = tx.objectStore(STORES.RECORDINGS);
    let count = 0;

    for (const id of ids) {
      store.delete(id);
      count++;
    }

    tx.oncomplete = () => resolve(count);
    tx.onerror = () => reject(tx.error);
  });
}

export async function toggleProtectRecordingInDB(id: string, isProtected: boolean): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORES.RECORDINGS, 'readwrite');
    const store = tx.objectStore(STORES.RECORDINGS);
    const getReq = store.get(id);

    getReq.onsuccess = () => {
      const item = getReq.result as AudioRecording | undefined;
      if (!item) {
        resolve();
        return;
      }
      item.isProtected = isProtected;
      const putReq = store.put(item);
      putReq.onsuccess = () => resolve();
      putReq.onerror = () => reject(putReq.error);
    };
    getReq.onerror = () => reject(getReq.error);
  });
}

export async function clearAllRecordingsFromDB(): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORES.RECORDINGS, 'readwrite');
    const store = tx.objectStore(STORES.RECORDINGS);
    const req = store.clear();
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function saveSettingsToDB(settings: CCTVSettings): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORES.SETTINGS, 'readwrite');
    const store = tx.objectStore(STORES.SETTINGS);
    const req = store.put({ key: 'current_settings', value: settings });
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function getSettingsFromDB(): Promise<CCTVSettings | null> {
  try {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORES.SETTINGS, 'readonly');
      const store = tx.objectStore(STORES.SETTINGS);
      const req = store.get('current_settings');
      req.onsuccess = () => {
        resolve(req.result ? req.result.value : null);
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    return null;
  }
}

export async function logEventToDB(event: CCTVEvent): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction(STORES.EVENTS, 'readwrite');
    const store = tx.objectStore(STORES.EVENTS);
    store.put(event);
  } catch (err) {
    console.warn('Failed to log event', err);
  }
}

export async function getRecentEventsFromDB(limit: number = 50): Promise<CCTVEvent[]> {
  try {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORES.EVENTS, 'readonly');
      const store = tx.objectStore(STORES.EVENTS);
      const index = store.index('timestamp');
      const req = index.openCursor(null, 'prev');
      const results: CCTVEvent[] = [];

      req.onsuccess = (e) => {
        const cursor = (e.target as IDBRequest<IDBCursorWithValue>).result;
        if (cursor && results.length < limit) {
          results.push(cursor.value);
          cursor.continue();
        } else {
          resolve(results);
        }
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    return [];
  }
}
