export type ThreatLevel = 'info' | 'low' | 'medium' | 'high' | 'critical';

export interface AudioClassification {
  tag: string; // e.g. "Glass Breaking", "Footsteps", "Human Voice / Speech", "Door Knock", "Dog Barking", "Alarm / Siren"
  category: 'threat' | 'speech' | 'intrusion' | 'domestic' | 'ambient';
  threatLevel: ThreatLevel;
  confidence: number; // 0.0 to 1.0
  explanation: string;
  detectedAt: number;
  engine: 'gemini' | 'acoustic-heuristic';
  providerStatus?: 'ready' | 'not_configured' | 'quota_exhausted' | 'credentials_invalid' | 'server_unreachable' | 'server_error' | 'unavailable';
  providerMessage?: string;
}

export interface AudioRecording {
  id: string;
  timestamp: number;
  dateString: string;
  duration: number; // in seconds
  blob?: Blob;
  audioUrl?: string;
  sizeBytes: number;
  peakDecibels: number;
  avgDecibels: number;
  triggersCount: number;
  isProtected: boolean;
  notes?: string;
  waveformData?: number[]; // simplified waveform amplitude points for rendering
  classification?: AudioClassification;
}

export type RetentionMode = '1h' | '6h' | '24h' | '3d' | '7d' | '30d' | 'custom' | 'never';

export interface CCTVSettings {
  isArmed: boolean;
  sensitivityThreshold: number; // 0 - 100 scale (mapped to dB)
  postRollSeconds: number; // default 10s
  storageQuotaMB: number; // e.g. 100 MB
  warningThresholdPercent: number; // e.g. 85%
  retentionMode: RetentionMode;
  customRetentionHours: number; // if retentionMode is 'custom'
  autoPruneOnFull: boolean;
  enableNotifications: boolean;
  enableVibration: boolean;
  enableScreenWakeLock: boolean;
  enableKeepAliveAudio: boolean;
  enableAIClassification: boolean; // Toggle for AI Audio Classifier
  apiBaseUrl?: string; // Optional laptop/server URL for the Android APK
  apiAccessToken?: string; // Optional shared token for a laptop-hosted dev server
}

export interface StorageStats {
  usedBytes: number;
  quotaBytes: number;
  usedPercentage: number;
  isNearFull: boolean;
  isFull: boolean;
  recordingsCount: number;
  protectedCount: number;
}

export interface CCTVEvent {
  id: string;
  timestamp: number;
  type: 'detection' | 'extension' | 'saved' | 'warning' | 'pruned' | 'system' | 'classified';
  message: string;
  decibels?: number;
  tag?: string;
  threatLevel?: ThreatLevel;
  confidence?: number;
}
