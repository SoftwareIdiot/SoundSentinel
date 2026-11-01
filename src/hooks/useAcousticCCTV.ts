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
  isSoundDetected,
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
};

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
  const [micError, setMicError] = useState<string | null>(null);
  const [storageWarningActive, setStorageWarningActive] = useState<boolean>(false);

  // Audio nodes and references
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const animationFrameRef = useRef<number | null>(null);

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

  // Simulated sound spike ref (for testing or mic-free environments)
  const simulationSpikeRef = useRef<number>(0);
  const activeSoundHintRef = useRef<string>('');

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

  // Initialize settings and database on mount
  useEffect(() => {
    async function init() {
      const saved = await getSettingsFromDB();
      if (saved) {
        const merged: CCTVSettings = {
          ...DEFAULT_SETTINGS,
          ...saved,
          enableAIClassification: saved.enableAIClassification !== false,
        };
        setSettings(merged);
        settingsRef.current = merged;
      }
      await refreshRecordings();
      await refreshEvents();
    }
    init();

    // Listen to visibility changes (running in background)
    const handleVisibility = () => {
      const hidden = document.visibilityState === 'hidden';
      if (hidden && settingsRef.current.isArmed) {
        addEvent('system', 'App moved to the background while armed; continued monitoring is not guaranteed');
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [refreshRecordings, refreshEvents, addEvent]);

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
      recorder.stop();
    }

    const durationSec = Math.max(1, Math.round((Date.now() - state.startTime) / 100) / 10);
    const avgDb = state.samplesCount > 0 ? Math.round(state.totalLevel / state.samplesCount) : state.peakLevel;
    const peakDb = state.peakLevel;
    const triggersCount = state.triggersCount;
    const sampleWaveform = state.waveformPoints.slice(0, 40);

    // Wait 100ms for recorder dataavailable event to gather all chunks
    setTimeout(async () => {
      let finalBlob: Blob;
      if (audioChunksRef.current.length > 0) {
        const mimeType = audioChunksRef.current[0].type || 'audio/webm';
        finalBlob = new Blob(audioChunksRef.current, { type: mimeType });
      } else {
        // Fallback synthetic audible wav blob if hardware mic provided empty chunks
        finalBlob = createSyntheticAudioBlob(durationSec);
      }

      const sizeBytes = finalBlob.size || Math.round(durationSec * 16000); // rough estimate if empty
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
      await addEvent('saved', `Recording saved (${durationSec}s, ${peakDb} dB, re-triggered ${triggersCount}x)`, peakDb);
      await refreshRecordings();

      audioChunksRef.current = [];

      // Run AI audio classification asynchronously
      if (settingsRef.current.enableAIClassification !== false) {
        const hint = activeSoundHintRef.current;
        activeSoundHintRef.current = '';
        if (hint) {
          newRecording.notes = hint;
        }
        classifyAudioRecording(newRecording, hint)
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
    }, 150);
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

      // Compute RMS amplitude
      let sumSquares = 0;
      for (let i = 0; i < timeDomainArray.length; i++) {
        sumSquares += timeDomainArray[i] * timeDomainArray[i];
      }
      const rms = Math.sqrt(sumSquares / timeDomainArray.length);
      const rawDb = calculateDecibelsFromRMS(rms);
      let soundScore = mapDecibelsToScale(rawDb);

      // Apply simulated spike if active
      if (simulationSpikeRef.current > 0) {
        soundScore = Math.max(soundScore, simulationSpikeRef.current);
        // decay simulation spike
        simulationSpikeRef.current = Math.max(0, simulationSpikeRef.current - 2);
      }

      setCurrentDecibels(rawDb);
      setCurrentSoundLevel(soundScore);

      const threshold = settingsRef.current.sensitivityThreshold;
      const postRoll = settingsRef.current.postRollSeconds;
      const hasSound = isSoundDetected(soundScore, threshold);
      const state = recordingStateRef.current;
      const now = Date.now();

      if (hasSound) {
        // Sound is active right now!
        if (!state.isRecording) {
          // Trigger new recording session!
          state.isRecording = true;
          state.startTime = now;
          state.lastSoundTime = now;
          state.peakLevel = soundScore;
          state.samplesCount = 1;
          state.totalLevel = soundScore;
          state.triggersCount = 1;
          state.waveformPoints = [soundScore];

          setIsRecording(true);
          setSilenceCountdown(postRoll);
          setTriggersInCurrentSession(1);
          setRecordingDuration(0);

          audioChunksRef.current = [];
          if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'inactive') {
            try {
              mediaRecorderRef.current.start(250); // Emit chunks every 250ms
            } catch (err) {
              console.warn('Failed to start media recorder', err);
            }
          }

          // Trigger immediate notification & alarm
          if (settingsRef.current.enableNotifications) {
            notificationService.sendDetectionNotification(soundScore, 1);
          }
          if (settingsRef.current.enableVibration) {
            notificationService.triggerHaptic();
          }
          notificationService.playAlertBeep();
          addEvent('detection', `Sound detected (${soundScore} dB)! Recording started`, soundScore);
        } else {
          // Already recording! Re-trigger extension!
          const timeSinceLastSound = now - state.lastSoundTime;
          // If there was a silence gap >= 500ms, count as re-trigger
          if (timeSinceLastSound > 500) {
            state.triggersCount += 1;
            setTriggersInCurrentSession(state.triggersCount);
            addEvent('extension', `Sound re-detected (${soundScore} dB)! Extended +10s`, soundScore);
          }
          state.lastSoundTime = now;
          state.peakLevel = Math.max(state.peakLevel, soundScore);
          state.totalLevel += soundScore;
          state.samplesCount += 1;
          if (state.waveformPoints.length < 50) {
            state.waveformPoints.push(soundScore);
          }
          setSilenceCountdown(postRoll);
        }
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
          const recorder = new MediaRecorder(stream, { mimeType });
          mediaRecorderRef.current = recorder;

          recorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) {
              audioChunksRef.current.push(event.data);
            }
          };

          setIsMicActive(true);
        } catch (micErr) {
          console.warn('Physical microphone unavailable or denied. Operating in Simulated / Hybrid Acoustic mode', micErr);
          // Create an oscillator node as fallback so surveillance loop can run smoothly
          const synthOsc = ctx.createOscillator();
          const synthGain = ctx.createGain();
          synthGain.gain.value = 0.001; // nearly silent
          synthOsc.connect(synthGain);
          synthGain.connect(analyser);
          synthOsc.start();
          setIsMicActive(false);
          setMicError('Hardware mic not detected or permission denied. Using Simulated Mic Sentinel mode.');
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
      // Disarm
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }

      if (recordingStateRef.current.isRecording) {
        finalizeRecording();
      }

      if (mediaStreamRef.current) {
        mediaStreamRef.current.getTracks().forEach((t) => t.stop());
        mediaStreamRef.current = null;
      }

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
    refreshRecordings();
  }, [refreshRecordings]);

  // Simulate sound spike (e.g. 85 dB glass break, footsteps, voice, door knock)
  const simulateSoundSpike = useCallback((decibels: number = 75, hint?: string) => {
    simulationSpikeRef.current = decibels;
    if (hint) {
      activeSoundHintRef.current = hint;
    }
    // synthesize audible beep in context if available
    notificationService.playAlertBeep();
  }, []);

  // Add a labeled sample to the audit trail without arming the microphone.
  const simulateDemoEvent = useCallback((decibels: number, hint: string) => {
    const tag = hint || 'Demo sound';
    const normalizedTag = tag.toLowerCase();
    const threatLevel: ThreatLevel = normalizedTag.includes('glass') || normalizedTag.includes('alarm')
      ? 'high'
      : normalizedTag.includes('door')
        ? 'medium'
        : 'low';

    void addEvent(
      'detection',
      `Demo event: ${tag}. No audio was recorded.`,
      decibels,
      undefined,
      threatLevel
    );
  }, [addEvent]);

  // Classify existing recording on demand from Vault
  const classifyExistingRecording = useCallback(
    async (id: string) => {
      const rec = recordingsRef.current.find((r) => r.id === id);
      if (!rec) return;
      try {
        const classification = await classifyAudioRecording(rec, rec.notes);
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
    micError,
    storageWarningActive,
    simulateSoundSpike,
    classifyExistingRecording,
    simulateDemoEvent,
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

/**
 * Creates a synthetic valid WAV audio blob for environments without hardware mic inputs
 */
function createSyntheticAudioBlob(durationSeconds: number): Blob {
  const sampleRate = 8000;
  const numSamples = Math.floor(sampleRate * durationSeconds);
  const buffer = new ArrayBuffer(44 + numSamples * 2);
  const view = new DataView(buffer);

  // RIFF header
  writeString(view, 0, 'RIFF');
  view.setUint32(4, 36 + numSamples * 2, true);
  writeString(view, 8, 'WAVE');
  writeString(view, 12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, 1, true); // mono
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true); // 16-bit
  writeString(view, 36, 'data');
  view.setUint32(40, numSamples * 2, true);

  // Generate synthetic tone with slight noise
  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    const sample = Math.sin(2 * Math.PI * 440 * t) * 0.3 * Math.exp(-t * 0.5);
    const intSample = Math.max(-32768, Math.min(32767, Math.floor(sample * 32767)));
    view.setInt16(44 + i * 2, intSample, true);
  }

  return new Blob([buffer], { type: 'audio/wav' });
}

function writeString(view: DataView, offset: number, string: string) {
  for (let i = 0; i < string.length; i++) {
    view.setUint8(offset + i, string.charCodeAt(i));
  }
}
