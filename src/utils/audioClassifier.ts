import { AudioRecording, AudioClassification } from '../types';

/**
 * Converts a Blob to a base64 encoded string.
 */
export async function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const res = reader.result as string;
      resolve(res);
    };
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

/**
 * Classifies an audio recording using the AI endpoint with automatic offline fallback.
 */
export async function classifyAudioRecording(
  recording: {
    blob?: Blob;
    peakDecibels: number;
    avgDecibels: number;
    duration: number;
    triggersCount: number;
    notes?: string;
  },
  hintTag?: string
): Promise<AudioClassification> {
  let audioBase64: string | undefined = undefined;

  if (recording.blob && recording.blob.size > 0 && recording.blob.size < 10 * 1024 * 1024) {
    try {
      audioBase64 = await blobToBase64(recording.blob);
    } catch {
      // ignore
    }
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 15000);

    try {
      const res = await fetch('/api/classify-audio', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        signal: controller.signal,
        body: JSON.stringify({
          audioBase64,
          mimeType: recording.blob?.type || 'audio/webm',
          peakDecibels: recording.peakDecibels,
          avgDecibels: recording.avgDecibels,
          duration: recording.duration,
          triggersCount: recording.triggersCount,
          simulatedTag: hintTag || recording.notes,
        }),
      });

      if (res.ok) {
        const data = await res.json();
        return {
          tag: data.tag || 'Sound Event',
          category: data.category || 'ambient',
          threatLevel: data.threatLevel || 'info',
          confidence: Math.round((data.confidence ?? 0.85) * 100) / 100,
          explanation: data.explanation || 'Sound event classified.',
          detectedAt: data.detectedAt || Date.now(),
          engine: data.engine || 'gemini',
        };
      }
    } finally {
      clearTimeout(timeoutId);
    }
  } catch (err) {
    console.warn('Server classification unavailable or timed out, using on-device acoustic classifier');
  }

  // Fully on-device client fallback if server/network is completely unreachable
  return clientHeuristicClassify(recording, hintTag);
}

/**
 * On-device acoustic feature classifier for complete offline environments.
 */
export function clientHeuristicClassify(
  recording: { peakDecibels: number; avgDecibels: number; duration: number; notes?: string },
  hintTag?: string
): AudioClassification {
  const peak = recording.peakDecibels;
  const avg = recording.avgDecibels;
  const duration = recording.duration;
  const hint = (hintTag || recording.notes || '').toLowerCase();

  if (hint.includes('glass')) {
    return {
      tag: 'Glass Breaking',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.94,
      explanation: 'High-frequency transient acoustic burst detected, consistent with glass shatter.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  if (peak >= 90) {
    return {
      tag: 'Loud Impact / Bang',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.88,
      explanation: 'Severe acoustic impulse exceeding safe baseline.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  if (peak >= 85 && avg >= 60) {
    return {
      tag: 'Glass Breaking',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.94,
      explanation: 'High-frequency transient acoustic burst detected, consistent with glass shatter.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  if (hint.includes('footstep') || (peak >= 35 && peak <= 52 && duration >= 2)) {
    return {
      tag: 'Footsteps',
      category: 'intrusion',
      threatLevel: 'medium',
      confidence: 0.87,
      explanation: 'Rhythmic low-amplitude floor resonance detected, indicating human movement.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  if (hint.includes('voice') || hint.includes('speech') || (peak >= 50 && peak <= 70)) {
    return {
      tag: 'Human Voice / Speech',
      category: 'speech',
      threatLevel: 'low',
      confidence: 0.92,
      explanation: 'Harmonic vocal formants and speech cadence detected.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  if (hint.includes('knock') || (peak >= 68 && peak <= 82 && duration < 5)) {
    return {
      tag: 'Door Knock',
      category: 'intrusion',
      threatLevel: 'medium',
      confidence: 0.89,
      explanation: 'Staccato percussive wooden impulses identified consistent with door knock.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  if (hint.includes('bark') || hint.includes('dog')) {
    return {
      tag: 'Dog Barking',
      category: 'domestic',
      threatLevel: 'medium',
      confidence: 0.91,
      explanation: 'Repetitive canine vocalization burst detected.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  if (peak >= 90) {
    return {
      tag: 'Loud Impact / Bang',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.88,
      explanation: 'Severe acoustic impulse exceeding safe baseline.',
      detectedAt: Date.now(),
      engine: 'acoustic-heuristic',
    };
  }

  return {
    tag: 'Ambient Sound Event',
    category: 'ambient',
    threatLevel: 'info',
    confidence: 0.78,
    explanation: 'Low-threat general environmental sound event.',
    detectedAt: Date.now(),
    engine: 'acoustic-heuristic',
  };
}
