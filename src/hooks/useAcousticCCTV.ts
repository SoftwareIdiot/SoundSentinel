import { useState, useEffect, useRef, useCallback } from 'react';
import {
  AudioRecording,
  CCTVSettings,
  CCTVEvent,
  StorageStats,
  ThreatLevel,
} from '../types';
import {
  calculateDecibelsFromRMS,
  mapDecibelsToScale,
  calculateStorageStats,
  identifyOldestToPrune,
  identifyExpiredRecordings,
  getRetentionDurationMs,
} from '../utils/cctvLogic';
import {
  saveRecordingToDB,
  getAllRecordingsFromDB,
  deleteRecordingFromDB,
  deleteMultipleRecordingsFromDB,
  toggleProtectRecordingInDB,
  clearAllRecordingsFromDB,
  saveSettingsToDB,
  getSettingsFromDB,
  logEventToDB,
  getRecentEventsFromDB,
} from '../utils/db';
import { notificationService } from '../utils/notifications';
import { classifyAudioRecording } from '../utils/audioClassifier';
import { backgroundMonitor, isAndroidNativeApp, NativeRecordingInfo } from '../native/backgroundMonitor';

const DEFAULT_LAPTOP_SERVER_URL = import.meta.env.VITE_LAPTOP_SERVER_URL?.trim() || '';

const DEFAULT_SETTINGS: CCTVSettings = {
  isArmed: false,
  sensitivityThreshold: 45, // 0-100 scale (~ -40 dB)
  postRollSeconds: 10, // 10s post-roll as requested
  storageQuotaMB: 100, // 100 MB default quota
  warningThresholdPercent: 85,
  retentionMode: '24h',
  customRetentionHours: 24,
  autoPruneOnFull: true,
  enableNotifications: true,
  enableVibration: true,
  enableScreenWakeLock: true,
  enableKeepAliveAudio: true,
  enableAIClassification: true, // AI audio classification enabled by default
  apiBaseUrl: DEFAULT_LAPTOP_SERVER_URL,
  apiAccessToken: '',
};

function nativeWavToBlob(base64: string): Blob {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: 'audio/wav' });
}

export function useAcousticCCTV() {
  const [settings, setSettings] = useState<CCTVSettings>(DEFAULT_SETTINGS);
  const [recordings, setRecordings] = useState<AudioRecording[]>([]);
  const [events, setEvents] = useState<CCTVEvent[]>([]);
  const [storageStats, setStorageStats] = useState<StorageStats>({
    usedBytes: 0,
    quotaBytes: 100 * 1024 * 1024,
    usedPercentage: 0,
    isNearFull: false,
    isFull: false,
    recordingsCount: 0,
    protectedCount: 0,
  });

  // Real-time audio surveillance states
  const [currentDecibels, setCurrentDecibels] = useState<number>(-80);
  const [currentSoundLevel, setCurrentSoundLevel] = useState<number>(0);
  const [isRecording, setIsRecording] = useState<boolean>(false);
  const [silenceCountdown, setSilenceCountdown] = useState<number>(10);
  const [recordingDuration, setRecordingDuration] = useState<number>(0);
  const [triggersInCurrentSession, setTriggersInCurrentSession] = useState<number>(0);
  const [isMicActive, setIsMicActive] = useState<boolean>(false);
  const [pendingNativeRecordings, setPendingNativeRecordings] = useState<number>(0);
  const [micError, setMicError] = useState<string | null>(null);
  const [storageWarningActive, setStorageWarningActive] = useState<boolean>(false);

  // Audio nodes and references
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const preRollChunksRef = useRef<Blob[]>([]);
  const finalizingRecordingRef = useRef(false);
  const animationFrameRef = useRef<number | null>(null);
  const smoothedDbRef = useRef(-80);
  const noiseFloorDbRef = useRef(-60);
  const aboveGateFramesRef = useRef(0);

  // Mutable state refs to prevent stale closures in audio processing loops
  const settingsRef = useRef<CCTVSettings>(settings);
  settingsRef.current = settings;
  const recordingsRef = useRef<AudioRecording[]>(recordings);
  recordingsRef.current = recordings;

  const recordingStateRef = useRef({
    isRecording: false,
    startTime: 0,
    lastSoundTime: 0,
    peakLevel: 0,
    samplesCount: 0,
    totalLevel: 0,
    triggersCount: 0,
    waveformPoints: [] as number[],
  });

  const nativeSyncInProgressRef = useRef(false);

  // Refresh recordings & storage stats from IndexedDB
  const refreshRecordings = useCallback(async () => {
    try {
      const recs = await getAllRecordingsFromDB();
      setRecordings(recs);
      const quotaBytes = settingsRef.current.storageQuotaMB * 1024 * 1024;
      const stats = calculateStorageStats(
        recs,
        quotaBytes,
        settingsRef.current.warningThresholdPercent
      );
      setStorageStats(stats);

      if (stats.isNearFull && !storageWarningActive) {
        setStorageWarningActive(true);
        if (settingsRef.current.enableNotifications) {
          notificationService.sendStorageWarningNotification(
            stats.usedPercentage,
            (stats.usedBytes / (1024 * 1024)).toFixed(1),
            settingsRef.current.storageQuotaMB
          );
        }
      } else if (!stats.isNearFull) {
        setStorageWarningActive(false);
      }
    } catch (e) {
      console.error('Error refreshing recordings', e);
    }
  }, [storageWarningActive]);

  // Refresh event logs
  const refreshEvents = useCallback(async () => {
    try {
      const evts = await getRecentEventsFromDB(50);
      setEvents(evts);
    } catch (e) {
      console.error('Error refreshing events', e);
    }
  }, []);

  const addEvent = useCallback(
    async (
      type: CCTVEvent['type'],
      message: string,
      decibels?: number,
      tag?: string,
      threatLevel?: ThreatLevel,
      confidence?: number
    ) => {
      const newEvent: CCTVEvent = {
        id: `evt-${Date.now()}-${Math.random().toString(36).substr(2, 5)}`,
        timestamp: Date.now(),
        type,
        message,
        decibels,
        tag,
        threatLevel,
        confidence,
      };
      await logEventToDB(newEvent);
      setEvents((prev) => [newEvent, ...prev.slice(0, 49)]);
    },
    []
  );

  const syncNativeRecordings = useCallback(async () => {
    if (!isAndroidNativeApp() || nativeSyncInProgressRef.current) return;
    nativeSyncInProgressRef.current = true;
    try {
      const result = await backgroundMonitor.listPendingRecordings();
      const pending = result.recordings || [];
      if (!pending.length) return;
      const existingIds = new Set((await getAllRecordingsFromDB()).map((recording) => recording.id));
      for (const info of pending as NativeRecordingInfo[]) {
        if (existingIds.has(info.id)) {
          await backgroundMonitor.acknowledgeRecording({ id: info.id });
          continue;
        }
        const { base64 } = await backgroundMonitor.readRecording({ id: info.id });
        const blob = nativeWavToBlob(base64);
        const recording: AudioRecording = {
          ...info,
          dateString: new Date(info.timestamp).toLocaleString(),
          blob,
          audioUrl: URL.createObjectURL(blob),
          sizeBytes: blob.size,
          isProtected: false,
        };
        await saveRecordingToDB(recording);
        await addEvent('detection', `Background sound detected and recorded (${info.duration}s)`, info.peakDecibels);
        await addEvent('saved', `Background recording imported (${info.duration}s, peak ${info.peakDecibels})`, info.peakDecibels);
        if (settingsRef.current.enableAIClassification) {
          const classification = info.classification || await classifyAudioRecording(
            recording,
            undefined,
            settingsRef.current.apiBaseUrl,
            settingsRef.current.apiAccessToken
          );
          recording.classification = classification;
          await saveRecordingToDB(recording);
          await addEvent(
            'classified',
            `Classified: ${classification.tag} (${Math.round(classification.confidence * 100)}% confidence) - ${classification.explanation}`,
            info.peakDecibels,
            classification.tag,
            classification.threatLevel,
            classification.confidence
          );
        }
        await backgroundMonitor.acknowledgeRecording({ id: info.id });
      }
      await refreshRecordings();
      await refreshEvents();
    } catch (error) {
      console.warn('Could not import native background recordings', error);
    } finally {
      nativeSyncInProgressRef.current = false;
    }
  }, [addEvent, refreshEvents, refreshRecordings]);

  // Initialize settings and database on mount
  useEffect(() => {
    async function init() {
      const saved = await getSettingsFromDB();
      let nativeRunning: boolean | undefined;
      if (isAndroidNativeApp()) {
        try {
          nativeRunning = (await backgroundMonitor.getStatus()).running;
        } catch { /* Native bridge may be starting during the first render. */ }
      }
      const merged: CCTVSettings = {
        ...DEFAULT_SETTINGS,
        ...(saved || {}),
        enableAIClassification: saved?.enableAIClassification !== false,
        apiBaseUrl: saved?.apiBaseUrl || DEFAULT_LAPTOP_SERVER_URL,
        isArmed: nativeRunning ?? saved?.isArmed ?? false,
      };
      setSettings(merged);
      settingsRef.current = merged;
      await refreshRecordings();
      await refreshEvents();
      await syncNativeRecordings();
    }
    init();

    let nativePollCount = 0;
    const nativePoll = isAndroidNativeApp()
      ? setInterval(async () => {
          try {
            const state = await backgroundMonitor.getStatus();
            setIsMicActive(state.capturing);
            setCurrentSoundLevel(state.soundLevel);
            setCurrentDecibels(state.decibels);
            setIsRecording(state.recording);
            setRecordingDuration(state.recordingDuration || 0);
            setSilenceCountdown(state.silenceRemaining ?? settingsRef.current.postRollSeconds);
            setPendingNativeRecordings(state.pendingRecordings || 0);
            setMicError(state.error || null);
            if (state.running !== settingsRef.current.isArmed) {
              const updated = { ...settingsRef.current, isArmed: state.running };
              settingsRef.current = updated;
              setSettings(updated);
              await saveSettingsToDB(updated);
            }
          } catch (error) {
            console.warn('Native monitor status unavailable', error);
          }
          nativePollCount += 1;
          if (nativePollCount % 5 === 0) await syncNativeRecordings();
        }, 1000)
      : undefined;

    // Listen to visibility changes (running in background)
    const handleVisibility = () => {
      const hidden = document.visibilityState === 'hidden';
      if (hidden && settingsRef.current.isArmed && !isAndroidNativeApp()) {
        addEvent('system', 'App moved to the background while armed; continued monitoring is not guaranteed');
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      if (nativePoll) clearInterval(nativePoll);
    };
  }, [refreshRecordings, refreshEvents, addEvent, syncNativeRecordings]);

  // Periodic retention and circular buffer auto-prune sweep
  useEffect(() => {
    const interval = setInterval(async () => {
      const currentRecs = recordingsRef.current;
      if (currentRecs.length === 0) return;

      const retentionMs = getRetentionDurationMs(
        settingsRef.current.retentionMode,
        settingsRef.current.customRetentionHours
      );

      // 1. Retention sweep
      const expiredIds = identifyExpiredRecordings(currentRecs, retentionMs, Date.now());
      if (expiredIds.length > 0) {
        await deleteMultipleRecordingsFromDB(expiredIds);
        addEvent('pruned', `Auto-purged ${expiredIds.length} recording(s) older than retention limit`);
        refreshRecordings();
      }

      // 2. Storage quota overflow sweep (auto-prune if full and ignored)
      const quotaBytes = settingsRef.current.storageQuotaMB * 1024 * 1024;
      const stats = calculateStorageStats(currentRecs, quotaBytes, settingsRef.current.warningThresholdPercent);
      if (stats.isFull && settingsRef.current.autoPruneOnFull) {
        const bytesToFree = stats.usedBytes - quotaBytes * 0.8; // Free down to 80%
        const toPrune = identifyOldestToPrune(currentRecs, bytesToFree);
        if (toPrune.length > 0) {
          await deleteMultipleRecordingsFromDB(toPrune);
          addEvent('pruned', `Storage full: automatically pruned ${toPrune.length} oldest file(s) to make room`);
          refreshRecordings();
        }
      }
    }, 15000); // Check every 15 seconds

    return () => clearInterval(interval);
  }, [refreshRecordings, addEvent]);

  // Finalize and save recorded session
  const finalizeRecording = useCallback(async () => {
    const state = recordingStateRef.current;
    if (!state.isRecording) return;

    state.isRecording = false;
    setIsRecording(false);
    setSilenceCountdown(settingsRef.current.postRollSeconds);

    const recorder = mediaRecorderRef.current;
    if (recorder && recorder.state === 'recording') {
      finalizingRecordingRef.current = true;
      await new Promise<void>((resolve) => {
        const finish = () => {
          window.clearTimeout(timeout);
          finalizingRecordingRef.current = false;
          resolve();
        };
        const timeout = window.setTimeout(finish, 1500);
        recorder.addEventListener('dataavailable', finish, { once: true });
        try {
          recorder.requestData();
        } catch {
          finish();
        }
      });
    }

    const durationSec = Math.max(1, Math.round((Date.now() - state.startTime) / 100) / 10);
    const avgDb = state.samplesCount > 0 ? Math.round(state.totalLevel / state.samplesCount) : state.peakLevel;
    const peakDb = state.peakLevel;
    const triggersCount = state.triggersCount;
    const sampleWaveform = state.waveformPoints.slice(0, 40);
    const chunks = audioChunksRef.current.splice(0);
    const mimeType = chunks.find((chunk) => chunk.type)?.type || recorder?.mimeType || 'audio/webm';
    const finalBlob = new Blob(chunks, { type: mimeType });
    if (finalBlob.size === 0) {
      await addEvent('warning', 'Recording had no audio data and was discarded. Check microphone permission and try again.');
      return;
    }

    const sizeBytes = finalBlob.size;
      const recId = `rec-${Date.now()}`;
      const newRecording: AudioRecording = {
        id: recId,
        timestamp: state.startTime,
        dateString: new Date(state.startTime).toLocaleString(),
        duration: durationSec,
        blob: finalBlob,
        audioUrl: URL.createObjectURL(finalBlob),
        sizeBytes,
        peakDecibels: peakDb,
        avgDecibels: avgDb,
        triggersCount,
        isProtected: false,
        waveformData: sampleWaveform.length > 0 ? sampleWaveform : [40, 60, 80, 50, 30, 20],
      };

      // Check Quota and Auto-Prune if adding this exceeds storage
      const currentRecs = recordingsRef.current;
      const quotaBytes = settingsRef.current.storageQuotaMB * 1024 * 1024;
      const projectedTotal = currentRecs.reduce((acc, r) => acc + (r.sizeBytes || 0), 0) + sizeBytes;

      if (projectedTotal > quotaBytes && settingsRef.current.autoPruneOnFull) {
        const bytesNeeded = projectedTotal - quotaBytes + 1024 * 1024; // extra 1MB buffer
        const oldestIds = identifyOldestToPrune(currentRecs, bytesNeeded);
        if (oldestIds.length > 0) {
          await deleteMultipleRecordingsFromDB(oldestIds);
          addEvent('pruned', `Auto-overwritten ${oldestIds.length} oldest file(s) to store new recording`);
        }
      }

      await saveRecordingToDB(newRecording);
      await addEvent('saved', `Recording saved (${durationSec}s, level ${peakDb}/100, re-triggered ${triggersCount}x)`, peakDb);
      await refreshRecordings();

      // Run AI audio classification asynchronously
      if (settingsRef.current.enableAIClassification !== false) {
        classifyAudioRecording(newRecording, newRecording.notes, settingsRef.current.apiBaseUrl, settingsRef.current.apiAccessToken)
          .then(async (classification) => {
            newRecording.classification = classification;
            await saveRecordingToDB(newRecording);
            await addEvent(
              'classified',
              `Classified: ${classification.tag} (${Math.round(classification.confidence * 100)}% confidence) - ${classification.explanation}`,
              peakDb,
              classification.tag,
              classification.threatLevel,
              classification.confidence
            );
            await refreshRecordings();
          })
          .catch((err) => {
            console.warn('AI Audio Classification error:', err);
          });
      }
  }, [addEvent, refreshRecordings]);

  // Audio analysis and sound surveillance loop
  const startSurveillanceLoop = useCallback(() => {
    const analyser = analyserRef.current;
    if (!analyser) return;

    const dataArray = new Uint8Array(analyser.frequencyBinCount);
    const timeDomainArray = new Float32Array(analyser.fftSize);

    const tick = () => {
      analyser.getByteFrequencyData(dataArray);
      analyser.getFloatTimeDomainData(timeDomainArray);

      // Combine an adaptive RMS gate with transient and high-frequency energy cues.
      let sumSquares = 0;
      let peakAmplitude = 0;
      let highFrequencyEnergy = 0;
      let totalFrequencyEnergy = 0;
      const sampleRate = audioContextRef.current?.sampleRate ?? 44100;
      for (let i = 0; i < dataArray.length; i++) {
        const frequency = (i * sampleRate) / analyser.fftSize;
        const binDb = analyser.minDecibels + (dataArray[i] / 255) * (analyser.maxDecibels - analyser.minDecibels);
        const amplitude = Math.pow(10, binDb / 20);
        const energy = amplitude * amplitude;
        totalFrequencyEnergy += energy;
        if (frequency >= 2500) highFrequencyEnergy += energy;
      }
      for (let i = 0; i < timeDomainArray.length; i++) {
        sumSquares += timeDomainArray[i] * timeDomainArray[i];
        peakAmplitude = Math.max(peakAmplitude, Math.abs(timeDomainArray[i]));
      }
      const rms = Math.sqrt(sumSquares / timeDomainArray.length);
      const rawDb = calculateDecibelsFromRMS(rms);
      const peakDb = calculateDecibelsFromRMS(peakAmplitude);
      smoothedDbRef.current += (rawDb - smoothedDbRef.current) * (rawDb > smoothedDbRef.current ? 0.45 : 0.12);
      const threshold = settingsRef.current.sensitivityThreshold;
      const postRoll = settingsRef.current.postRollSeconds;
      const state = recordingStateRef.current;
      const now = Date.now();
      const absoluteGateDb = -70 + threshold * 0.65;
      const adaptiveGateDb = Math.max(absoluteGateDb, noiseFloorDbRef.current + 6);
      const releaseGateDb = Math.max(absoluteGateDb - 4, noiseFloorDbRef.current + 3);
      const highFrequencyRatio = highFrequencyEnergy / Math.max(1e-12, totalFrequencyEnergy);
      const crestDb = peakDb - rawDb;
      const impulse = peakDb >= Math.max(absoluteGateDb + 8, noiseFloorDbRef.current + 14)
        && crestDb >= 7 && highFrequencyRatio >= 0.18;
      const aboveGate = smoothedDbRef.current >= (state.isRecording ? releaseGateDb : adaptiveGateDb);
      if (!state.isRecording && aboveGate) aboveGateFramesRef.current += 1;
      else if (!aboveGate) aboveGateFramesRef.current = 0;
      const hasSound = impulse || (state.isRecording ? aboveGate : aboveGateFramesRef.current >= 2);
      if (!state.isRecording && !hasSound) {
        noiseFloorDbRef.current = Math.max(-72, Math.min(-35,
          noiseFloorDbRef.current + (rawDb - noiseFloorDbRef.current) * 0.015));
      }
      const soundScore = mapDecibelsToScale(smoothedDbRef.current);
      const eventScore = impulse ? Math.max(soundScore, mapDecibelsToScale(peakDb)) : soundScore;
      setCurrentDecibels(smoothedDbRef.current);
      setCurrentSoundLevel(eventScore);

      if (hasSound) {
        // Sound is active right now!
        if (!state.isRecording) {
          // Trigger new recording session!
          state.isRecording = true;
          state.startTime = now;
          state.lastSoundTime = now;
          state.peakLevel = eventScore;
          state.samplesCount = 1;
          state.totalLevel = soundScore;
          state.triggersCount = 1;
          state.waveformPoints = [soundScore];

          setIsRecording(true);
          setSilenceCountdown(postRoll);
          setTriggersInCurrentSession(1);
          setRecordingDuration(0);

          audioChunksRef.current = preRollChunksRef.current.splice(0);

          // Trigger immediate notification & alarm
          if (settingsRef.current.enableNotifications) {
            notificationService.sendDetectionNotification(eventScore, 1);
          }
          if (settingsRef.current.enableVibration) {
            notificationService.triggerHaptic();
          }
          notificationService.playAlertBeep();
          addEvent('detection', `Sound detected (level ${eventScore}/100). Recording started`, eventScore);
        } else {
          // Already recording! Re-trigger extension!
          const timeSinceLastSound = now - state.lastSoundTime;
          // If there was a silence gap >= 500ms, count as re-trigger
          if (timeSinceLastSound > 500) {
            state.triggersCount += 1;
            setTriggersInCurrentSession(state.triggersCount);
            addEvent('extension', `Sound re-detected (level ${eventScore}/100). Post-roll restarted`, eventScore);
          }
          state.lastSoundTime = now;
          state.peakLevel = Math.max(state.peakLevel, eventScore);
          state.totalLevel += eventScore;
          state.samplesCount += 1;
          if (state.waveformPoints.length < 50) {
            state.waveformPoints.push(eventScore);
          }
          setSilenceCountdown(postRoll);
        }
        setRecordingDuration(Math.round((now - state.startTime) / 100) / 10);
      } else {
        // Silence detected right now
        if (state.isRecording) {
          state.samplesCount += 1;
          state.totalLevel += soundScore;
          const elapsedSinceLastSound = (now - state.lastSoundTime) / 1000;
          const remainingCountdown = Math.max(0, postRoll - elapsedSinceLastSound);
          setSilenceCountdown(Math.round(remainingCountdown * 10) / 10);
          setRecordingDuration(Math.round((now - state.startTime) / 1000));

          // 10 seconds of silence have elapsed! Cut and finalize recording!
          if (elapsedSinceLastSound >= postRoll) {
            finalizeRecording();
          }
        }
      }

      animationFrameRef.current = requestAnimationFrame(tick);
    };

    animationFrameRef.current = requestAnimationFrame(tick);
  }, [addEvent, finalizeRecording]);

  // Master Arm / Disarm Surveillance Switch
  const setArmed = useCallback(async (shouldArm: boolean) => {
    if (shouldArm) {
      try {
        setMicError(null);
        if (isAndroidNativeApp()) {
          const microphone = await backgroundMonitor.requestMicrophonePermission();
          if (!microphone.granted) {
            throw new Error('Microphone permission was not granted. Allow microphone access in Android app settings, then try again.');
          }
          const notifications = await backgroundMonitor.requestNotificationPermission();
          await backgroundMonitor.startMonitoring({
            threshold: settingsRef.current.sensitivityThreshold,
            postRollSeconds: settingsRef.current.postRollSeconds,
          });
          const updatedSettings = { ...settingsRef.current, isArmed: true };
          settingsRef.current = updatedSettings;
          setSettings(updatedSettings);
          await saveSettingsToDB(updatedSettings);
          setIsMicActive(false);
          addEvent('system', 'Android microphone service started; waiting for capture confirmation');
          if (!notifications.granted) {
            addEvent('warning', 'Android notification permission is off; enable notifications to see the monitor status in the notification shade');
          }
          return;
        }
        // Acquire Screen Wake Lock so phone doesn't sleep
        if (settingsRef.current.enableScreenWakeLock) {
          await notificationService.acquireWakeLock();
        }
        // Start inaudible carrier to keep mobile background audio session alive
        if (settingsRef.current.enableKeepAliveAudio) {
          notificationService.startKeepAliveAudio();
        }

        // Request notification permission if not yet granted
        if (notificationService.getPermissionState() === 'default') {
          await notificationService.requestPermission();
        }

        const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        const ctx = new AudioCtx();
        audioContextRef.current = ctx;

        if (ctx.state === 'suspended') {
          await ctx.resume();
        }

        const analyser = ctx.createAnalyser();
        analyser.fftSize = 512;
        analyser.smoothingTimeConstant = 0.3;
        analyserRef.current = analyser;

        // Try getting actual microphone stream
        let stream: MediaStream | null = null;
        try {
          stream = await navigator.mediaDevices.getUserMedia({
            audio: {
              echoCancellation: false,
              noiseSuppression: false,
              autoGainControl: false,
            },
          });
          mediaStreamRef.current = stream;
          const source = ctx.createMediaStreamSource(stream);
          source.connect(analyser);

          // Prepare MediaRecorder
          const mimeType = getSupportedAudioMimeType();
          const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
          mediaRecorderRef.current = recorder;

          recorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) {
              if (recordingStateRef.current.isRecording || finalizingRecordingRef.current) {
                audioChunksRef.current.push(event.data);
              } else {
                preRollChunksRef.current.push(event.data);
                while (preRollChunksRef.current.length > 5) preRollChunksRef.current.shift();
              }
            }
          };
          preRollChunksRef.current = [];
          audioChunksRef.current = [];
          recorder.start(250);

          setIsMicActive(true);
        } catch (micErr) {
          mediaStreamRef.current?.getTracks().forEach((track) => track.stop());
          mediaStreamRef.current = null;
          await ctx.close().catch(() => {});
          audioContextRef.current = null;
          analyserRef.current = null;
          setIsMicActive(false);
          throw new Error(`Microphone capture could not start: ${micErr instanceof Error ? micErr.message : String(micErr)}`);
        }

        const updatedSettings = { ...settingsRef.current, isArmed: true };
        setSettings(updatedSettings);
        await saveSettingsToDB(updatedSettings);
        addEvent('system', 'CCTV Acoustic Surveillance ARMED & ACTIVE');

        startSurveillanceLoop();
      } catch (err: any) {
        console.error('Failed to arm CCTV', err);
        setMicError(err.message || 'Failed to initialize acoustic surveillance');
      }
    } else {
      if (isAndroidNativeApp()) {
        await backgroundMonitor.stopMonitoring();
        const updatedSettings = { ...settingsRef.current, isArmed: false };
        settingsRef.current = updatedSettings;
        setSettings(updatedSettings);
        await saveSettingsToDB(updatedSettings);
        setIsMicActive(false);
        setIsRecording(false);
        setCurrentDecibels(-80);
        setCurrentSoundLevel(0);
        addEvent('system', 'Android microphone foreground service disarmed');
        return;
      }
      // Disarm
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }

      if (recordingStateRef.current.isRecording) {
        await finalizeRecording();
      }

      const recorder = mediaRecorderRef.current;
      if (recorder?.state === 'recording') {
        await new Promise<void>((resolve) => {
          const timeout = window.setTimeout(resolve, 1500);
          recorder.addEventListener('stop', () => {
            window.clearTimeout(timeout);
            resolve();
          }, { once: true });
          try {
            recorder.stop();
          } catch {
            window.clearTimeout(timeout);
            resolve();
          }
        });
      }

      if (mediaStreamRef.current) {
        mediaStreamRef.current.getTracks().forEach((t) => t.stop());
        mediaStreamRef.current = null;
      }
      audioChunksRef.current = [];
      preRollChunksRef.current = [];
      finalizingRecordingRef.current = false;

      if (audioContextRef.current) {
        audioContextRef.current.close().catch(() => {});
        audioContextRef.current = null;
      }

      notificationService.releaseWakeLock();
      notificationService.stopKeepAliveAudio();

      setIsMicActive(false);
      setCurrentDecibels(-80);
      setCurrentSoundLevel(0);
      setIsRecording(false);

      const updatedSettings = { ...settingsRef.current, isArmed: false };
      setSettings(updatedSettings);
      await saveSettingsToDB(updatedSettings);
      addEvent('system', 'CCTV Surveillance DISARMED / Standby');
    }
  }, [addEvent, finalizeRecording, startSurveillanceLoop]);

  // Clean up on unmount
  useEffect(() => {
    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
      if (mediaStreamRef.current) {
        mediaStreamRef.current.getTracks().forEach((t) => t.stop());
      }
      if (audioContextRef.current) {
        audioContextRef.current.close().catch(() => {});
      }
      notificationService.releaseWakeLock();
      notificationService.stopKeepAliveAudio();
    };
  }, []);

  // Update specific setting
  const updateSettings = useCallback(async (newSettings: Partial<CCTVSettings>) => {
    const updated = { ...settingsRef.current, ...newSettings };
    setSettings(updated);
    settingsRef.current = updated;
    await saveSettingsToDB(updated);
    if (isAndroidNativeApp() && updated.isArmed) {
      await backgroundMonitor.updateMonitorSettings({
        threshold: updated.sensitivityThreshold,
        postRollSeconds: updated.postRollSeconds,
      }).catch((error) => console.warn('Could not update native monitor settings', error));
    }
    refreshRecordings();
  }, [refreshRecordings]);

  // Classify existing recording on demand from Vault
  const classifyExistingRecording = useCallback(
    async (id: string) => {
      const rec = recordingsRef.current.find((r) => r.id === id);
      if (!rec) return;
      try {
        const classification = await classifyAudioRecording(rec, rec.notes, settingsRef.current.apiBaseUrl, settingsRef.current.apiAccessToken);
        rec.classification = classification;
        await saveRecordingToDB(rec);
        await addEvent(
          'classified',
          `Re-classified: ${classification.tag} (${Math.round(classification.confidence * 100)}% confidence) - ${classification.explanation}`,
          rec.peakDecibels,
          classification.tag,
          classification.threatLevel,
          classification.confidence
        );
        await refreshRecordings();
      } catch (e) {
        console.error('Failed to classify recording', e);
      }
    },
    [addEvent, refreshRecordings]
  );

  // Delete single recording
  const deleteRecording = useCallback(async (id: string) => {
    await deleteRecordingFromDB(id);
    await refreshRecordings();
    addEvent('system', `Deleted recording ${id}`);
  }, [addEvent, refreshRecordings]);

  // Toggle lock / protect recording
  const toggleProtect = useCallback(async (id: string, isProtected: boolean) => {
    await toggleProtectRecordingInDB(id, isProtected);
    await refreshRecordings();
    addEvent('system', `${isProtected ? 'Protected' : 'Unprotected'} recording from auto-overwrite`);
  }, [addEvent, refreshRecordings]);

  // Clear all unprotected recordings
  const clearUnprotectedRecordings = useCallback(async () => {
    const currentRecs = recordingsRef.current;
    const toDelete = currentRecs.filter((r) => !r.isProtected).map((r) => r.id);
    if (toDelete.length > 0) {
      await deleteMultipleRecordingsFromDB(toDelete);
      await refreshRecordings();
      addEvent('system', `Manually cleared ${toDelete.length} unprotected recording(s)`);
    }
  }, [addEvent, refreshRecordings]);

  return {
    settings,
    updateSettings,
    isArmed: settings.isArmed,
    setArmed,
    recordings,
    events,
    storageStats,
    currentDecibels,
    currentSoundLevel,
    isRecording,
    silenceCountdown,
    recordingDuration,
    triggersInCurrentSession,
    isMicActive,
    pendingNativeRecordings,
    micError,
    storageWarningActive,
    classifyExistingRecording,
    deleteRecording,
    toggleProtect,
    clearUnprotectedRecordings,
    refreshRecordings,
  };
}

// Helpers
function getSupportedAudioMimeType(): string {
  const types = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus',
    'audio/ogg',
    'audio/mp4',
  ];
  for (const t of types) {
    if (typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(t)) {
      return t;
    }
  }
  return '';
}
