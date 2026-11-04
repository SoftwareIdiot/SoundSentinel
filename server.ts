process.env.DISABLE_HMR = 'true';

import express from 'express';
import path from 'path';
import dotenv from 'dotenv';
import { GoogleGenAI, Type } from '@google/genai';

dotenv.config();

const app = express();
const PORT = Number(process.env.PORT) || 3000;

// Capacitor's Android WebView calls the laptop API cross-origin. The desktop
// development page also calls the API by Wi-Fi IP, so allow only its local origin.
const allowedAppOrigins = new Set([
  'https://localhost',
  'http://localhost',
  'capacitor://localhost',
  'http://localhost:3000',
]);
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && allowedAppOrigins.has(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  }
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

// Parse large payloads for audio base64 buffers
app.use(express.json({ limit: '50mb' }));

// Shared Gemini AI Client instance
const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY || '',
  httpOptions: {
    timeout: 40000,
    retryOptions: {
      attempts: 1,
      initialDelay: 1,
      maxDelay: 1,
      jitter: 0,
    },
    headers: {
      'User-Agent': 'aistudio-build',
    },
  },
});

interface ClassifyRequestBody {
  audioBase64?: string;
  mimeType?: string;
  peakDecibels?: number;
  avgDecibels?: number;
  duration?: number;
  triggersCount?: number;
  simulatedTag?: string;
}

interface WavAcousticFeatures {
  periodicBurstCount: number;
  medianBurstSeconds: number;
  burstIntervalVariation: number;
  shortLoudTransient: boolean;
}

/** Extract simple timing and impulse cues from native PCM WAV clips for offline fallback. */
function analyzePcmWav(body: ClassifyRequestBody): WavAcousticFeatures | undefined {
  if (!body.audioBase64 || !body.mimeType?.toLowerCase().includes('wav')) return undefined;
  try {
    const encoded = body.audioBase64.includes(',') ? body.audioBase64.split(',')[1] : body.audioBase64;
    const audio = Buffer.from(encoded, 'base64');
    if (audio.length < 44 || audio.toString('ascii', 0, 4) !== 'RIFF' || audio.toString('ascii', 8, 12) !== 'WAVE') return undefined;

    let format = 0, channels = 0, sampleRate = 0, bitsPerSample = 0;
    let dataOffset = -1, dataLength = 0;
    for (let offset = 12; offset + 8 <= audio.length;) {
      const chunkName = audio.toString('ascii', offset, offset + 4);
      const chunkLength = audio.readUInt32LE(offset + 4);
      const chunkStart = offset + 8;
      if (chunkStart + chunkLength > audio.length) return undefined;
      if (chunkName === 'fmt ' && chunkLength >= 16) {
        format = audio.readUInt16LE(chunkStart);
        channels = audio.readUInt16LE(chunkStart + 2);
        sampleRate = audio.readUInt32LE(chunkStart + 4);
        bitsPerSample = audio.readUInt16LE(chunkStart + 14);
      } else if (chunkName === 'data') {
        dataOffset = chunkStart;
        dataLength = chunkLength;
      }
      offset = chunkStart + chunkLength + (chunkLength % 2);
    }
    if (format !== 1 || channels < 1 || bitsPerSample !== 16 || sampleRate < 4000 || dataOffset < 0) return undefined;

    const samples = Math.floor(dataLength / (channels * 2));
    if (samples < sampleRate / 4 || samples > sampleRate * 120) return undefined;
    const frameSamples = Math.max(1, Math.round(sampleRate * 0.02));
    const rmsFrames: number[] = [];
    const peakFrames: number[] = [];
    for (let start = 0; start < samples; start += frameSamples) {
      const end = Math.min(samples, start + frameSamples);
      let squareSum = 0, peak = 0, count = 0;
      for (let sampleIndex = start; sampleIndex < end; sampleIndex++) {
        let mixed = 0;
        for (let channel = 0; channel < channels; channel++) {
          mixed += audio.readInt16LE(dataOffset + (sampleIndex * channels + channel) * 2) / 32768;
        }
        mixed /= channels;
        squareSum += mixed * mixed;
        peak = Math.max(peak, Math.abs(mixed));
        count++;
      }
      rmsFrames.push(Math.sqrt(squareSum / Math.max(1, count)));
      peakFrames.push(peak);
    }

    const sortedRms = [...rmsFrames].sort((a, b) => a - b);
    const noiseFloor = sortedRms[Math.floor(sortedRms.length * 0.2)] ?? 0;
    const upperTypical = sortedRms[Math.floor(sortedRms.length * 0.75)] ?? 0;
    const activityGate = Math.max(0.012, noiseFloor * 2.5, upperTypical * 0.12);
    const active = rmsFrames.map((rms) => rms >= activityGate);
    // Merge one-frame gaps caused by frame alignment or a sound's envelope.
    for (let i = 1; i < active.length - 1; i++) {
      if (!active[i] && active[i - 1] && active[i + 1]) active[i] = true;
    }
    const bursts: Array<{ start: number; end: number }> = [];
    for (let i = 0; i < active.length;) {
      if (!active[i]) { i++; continue; }
      const start = i;
      while (i < active.length && active[i]) i++;
      if (i - start >= 3) bursts.push({ start, end: i });
    }

    const periodicBursts = bursts.filter((burst) => {
      const duration = (burst.end - burst.start) * 0.02;
      return duration >= 0.06 && duration <= 1.5;
    });
    const intervals = periodicBursts.slice(1).map((burst, index) => (burst.start - periodicBursts[index].start) * 0.02);
    const meanInterval = intervals.length ? intervals.reduce((sum, value) => sum + value, 0) / intervals.length : 0;
    const burstIntervalVariation = intervals.length > 1 && meanInterval > 0
      ? Math.sqrt(intervals.reduce((sum, value) => sum + (value - meanInterval) ** 2, 0) / intervals.length) / meanInterval
      : 0;
    const medianBurstSeconds = periodicBursts.length
      ? [...periodicBursts.map((burst) => (burst.end - burst.start) * 0.02)].sort((a, b) => a - b)[Math.floor(periodicBursts.length / 2)]
      : 0;
    const periodicBurstCount = periodicBursts.length >= 3
      && meanInterval >= 0.25 && meanInterval <= 2.2 && burstIntervalVariation <= 0.32
      ? periodicBursts.length : 0;
    const shortLoudTransient = bursts.some((burst) => {
      const duration = (burst.end - burst.start) * 0.02;
      const peak = Math.max(...peakFrames.slice(burst.start, burst.end));
      return duration <= 0.16 && peak >= 0.35;
    });

    return { periodicBurstCount, medianBurstSeconds, burstIntervalVariation, shortLoudTransient };
  } catch {
    return undefined;
  }
}

// Fallback heuristic acoustic classifier
function fallbackHeuristicClassify(body: ClassifyRequestBody) {
  const audio = analyzePcmWav(body);
  if (audio?.periodicBurstCount) {
    const footstepsLike = audio.medianBurstSeconds <= 0.18;
    return {
      tag: footstepsLike ? 'Footsteps' : 'Alarm / Siren',
      category: footstepsLike ? 'intrusion' : 'threat',
      threatLevel: footstepsLike ? 'medium' : 'high',
      confidence: footstepsLike ? 0.62 : 0.68,
      explanation: `Gemini was unavailable. The local WAV fallback found ${audio.periodicBurstCount} regularly spaced sound bursts; treat this label as tentative.`,
      engine: 'acoustic-heuristic',
    };
  }

  if (audio?.shortLoudTransient) {
    return {
      tag: 'Loud Impact / Bang',
      category: 'threat',
      threatLevel: 'medium',
      confidence: 0.58,
      explanation: 'Gemini was unavailable. The local WAV fallback found a brief, strong audio transient; it cannot reliably distinguish an impact from other sharp sounds.',
      engine: 'acoustic-heuristic',
    };
  }

  return {
    tag: 'Ambient Noise',
    category: 'ambient',
    threatLevel: 'info',
    confidence: 0.45,
    explanation: 'Gemini was unavailable and the local audio cues were not distinctive enough to identify the sound confidently.',
    engine: 'acoustic-heuristic',
  };
}

// AI Audio Classification Endpoint
app.post('/api/classify-audio', async (req, res) => {
  const configuredToken = process.env.APP_API_TOKEN?.trim();
  const remoteAddress = req.socket.remoteAddress || '';
  const isLoopback = remoteAddress === '::1'
    || remoteAddress === '127.0.0.1'
    || remoteAddress.endsWith('::ffff:127.0.0.1');
  if (!configuredToken && !isLoopback) {
    return res.status(503).json({ error: 'Set APP_API_TOKEN before allowing LAN access to audio classification' });
  }
  if (configuredToken && req.get('authorization') !== `Bearer ${configuredToken}`) {
    return res.status(401).json({ error: 'Unauthorized classification request' });
  }
  const body = req.body as ClassifyRequestBody;
  const geminiConfigured = Boolean(process.env.GEMINI_API_KEY?.trim() && process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY');
  let aiStatus = geminiConfigured ? 'unavailable' : 'not_configured';
  let aiMessage = geminiConfigured
    ? 'Gemini could not classify this clip; the local heuristic was used.'
    : 'GEMINI_API_KEY is not configured on the laptop server; the local heuristic was used.';

  // If Gemini API key is available and audioBase64 or hint is provided, use Gemini
  if (geminiConfigured) {
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    try {
      const parts: any[] = [];

      if (body.audioBase64) {
        // Strip data URL prefix if included
        const cleanBase64 = body.audioBase64.includes(',')
          ? body.audioBase64.split(',')[1]
          : body.audioBase64;

        parts.push({
          inlineData: {
            mimeType: body.mimeType || 'audio/webm',
            data: cleanBase64,
          },
        });
      }

      const promptText = `You are an acoustic event classifier for a personal sound-monitoring app.
Classify the actual attached audio when audio is present. The numeric fields are relative signal scores from 0 to 100, not calibrated sound-pressure measurements. Use them only as supporting context.
- Relative peak signal level: ${body.peakDecibels ?? 'N/A'} / 100 (not calibrated dB SPL)
- Relative average signal level: ${body.avgDecibels ?? 'N/A'} / 100
- Duration: ${body.duration ?? 'N/A'} seconds
- Re-triggers count: ${body.triggersCount ?? 1}
${body.simulatedTag ? `- Acoustic Signature Hint: ${body.simulatedTag}` : ''}

Classify this sound into one of these tags:
- 'Glass Breaking' (threat: high or critical)
- 'Footsteps' (threat: medium)
- 'Human Voice / Speech' (threat: low or info)
- 'Door Knock' (threat: medium)
- 'Door Forced / Slam' (threat: high)
- 'Dog Barking' (threat: medium)
- 'Alarm / Siren' (threat: high or critical)
- 'Loud Impact / Crash' (threat: high)
- 'Ambient Noise' (threat: info)

Base the result on audible evidence. If the clip is unclear, use 'Ambient Noise' with low confidence. Do not infer a person, break-in, or threat from loudness alone. Return a concise explanation and confidence that reflects uncertainty.`;

      parts.push({ text: promptText });

      const geminiPromise = ai.models.generateContent({
        model: 'gemini-3.8-flash',
        contents: { parts },
        config: {
          systemInstruction: 'You are an advanced acoustic surveillance engine. Always classify sounds into standardized security tags with threat levels.',
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              tag: {
                type: Type.STRING,
                description: 'Specific security classification tag e.g. Glass Breaking, Footsteps, Human Voice / Speech, Door Knock',
              },
              category: {
                type: Type.STRING,
                description: 'One of: threat, speech, intrusion, domestic, ambient',
              },
              threatLevel: {
                type: Type.STRING,
                description: 'One of: info, low, medium, high, critical',
              },
              confidence: {
                type: Type.NUMBER,
                description: 'Confidence score between 0.0 and 1.0',
              },
              explanation: {
                type: Type.STRING,
                description: 'Brief 1-sentence security assessment explanation of the sound.',
              },
            },
            required: ['tag', 'category', 'threatLevel', 'confidence', 'explanation'],
          },
        },
      });

      const timeoutPromise = new Promise<never>((_, reject) => {
        timeoutId = setTimeout(() => reject(new Error('Gemini API request timed out')), 40000);
      });

      const response = await Promise.race([geminiPromise, timeoutPromise]);
      if (timeoutId) clearTimeout(timeoutId);

      const jsonStr = (response.text || '').trim();
      const result = JSON.parse(jsonStr);

      return res.json({
        ...result,
        engine: 'gemini',
        aiStatus: 'ready',
        aiMessage: 'Gemini classified this sound.',
        detectedAt: Date.now(),
      });
    } catch (geminiError: any) {
      if (timeoutId) clearTimeout(timeoutId);
      const status = Number(geminiError?.status ?? geminiError?.code ?? 0);
      if (status === 429) {
        aiStatus = 'quota_exhausted';
        aiMessage = 'Gemini API quota is exhausted or rate-limited. The local heuristic was used.';
      } else if (status === 401 || status === 403) {
        aiStatus = 'credentials_invalid';
        aiMessage = 'Gemini rejected the server API key. The local heuristic was used.';
      } else if (status === 404) {
        aiStatus = 'unavailable';
        aiMessage = 'The configured Gemini model is unavailable for this key. The local heuristic was used.';
      }
      console.warn(`Gemini classification fallback (${aiStatus}):`, status || 'request error');
    }
  }

  // Graceful fallback to heuristic acoustic classifier
  const heuristicResult = fallbackHeuristicClassify(body);
  return res.json({
    ...heuristicResult,
    aiStatus,
    aiMessage,
    detectedAt: Date.now(),
  });
});

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'online',
    hasGeminiKey: Boolean(process.env.GEMINI_API_KEY?.trim() && process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY'),
    hasAppApiToken: Boolean(process.env.APP_API_TOKEN?.trim()),
    timestamp: Date.now(),
  });
});

async function startServer() {
  if (process.env.NODE_ENV !== 'production') {
    // In dev, mount Vite middlewares
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        hmr: false,
        ws: false,
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    // In production, serve dist folder
    app.use(express.static(path.resolve(process.cwd(), 'dist')));
    app.get('*', (req, res) => {
      res.sendFile(path.resolve(process.cwd(), 'dist', 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`SoundSentinel Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
