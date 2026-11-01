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
  Radio,
  ChevronDown,
} from 'lucide-react';
import { CCTVRadar } from './CCTVRadar';
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
  micError: string | null;
  storageWarningActive: boolean;
  settings: CCTVSettings;
  onUpdateSettings: (newSettings: Partial<CCTVSettings>) => void;
  onSimulateDemo: (db: number, hint: string) => void;
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
  micError,
  storageWarningActive,
  settings,
  onUpdateSettings,
  onSimulateDemo,
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
              You can still use the <strong>Test Sound triggers</strong> below to verify sound detection, the 10-second post-roll countdown, and automated storage overwriting!
            </span>
          </div>
        </div>
      )}

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

      {/* Radar HUD View */}
      <CCTVRadar
        isArmed={isArmed}
        soundLevel={soundLevel}
        threshold={threshold}
        rawDecibels={currentDecibels}
        isRecording={isRecording}
        silenceCountdown={silenceCountdown}
        recordingDuration={recordingDuration}
        triggersCount={triggersCount}
      />

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

        {/* Sensitivity Presets */}
        <div className="grid grid-cols-3 gap-2">
          {[
            { label: 'Low (Loud)', val: 65, desc: 'Claps, shouts' },
            { label: 'Medium', val: 45, desc: 'Normal voice' },
            { label: 'High (Sensitive)', val: 25, desc: 'Whispers, steps' },
          ].map((preset) => (
            <button
              key={preset.label}
              onClick={() => onUpdateSettings({ sensitivityThreshold: preset.val })}
              className={`min-h-16 p-3 rounded-xl text-left border transition-all ${
                threshold === preset.val
                  ? 'bg-emerald-950/60 border-emerald-500/80 text-emerald-200'
                  : 'bg-slate-950/50 border-slate-800 text-slate-400 hover:border-slate-700'
              }`}
            >
              <div className="text-xs font-bold">{preset.label}</div>
              <div className="mt-1 text-[11px] text-slate-400">{preset.desc}</div>
            </button>
          ))}
        </div>

        {/* Fine-grain threshold slider */}
        <div className="pt-1">
          <input
            type="range"
            min="10"
            max="90"
            value={threshold}
            onChange={(e) => onUpdateSettings({ sensitivityThreshold: Number(e.target.value) })}
            className="w-full accent-emerald-500 cursor-pointer h-2 bg-slate-800 rounded-lg appearance-none"
            aria-label="Adjust sound sensitivity threshold"
          />
          <div className="flex justify-between text-[11px] text-slate-400 mt-2">
            <span>High Sensitivity (Quiet)</span>
            <span>Low Sensitivity (Loud)</span>
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
          <p className="rounded-xl border border-amber-900/60 bg-amber-950/30 p-3 text-xs text-amber-200/90">Keep this screen open while monitoring. Mobile browsers may pause microphone work when you switch apps or lock the phone.</p>
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
        <p className="text-xs text-slate-400 leading-relaxed">
          Gemini classifies recordings when the server key is configured. Without a key, the app uses its local heuristic classifier.
        </p>
      </div>

      <details className="group rounded-2xl border border-slate-800 bg-slate-900/60">
        <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-slate-200">
          <span className="flex items-center gap-2"><Radio className="h-4 w-4 text-amber-400" />Try a demo event</span>
          <ChevronDown className="h-4 w-4 shrink-0 text-slate-500 transition-transform group-open:rotate-180" />
        </summary>
        <div className="space-y-3 px-4 pb-4">
          <p className="text-xs leading-relaxed text-slate-400">Adds a clearly labeled sample to the audit log. It does not arm the microphone, record audio, or contact Gemini.</p>
          <div className="grid grid-cols-2 gap-2.5">
            <button onClick={() => onSimulateDemo(88, 'Glass Breaking')} className="min-h-14 rounded-xl border border-red-900/50 bg-slate-800 p-3 text-left text-sm font-semibold text-red-200 transition hover:bg-slate-700 active:scale-[0.98]">Glass break <span className="mt-1 block text-xs font-normal text-slate-400">High alert</span></button>
            <button onClick={() => onSimulateDemo(48, 'Footsteps')} className="min-h-14 rounded-xl border border-amber-900/50 bg-slate-800 p-3 text-left text-sm font-semibold text-amber-200 transition hover:bg-slate-700 active:scale-[0.98]">Footsteps <span className="mt-1 block text-xs font-normal text-slate-400">Movement</span></button>
            <button onClick={() => onSimulateDemo(58, 'Human Voice / Speech')} className="min-h-14 rounded-xl border border-blue-900/50 bg-slate-800 p-3 text-left text-sm font-semibold text-blue-200 transition hover:bg-slate-700 active:scale-[0.98]">Human voice <span className="mt-1 block text-xs font-normal text-slate-400">Speech</span></button>
            <button onClick={() => onSimulateDemo(75, 'Door Knock')} className="min-h-14 rounded-xl border border-slate-700 bg-slate-800 p-3 text-left text-sm font-semibold text-slate-100 transition hover:bg-slate-700 active:scale-[0.98]">Door knock <span className="mt-1 block text-xs font-normal text-slate-400">Impact</span></button>
          </div>
        </div>
      </details>

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
