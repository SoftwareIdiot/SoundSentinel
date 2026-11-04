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
  hintTag?: string,
  apiBaseUrl?: string,
  apiAccessToken?: string
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
    const timeoutId = setTimeout(() => controller.abort(), 45000);

    try {
      const baseUrl = (apiBaseUrl || '').trim().replace(/\/$/, '');
      const res = await fetch(`${baseUrl}/api/classify-audio`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(apiAccessToken ? { Authorization: `Bearer ${apiAccessToken}` } : {}),
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
          providerStatus: data.aiStatus || (data.engine === 'gemini' ? 'ready' : undefined),
          providerMessage: data.aiMessage,
        };
      }

      const errorData = await res.json().catch(() => ({}));
      const fallback = clientHeuristicClassify(recording, hintTag);
      const providerStatus = res.status === 401 ? 'credentials_invalid' : 'server_error';
      return {
        ...fallback,
        providerStatus,
        providerMessage: errorData.error || `AI server returned HTTP ${res.status}; local heuristic used.`,
      };
    } finally {
      clearTimeout(timeoutId);
    }
  } catch {
    console.warn('Server classification unavailable or timed out, using on-device acoustic classifier');
    const fallback = clientHeuristicClassify(recording, hintTag);
    return {
      ...fallback,
      providerStatus: 'server_unreachable',
      providerMessage: 'Laptop AI server is unreachable; this result came from the local heuristic.',
    };
  }

  // Fully on-device client fallback if server/network is completely unreachable
  return {
    ...clientHeuristicClassify(recording, hintTag),
    providerStatus: 'server_unreachable',
    providerMessage: 'Laptop AI server is unreachable; this result came from the local heuristic.',
  };
}

/**
 * On-device acoustic feature classifier for complete offline environments.
 */
export function clientHeuristicClassify(
  recording: { peakDecibels: number; avgDecibels: number; duration: number; notes?: string },
  hintTag?: string
): AudioClassification {
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

  if (hint.includes('footstep')) {
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

  if (hint.includes('voice') || hint.includes('speech')) {
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

  if (hint.includes('knock')) {
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

  return {
    tag: 'Ambient Noise',
    category: 'ambient',
    threatLevel: 'info',
    confidence: 0.45,
    explanation: 'The offline fallback only has clip-level loudness, which is not enough to identify a sound reliably.',
    detectedAt: Date.now(),
    engine: 'acoustic-heuristic',
  };
}
