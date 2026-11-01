import { RetentionMode, StorageStats } from '../types';

/**
 * Calculates decibels (dBFS) from root mean square amplitude.
 * Returns a value typically between -80 dB and 0 dB.
 */
export function calculateDecibelsFromRMS(rms: number): number {
  if (rms <= 0.0001) return -80;
  const db = 20 * Math.log10(rms);
  return Math.max(-80, Math.min(0, Math.round(db * 10) / 10));
}

/**
 * Maps raw dBFS (-80 dB to 0 dB) to a human-friendly 0 - 100 sound level score.
 */
export function mapDecibelsToScale(db: number): number {
  if (db <= -70) return 0;
  if (db >= -5) return 100;
  // Linear scaling from -70 dB (0) to -5 dB (100)
  const score = ((db + 70) / 65) * 100;
  return Math.max(0, Math.min(100, Math.round(score)));
}

/**
 * Checks if sound level exceeds the sensitivity threshold.
 */
export function isSoundDetected(soundLevel: number, threshold: number): boolean {
  return soundLevel >= threshold;
}

/**
 * Computes storage usage, percentage, and warning flags.
 */
export function calculateStorageStats(
  recordings: { sizeBytes: number; isProtected?: boolean }[],
  quotaBytes: number,
  warningThresholdPercent: number = 85
): StorageStats {
  const safeQuota = Math.max(1024 * 1024, quotaBytes); // At least 1 MB
  let usedBytes = 0;
  let protectedCount = 0;

  for (const r of recordings) {
    usedBytes += r.sizeBytes || 0;
    if (r.isProtected) protectedCount++;
  }

  const rawPercent = (usedBytes / safeQuota) * 100;
  const usedPercentage = Math.round(Math.min(100, rawPercent) * 10) / 10;
  const isNearFull = rawPercent >= warningThresholdPercent;
  const isFull = rawPercent >= 98;

  return {
    usedBytes,
    quotaBytes: safeQuota,
    usedPercentage,
    isNearFull,
    isFull,
    recordingsCount: recordings.length,
    protectedCount,
  };
}

/**
 * Identifies oldest, unprotected recordings that must be deleted to free up target bytes.
 * Sorts from oldest timestamp to newest. Protected recordings are never pruned.
 */
export function identifyOldestToPrune(
  recordings: { id: string; sizeBytes: number; isProtected: boolean; timestamp: number }[],
  bytesNeeded: number
): string[] {
  if (bytesNeeded <= 0) return [];

  // Filter unprotected and sort by timestamp ascending (oldest first)
  const candidateRecordings = recordings
    .filter((r) => !r.isProtected)
    .sort((a, b) => a.timestamp - b.timestamp);

  const idsToPrune: string[] = [];
  let freedBytes = 0;

  for (const rec of candidateRecordings) {
    idsToPrune.push(rec.id);
    freedBytes += rec.sizeBytes || 0;
    if (freedBytes >= bytesNeeded) {
      break;
    }
  }

  return idsToPrune;
}

/**
 * Returns retention time in milliseconds based on user preference.
 * Returns Infinity if mode is 'never'.
 */
export function getRetentionDurationMs(mode: RetentionMode, customHours: number = 24): number {
  const HOUR = 60 * 60 * 1000;
  switch (mode) {
    case '1h':
      return 1 * HOUR;
    case '6h':
      return 6 * HOUR;
    case '24h':
      return 24 * HOUR;
    case '3d':
      return 3 * 24 * HOUR;
    case '7d':
      return 7 * 24 * HOUR;
    case '30d':
      return 30 * 24 * HOUR;
    case 'custom':
      return Math.max(1, customHours) * HOUR;
    case 'never':
    default:
      return Infinity;
  }
}

/**
 * Identifies recordings older than retention duration.
 * Protected recordings are preserved.
 */
export function identifyExpiredRecordings(
  recordings: { id: string; timestamp: number; isProtected: boolean }[],
  retentionDurationMs: number,
  now: number = Date.now()
): string[] {
  if (retentionDurationMs === Infinity) return [];

  return recordings
    .filter((r) => !r.isProtected && now - r.timestamp > retentionDurationMs)
    .map((r) => r.id);
}

/**
 * Formats byte counts into human readable strings (KB, MB, GB).
 */
export function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const val = bytes / Math.pow(1024, i);
  return `${val.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

/**
 * Formats duration in seconds into MM:SS or HH:MM:SS.
 */
export function formatDuration(seconds: number): string {
  const sec = Math.max(0, Math.round(seconds));
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  if (m >= 60) {
    const h = Math.floor(m / 60);
    const remM = m % 60;
    return `${h}:${remM.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}
