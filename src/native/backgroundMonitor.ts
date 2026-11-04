import { Capacitor, registerPlugin } from '@capacitor/core';

export interface NativeRecordingInfo {
  id: string;
  timestamp: number;
  duration: number;
  sizeBytes: number;
  peakDecibels: number;
  avgDecibels: number;
  triggersCount: number;
  waveformData: number[];
  classification?: import('../types').AudioClassification;
}

interface BackgroundMonitorPlugin {
  requestMicrophonePermission(): Promise<{ granted: boolean }>;
  requestNotificationPermission(): Promise<{ granted: boolean }>;
  startMonitoring(options: { threshold: number; postRollSeconds: number }): Promise<void>;
  stopMonitoring(): Promise<void>;
  updateMonitorSettings(options: { threshold: number; postRollSeconds: number }): Promise<void>;
  getStatus(): Promise<{ running: boolean; capturing: boolean; soundLevel: number; decibels: number; recording: boolean; recordingDuration: number; silenceRemaining: number; pendingRecordings: number; error?: string }>;
  listPendingRecordings(): Promise<{ recordings: NativeRecordingInfo[] }>;
  readRecording(options: { id: string }): Promise<{ base64: string }>;
  acknowledgeRecording(options: { id: string }): Promise<void>;
  deleteRecording(options: { id: string }): Promise<void>;
}

export const backgroundMonitor = registerPlugin<BackgroundMonitorPlugin>('BackgroundMonitor');

export function isAndroidNativeApp(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}
