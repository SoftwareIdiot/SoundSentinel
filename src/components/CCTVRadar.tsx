import React from 'react';
import { Volume2, VolumeX, Radio, ShieldAlert } from 'lucide-react';

interface CCTVRadarProps {
  isArmed: boolean;
  soundLevel: number; // 0 - 100
  threshold: number; // 0 - 100
  rawDecibels: number; // -80 to 0 dB
  isRecording: boolean;
  silenceCountdown: number; // seconds remaining
  recordingDuration: number;
  triggersCount: number;
}

export const CCTVRadar: React.FC<CCTVRadarProps> = ({
  isArmed,
  soundLevel,
  threshold,
  rawDecibels,
  isRecording,
  silenceCountdown,
  recordingDuration,
  triggersCount,
}) => {
  const isTriggered = soundLevel >= threshold;

  // Normalized threshold radius for circular radar (from 20% to 90%)
  const thresholdRadius = 25 + (threshold / 100) * 65;
  const currentSoundRadius = 20 + (soundLevel / 100) * 75;

  return (
    <div className="relative w-full aspect-square max-w-[320px] mx-auto rounded-3xl bg-gradient-to-b from-slate-900 via-slate-950 to-slate-900 border border-slate-800/80 p-4 shadow-2xl overflow-hidden flex flex-col items-center justify-center">
      {/* Subtle CCTV CRT scanline overlay */}
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-transparent via-slate-950/40 to-slate-950/90 z-10" />
      <div 
        className="pointer-events-none absolute inset-0 opacity-[0.03] z-10"
        style={{
          backgroundImage: 'linear-gradient(rgba(16, 185, 129, 0.4) 1px, transparent 1px)',
          backgroundSize: '100% 4px',
        }}
      />

      {/* CCTV HUD Header Overlay */}
      <div className="absolute top-3 left-4 right-4 flex items-center justify-between text-xs font-mono z-20">
        <div className="flex items-center gap-1.5">
          <span className={`w-2 h-2 rounded-full ${isArmed ? (isRecording ? 'bg-red-500 animate-ping' : 'bg-emerald-500 animate-pulse') : 'bg-slate-600'}`} />
          <span className={isArmed ? (isRecording ? 'text-red-400 font-bold' : 'text-emerald-400') : 'text-slate-500'}>
            {isArmed ? (isRecording ? 'RECORDING' : 'ARMED') : 'STANDBY'}
          </span>
        </div>
        <div className="text-slate-400 flex items-center gap-2">
          <span>{rawDecibels > -79 ? `${rawDecibels.toFixed(0)} dB` : 'QUIET'}</span>
          <span className="text-slate-600">|</span>
          <span className="text-emerald-500/80">TRIG: {threshold}%</span>
        </div>
      </div>

      {/* Radar SVG Display */}
      <div className="relative w-full h-full flex items-center justify-center">
        <svg className="w-full h-full max-w-[270px] max-h-[270px]" viewBox="0 0 200 200">
          <defs>
            <radialGradient id="soundGlow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor={isTriggered ? '#ef4444' : '#10b981'} stopOpacity="0.4" />
              <stop offset="70%" stopColor={isTriggered ? '#ef4444' : '#10b981'} stopOpacity="0.1" />
              <stop offset="100%" stopColor="#090d16" stopOpacity="0" />
            </radialGradient>
            <linearGradient id="scanBeam" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stopColor="#10b981" stopOpacity="0.4" />
              <stop offset="100%" stopColor="#10b981" stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* Concentric radar rings */}
          <circle cx="100" cy="100" r="90" fill="none" stroke="#1e293b" strokeWidth="1" strokeDasharray="3 3" />
          <circle cx="100" cy="100" r="70" fill="none" stroke="#1e293b" strokeWidth="1" />
          <circle cx="100" cy="100" r="50" fill="none" stroke="#1e293b" strokeWidth="1" strokeDasharray="4 4" />
          <circle cx="100" cy="100" r="30" fill="none" stroke="#1e293b" strokeWidth="1" />

          {/* Crosshairs */}
          <line x1="100" y1="10" x2="100" y2="190" stroke="#334155" strokeWidth="0.75" />
          <line x1="10" y1="100" x2="190" y2="100" stroke="#334155" strokeWidth="0.75" />

          {/* Current Sound Wave expansion circle */}
          {isArmed && (
            <circle
              cx="100"
              cy="100"
              r={Math.min(95, currentSoundRadius)}
              fill="url(#soundGlow)"
              stroke={isTriggered ? '#ef4444' : '#10b981'}
              strokeWidth={isTriggered ? 2.5 : 1.5}
              strokeOpacity={isTriggered ? 0.9 : 0.6}
              className="transition-all duration-75"
            />
          )}

          {/* User Sensitivity Threshold boundary circle */}
          <circle
            cx="100"
            cy="100"
            r={thresholdRadius}
            fill="none"
            stroke="#f59e0b"
            strokeWidth="1.5"
            strokeDasharray="5 3"
            strokeOpacity="0.75"
          />

          {/* Center CCTV Acoustic Sensor Dot */}
          <circle cx="100" cy="100" r="14" fill="#0f172a" stroke={isArmed ? (isTriggered ? '#ef4444' : '#10b981') : '#475569'} strokeWidth="2" />
          <circle cx="100" cy="100" r="6" fill={isArmed ? (isRecording ? '#ef4444' : '#10b981') : '#64748b'} className={isArmed ? 'animate-pulse' : ''} />
        </svg>

        {/* Center Readout Overlay */}
        <div className="absolute flex flex-col items-center justify-center text-center pointer-events-none">
          {isRecording ? (
            <div className="flex flex-col items-center animate-fade-in">
              <span className="flex items-center gap-1 text-[11px] font-mono font-bold text-red-400 bg-red-950/80 px-2 py-0.5 rounded-full border border-red-800">
                <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-ping" />
                RECORDING {recordingDuration}s
              </span>
              <span className="text-[10px] text-amber-300 font-mono mt-1 font-semibold">
                {isTriggered ? 'SOUND ACTIVE (+10s)' : `CUT IN: ${silenceCountdown.toFixed(1)}s`}
              </span>
              {triggersCount > 1 && (
                <span className="text-[9px] text-red-300/80 font-mono">
                  Extended {triggersCount}x
                </span>
              )}
            </div>
          ) : isArmed ? (
            <div className="flex flex-col items-center">
              <span className="text-2xl font-mono font-black text-slate-100 tracking-wider">
                {soundLevel}
              </span>
              <span className="text-[10px] font-mono text-slate-400 uppercase tracking-widest">
                Level / 100
              </span>
            </div>
          ) : (
            <div className="flex flex-col items-center text-slate-500">
              <VolumeX className="w-6 h-6 mb-1 text-slate-600" />
              <span className="text-[11px] font-mono font-semibold">DISARMED</span>
            </div>
          )}
        </div>
      </div>

      {/* CCTV HUD Bottom Bar: Post-Roll Progress or Threshold Status */}
      <div className="w-full mt-1 z-20">
        {isRecording ? (
          <div className="space-y-1">
            <div className="flex justify-between items-center text-[10px] font-mono text-slate-300">
              <span className="flex items-center gap-1 text-amber-400">
                <Radio className="w-3 h-3 animate-pulse" />
                {isTriggered ? 'Sound Detected: Countdown Frozen at 10s' : 'Silence: Counting down 10s post-roll'}
              </span>
              <span className="font-bold text-red-400">{silenceCountdown.toFixed(1)}s</span>
            </div>
            {/* Progress bar for 10-second silence countdown */}
            <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all duration-100 rounded-full ${isTriggered ? 'bg-red-500' : 'bg-amber-400'}`}
                style={{ width: `${(silenceCountdown / 10) * 100}%` }}
              />
            </div>
          </div>
        ) : (
          <div className="flex items-center justify-between text-[11px] font-mono text-slate-400 px-1">
            <span className="flex items-center gap-1">
              <Volume2 className="w-3.5 h-3.5 text-emerald-400" />
              Threshold: {threshold}
            </span>
            <span className={soundLevel >= threshold ? 'text-red-400 font-bold' : 'text-slate-500'}>
              {soundLevel >= threshold ? 'TRIGGERED' : 'QUIET'}
            </span>
          </div>
        )}
      </div>
    </div>
  );
};
