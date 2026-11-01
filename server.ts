process.env.DISABLE_HMR = 'true';

import express from 'express';
import path from 'path';
import dotenv from 'dotenv';
import { GoogleGenAI, Type } from '@google/genai';

dotenv.config();

const app = express();
const PORT = Number(process.env.PORT) || 3000;

// Parse large payloads for audio base64 buffers
app.use(express.json({ limit: '50mb' }));

// Shared Gemini AI Client instance
const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY || '',
  httpOptions: {
    timeout: 10000,
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

// Fallback heuristic acoustic classifier
function fallbackHeuristicClassify(body: ClassifyRequestBody) {
  const peak = body.peakDecibels ?? 60;
  const avg = body.avgDecibels ?? 45;
  const duration = body.duration ?? 5;
  const hint = body.simulatedTag?.toLowerCase() || '';

  if (hint.includes('glass')) {
    return {
      tag: 'Glass Breaking',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.94,
      explanation: 'High-frequency transient acoustic burst detected, consistent with glass shatter or perimeter breach.',
      engine: 'acoustic-heuristic',
    };
  }

  if (peak >= 90) {
    return {
      tag: 'Loud Impact / Bang',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.88,
      explanation: 'Severe acoustic impulse exceeding safe surveillance baseline.',
      engine: 'acoustic-heuristic',
    };
  }

  if (peak >= 85 && avg >= 65) {
    return {
      tag: 'Glass Breaking',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.94,
      explanation: 'High-frequency transient acoustic burst detected, consistent with glass shatter or perimeter breach.',
      engine: 'acoustic-heuristic',
    };
  }

  if (hint.includes('footstep') || (peak >= 40 && peak <= 55 && duration > 3)) {
    return {
      tag: 'Footsteps',
      category: 'intrusion',
      threatLevel: 'medium',
      confidence: 0.86,
      explanation: 'Rhythmic low-amplitude floor resonance detected, indicating human movement.',
      engine: 'acoustic-heuristic',
    };
  }

  if (hint.includes('voice') || hint.includes('speech') || (peak >= 50 && peak <= 70 && duration >= 2)) {
    return {
      tag: 'Human Voice / Speech',
      category: 'speech',
      threatLevel: 'low',
      confidence: 0.91,
      explanation: 'Harmonic vocal formants and speech cadence detected within speech frequencies.',
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
      engine: 'acoustic-heuristic',
    };
  }

  if (hint.includes('bark') || hint.includes('dog')) {
    return {
      tag: 'Dog Barking',
      category: 'domestic',
      threatLevel: 'medium',
      confidence: 0.93,
      explanation: 'Repetitive canine vocalization burst detected.',
      engine: 'acoustic-heuristic',
    };
  }

  if (peak >= 90) {
    return {
      tag: 'Loud Impact / Bang',
      category: 'threat',
      threatLevel: 'high',
      confidence: 0.88,
      explanation: 'Severe acoustic impulse exceeding safe surveillance baseline.',
      engine: 'acoustic-heuristic',
    };
  }

  return {
    tag: 'Ambient Noise',
    category: 'ambient',
    threatLevel: 'info',
    confidence: 0.75,
    explanation: 'Low-threat general environmental sound event.',
    engine: 'acoustic-heuristic',
  };
}

// AI Audio Classification Endpoint
app.post('/api/classify-audio', async (req, res) => {
  const body = req.body as ClassifyRequestBody;

  // If Gemini API key is available and audioBase64 or hint is provided, use Gemini
  if (process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY') {
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

      const promptText = `You are a security acoustic CCTV AI surveillance classifier.
Analyze this recorded sound event:
- Peak Sound Level: ${body.peakDecibels ?? 'N/A'} dB
- Average Sound Level: ${body.avgDecibels ?? 'N/A'} dB
- Duration: ${body.duration ?? 'N/A'} seconds
- Re-triggers count: ${body.triggersCount ?? 1}
${body.simulatedTag ? `- Acoustic Signature Hint: ${body.simulatedTag}` : ''}

Classify this sound into one of these specific security tags:
- 'Glass Breaking' (threat: high or critical)
- 'Footsteps' (threat: medium)
- 'Human Voice / Speech' (threat: low or info)
- 'Door Knock' (threat: medium)
- 'Door Forced / Slam' (threat: high)
- 'Dog Barking' (threat: medium)
- 'Alarm / Siren' (threat: high or critical)
- 'Loud Impact / Crash' (threat: high)
- 'Ambient Noise' (threat: info)

Provide a concise, professional security classification.`;

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
        timeoutId = setTimeout(() => reject(new Error('Gemini API request timed out')), 12000);
      });

      const response = await Promise.race([geminiPromise, timeoutPromise]);
      if (timeoutId) clearTimeout(timeoutId);

      const jsonStr = (response.text || '').trim();
      const result = JSON.parse(jsonStr);

      return res.json({
        ...result,
        engine: 'gemini',
        detectedAt: Date.now(),
      });
    } catch (geminiError: any) {
      if (timeoutId) clearTimeout(timeoutId);
      console.warn('Gemini classification fallback to acoustic heuristic:', geminiError?.message);
    }
  }

  // Graceful fallback to heuristic acoustic classifier
  const heuristicResult = fallbackHeuristicClassify(body);
  return res.json({
    ...heuristicResult,
    detectedAt: Date.now(),
  });
});

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'online',
    hasGeminiKey: Boolean(process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY'),
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
