import React from 'react';
import {
  Shield,
  ShieldAlert,
  Power,
  Bell,
  Sliders,
  Sparkles,
  AlertTriangle,
  Info,
  ChevronDown,
} from 'lucide-react';
import { CCTVSettings } from '../types';
import { notificationService } from '../utils/notifications';

interface SurveillanceScreenProps {
  isArmed: boolean;
  setArmed: (val: boolean) => void;
  soundLevel: number;
  currentDecibels: number;
  threshold: number;
  isRecording: boolean;
  silenceCountdown: number;
  recordingDuration: number;
  triggersCount: number;
  isMicActive: boolean;
  pendingNativeRecordings: number;
  micError: string | null;
  storageWarningActive: boolean;
  settings: CCTVSettings;
  onUpdateSettings: (newSettings: Partial<CCTVSettings>) => void;
}

export const SurveillanceScreen: React.FC<SurveillanceScreenProps> = ({
  isArmed,
  setArmed,
  soundLevel,
  currentDecibels,
  threshold,
  isRecording,
  silenceCountdown,
  recordingDuration,
  triggersCount,
  isMicActive,
  pendingNativeRecordings,
  micError,
  storageWarningActive,
  settings,
  onUpdateSettings,
}) => {
  const notifPermission = notificationService.getPermissionState();

  const handleRequestNotif = async () => {
    await notificationService.requestPermission();
    onUpdateSettings({ enableNotifications: true });
  };

  return (
    <div className="flex flex-col gap-3.5 pb-6">
      {/* CCTV Camera Info Header */}
      <div className="flex items-center justify-between gap-3 bg-slate-900/80 backdrop-blur-md px-4 py-3 rounded-2xl border border-slate-800">
        <div className="flex min-w-0 items-center gap-3">
          <div className={`p-2 rounded-xl ${isArmed ? 'bg-emerald-950 text-emerald-400 border border-emerald-800/50' : 'bg-slate-800 text-slate-400'}`}>
            <Shield className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-mono font-bold text-slate-100 uppercase tracking-wide">
                AUDIO SENSOR 01
              </span>
              <span className={`text-[11px] px-2 py-0.5 rounded-full font-mono font-semibold ${isArmed ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-800 text-slate-400'}`}>
                {isArmed ? 'ARMED' : 'STANDBY'}
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-400">
              Acoustic CCTV Engine • 10s Post-Roll Extension
            </p>
          </div>
        </div>

        {/* Master Power / Arm Button */}
        <button
          onClick={() => setArmed(!isArmed)}
          aria-label={isArmed ? 'Disarm Acoustic CCTV' : 'Arm Acoustic CCTV'}
          className={`relative group flex min-h-11 shrink-0 items-center gap-2 px-4 rounded-xl font-mono text-sm font-bold transition-all shadow-lg active:scale-95 ${
            isArmed
              ? 'bg-red-600 hover:bg-red-500 text-white shadow-red-950/50'
              : 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-950/50'
          }`}
        >
          <Power className="w-4 h-4" />
          <span>{isArmed ? 'DISARM' : 'ARM CCTV'}</span>
        </button>
      </div>

      {/* Mic Warning or Simulation Notice */}
      {micError && (
        <div className="flex items-start gap-3 p-4 rounded-2xl bg-amber-950/40 border border-amber-800/60 text-amber-100 text-sm leading-relaxed">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-amber-400" />
          <div className="flex-1">
            <span className="font-semibold block">{micError}</span>
            <span className="mt-1 block text-xs text-amber-200/80">
              Check microphone access in Android settings, then arm the monitor again.
            </span>
          </div>
        </div>
      )}

      <div className={`flex items-start gap-3 rounded-2xl border p-3.5 ${isMicActive ? 'border-emerald-800/70 bg-emerald-950/30' : 'border-slate-800 bg-slate-900/50'}`}>
        <span className={`mt-1 h-2.5 w-2.5 shrink-0 rounded-full ${isMicActive ? 'bg-emerald-400 shadow-[0_0_12px_rgba(52,211,153,0.6)]' : 'bg-slate-600'}`} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
            <span className="text-xs font-bold text-slate-200">Microphone input</span>
            <span className={`text-[10px] font-mono font-bold ${isMicActive ? 'text-emerald-300' : 'text-slate-400'}`}>
              {isMicActive ? 'CAPTURE CONFIRMED' : isArmed ? 'WAITING FOR CAPTURE' : 'OFF'}
            </span>
          </div>
          <p className="mt-1 text-[11px] leading-relaxed text-slate-400">
            Live input {currentDecibels.toFixed(1)} dBFS · relative level {soundLevel}/100 · trigger {threshold}/100
            {pendingNativeRecordings > 0 ? ` · ${pendingNativeRecordings} clip(s) waiting to import` : ''}
          </p>
          {isArmed && !isMicActive && !micError && (
            <p className="mt-1 text-[11px] text-amber-300">Waiting for Android to confirm audio capture. If it stays here, disarm and arm again.</p>
          )}
          {isMicActive && soundLevel < threshold && (
            <p className="mt-1 text-[11px] text-slate-500">Play a loud test sound. Lower Detection Sensitivity if the live level never reaches the trigger.</p>
          )}
        </div>
      </div>

      {/* Storage Warning Banner */}
      {storageWarningActive && (
        <div className="flex items-center gap-3 p-4 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-200 text-sm">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-4 h-4 text-amber-400 animate-pulse" />
            <span>
              <strong>Storage Alert:</strong> Over {settings.warningThresholdPercent}% quota reached. Oldest recordings will be automatically purged on new triggers.
            </span>
          </div>
        </div>
      )}

      {isArmed && (
        <div className={`rounded-2xl border px-4 py-3 ${isRecording ? 'border-red-900/70 bg-red-950/30' : 'border-slate-800 bg-slate-900/50'}`}>
          <div className="flex items-center justify-between gap-3">
            <span className={`text-xs font-bold ${isRecording ? 'text-red-200' : 'text-slate-300'}`}>
              {isRecording ? `Recording · ${recordingDuration.toFixed(1)} s` : 'Listening for a sound'}
            </span>
            {isRecording && <span className="text-[11px] font-mono text-amber-300">quiet tail {silenceCountdown.toFixed(1)} s</span>}
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-800">
            <div className={`h-full rounded-full transition-[width] duration-150 ${isRecording ? 'bg-red-400' : 'bg-emerald-400'}`} style={{ width: `${soundLevel}%` }} />
          </div>
          <p className="mt-1.5 text-[10px] text-slate-500">
            {isRecording ? `The clip closes after ${settings.postRollSeconds} seconds below the sound gate · ${triggersCount} sound burst(s)` : 'The gate adapts to room noise; brief sharp sounds are also checked.'}
          </p>
        </div>
      )}

      {/* Live CCTV Mode & Re-trigger Automation Indicator */}
      <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-4 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sliders className="w-4 h-4 text-emerald-400" />
            <h4 className="text-sm font-bold text-slate-100 uppercase tracking-wide">
              Detection Sensitivity
            </h4>
          </div>
          <span className="text-sm font-mono font-bold text-emerald-300">
            {threshold} / 100
          </span>
        </div>

        <div className="pt-1">
          <input type="range" min="10" max="90" value={threshold}
            onChange={(e) => onUpdateSettings({ sensitivityThreshold: Number(e.target.value) })}
            className="w-full accent-emerald-500 cursor-pointer h-2 bg-slate-800 rounded-lg appearance-none"
            aria-label="Adjust sound sensitivity threshold" />
          <div className="mt-2 flex justify-between text-[10px] text-slate-400">
            <span>More sensitive</span><span>Only louder sounds</span>
          </div>
        </div>
      </div>

      <details className="group rounded-2xl border border-slate-800/80 bg-slate-900/40">
        <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-slate-200">
          <span className="flex items-center gap-2"><Info className="h-4 w-4 text-emerald-400" />How monitoring works</span>
          <ChevronDown className="h-4 w-4 shrink-0 text-slate-500 transition-transform group-open:rotate-180" />
        </summary>
        <div className="space-y-2 px-4 pb-4 text-sm leading-relaxed text-slate-400">
          <p>A sound above the selected threshold starts an audio recording. When it goes quiet, recording continues for 10 seconds. A new sound resets that countdown.</p>
          <p className="rounded-xl border border-slate-800 bg-slate-950/60 p-3 text-xs text-slate-300">The Android app uses a foreground microphone service while armed. Allow its persistent notification and set battery use to Unrestricted for long background sessions.</p>
        </div>
      </details>

      {/* AI Threat Classification Engine Status */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-emerald-400" />
            <h4 className="text-sm font-bold text-slate-100">
              AI classification
            </h4>
          </div>
          <button
            onClick={() => onUpdateSettings({ enableAIClassification: !settings.enableAIClassification })}
            className={`w-9 h-5 rounded-full transition-colors relative p-0.5 ${
              settings.enableAIClassification ? 'bg-emerald-500' : 'bg-slate-700'
            }`}
            aria-label="Toggle AI Audio Classification"
          >
            <div
              className={`w-4 h-4 rounded-full bg-white transition-transform ${
                settings.enableAIClassification ? 'translate-x-4' : 'translate-x-0'
              }`}
            />
          </button>
        </div>
        <p className="text-[11px] text-slate-400 leading-relaxed">
          Sound detection runs locally. Gemini analyzes each saved clip through the laptop server when it is reachable; otherwise the app keeps the recording and marks the result as local.
        </p>
      </div>

      <div className="flex min-h-12 items-center justify-between gap-3 rounded-2xl border border-slate-800 bg-slate-900/50 px-4 py-3">
        <span className="flex items-center gap-2 text-sm text-slate-300">
          <Bell className="h-4 w-4 text-amber-400" />
          Alerts
        </span>
        {notifPermission === 'granted' ? (
          <span className="text-xs font-semibold text-emerald-400">Allowed</span>
        ) : (
          <button onClick={handleRequestNotif} className="min-h-9 rounded-lg px-2 text-xs font-semibold text-amber-300 underline underline-offset-4 hover:text-amber-200">
            Enable notifications
          </button>
        )}
      </div>    </div>
  );
};
