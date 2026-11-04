import React, { useState } from 'react';
import {
  HardDrive,
  Calendar,
  Clock,
  ShieldAlert,
  AlertTriangle,
  RotateCcw,
  Sliders,
  CheckCircle2,
  Trash2,
  Lock,
  Bell,
  Cpu,
} from 'lucide-react';
import { CCTVSettings, StorageStats, RetentionMode } from '../types';
import { formatBytes, getRetentionDurationMs } from '../utils/cctvLogic';
import { notificationService } from '../utils/notifications';
import { isAndroidNativeApp } from '../native/backgroundMonitor';

interface StorageManagerProps {
  settings: CCTVSettings;
  storageStats: StorageStats;
  onUpdateSettings: (newSettings: Partial<CCTVSettings>) => void;
  onClearUnprotected: () => void;
}

export const StorageManager: React.FC<StorageManagerProps> = ({
  settings,
  storageStats,
  onUpdateSettings,
  onClearUnprotected,
}) => {
  const [customQuotaInput, setCustomQuotaInput] = useState<string>(String(settings.storageQuotaMB));
  const [customHoursInput, setCustomHoursInput] = useState<string>(String(settings.customRetentionHours));
  const [testNotifSent, setTestNotifSent] = useState<boolean>(false);
  const [isTestingAI, setIsTestingAI] = useState(false);
  const [aiTestResult, setAiTestResult] = useState<{ kind: 'success' | 'warning' | 'error'; message: string } | null>(null);

  const quotaPresets = [25, 50, 100, 250, 500, 1000];

  const retentionOptions: { mode: RetentionMode; label: string; desc: string }[] = [
    { mode: '1h', label: '1 Hour', desc: 'Overwrites after 60 mins' },
    { mode: '6h', label: '6 Hours', desc: 'Overwrites after 6h' },
    { mode: '24h', label: '24 Hours', desc: 'Daily CCTV cycle' },
    { mode: '3d', label: '3 Days', desc: '72-hour retention' },
    { mode: '7d', label: '7 Days', desc: '1 week surveillance' },
    { mode: '30d', label: '30 Days', desc: 'Monthly rolling archive' },
    { mode: 'custom', label: 'Custom Time', desc: 'Specify exact hours' },
    { mode: 'never', label: 'Only on Full', desc: 'Keep until storage limit' },
  ];

  const handleQuotaChange = (mb: number) => {
    onUpdateSettings({ storageQuotaMB: mb });
    setCustomQuotaInput(String(mb));
  };

  const handleCustomQuotaSubmit = () => {
    const val = parseInt(customQuotaInput, 10);
    if (!isNaN(val) && val >= 5) {
      onUpdateSettings({ storageQuotaMB: val });
    }
  };

  const handleCustomHoursSubmit = () => {
    const val = parseInt(customHoursInput, 10);
    if (!isNaN(val) && val >= 1) {
      onUpdateSettings({ customRetentionHours: val, retentionMode: 'custom' });
    }
  };

  const handleTestWarning = () => {
    notificationService.sendStorageWarningNotification(
      storageStats.usedPercentage,
      (storageStats.usedBytes / (1024 * 1024)).toFixed(1),
      settings.storageQuotaMB
    );
    setTestNotifSent(true);
    setTimeout(() => setTestNotifSent(false), 3000);
  };

  const handleTestAI = async () => {
    const baseUrl = (settings.apiBaseUrl || '').trim().replace(/\/+$/, '');
    if (!baseUrl && isAndroidNativeApp()) {
      setAiTestResult({ kind: 'error', message: 'Enter the laptop Wi-Fi address (for example http://192.168.1.25:3000) first. The installed app cannot reach the laptop through its own localhost address.' });
      return;
    }

    setIsTestingAI(true);
    setAiTestResult(null);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 45000);
    try {
      const response = await fetch(`${baseUrl}/api/classify-audio`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(settings.apiAccessToken ? { Authorization: `Bearer ${settings.apiAccessToken}` } : {}),
        },
        signal: controller.signal,
        body: JSON.stringify({
          simulatedTag: 'Connection test only: classify a repeating smoke alarm beep. No audio is recorded or uploaded.',
          peakDecibels: 55,
          avgDecibels: 30,
          duration: 2,
          triggersCount: 1,
        }),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        setAiTestResult({ kind: 'error', message: result.error || `AI server returned HTTP ${response.status}.` });
      } else if (result.engine === 'gemini') {
        setAiTestResult({ kind: 'success', message: `Gemini is working. Test result: ${result.tag || 'sound event'} (${Math.round((result.confidence || 0) * 100)}% confidence).` });
      } else {
        setAiTestResult({ kind: 'warning', message: result.aiMessage || 'The server responded, but this result came from the local heuristic rather than Gemini.' });
      }
    } catch (error) {
      setAiTestResult({ kind: 'error', message: error instanceof Error && error.name === 'AbortError' ? 'AI test timed out. Check that the laptop server is running and both devices are on the same Wi-Fi.' : 'Could not reach the AI server. Check the laptop address, server, Wi-Fi, and firewall.' });
    } finally {
      clearTimeout(timeout);
      setIsTestingAI(false);
    }
  };

  // Retention time calculation
  const retentionMs = getRetentionDurationMs(settings.retentionMode, settings.customRetentionHours);
  const expirationDate = retentionMs !== Infinity ? new Date(Date.now() + retentionMs) : null;

  return (
    <div className="flex flex-col gap-4 pb-12">
      {/* Header */}
      <div>
        <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
          <HardDrive className="w-5 h-5 text-emerald-400" />
          Phone Storage & Overwrite Settings
        </h3>
        <p className="text-xs text-slate-400">
          Control maximum storage allocation, FIFO circular overwrite, and retention rules
        </p>
        <p className="text-[11px] text-slate-500">
          Recordings are stored in this app’s private space. Android does not need broad storage permission for recording or showing these clips.
        </p>
      </div>

      <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-4 space-y-2">
        <label htmlFor="classification-server" className="text-xs font-bold text-slate-200 uppercase tracking-wider font-mono">
          Laptop AI Server
        </label>
        <input
          id="classification-server"
          type="url"
          inputMode="url"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          value={settings.apiBaseUrl || ''}
          onChange={(event) => onUpdateSettings({ apiBaseUrl: event.target.value.trim().replace(/\/$/, '') })}
          placeholder="http://192.168.1.25:3000"
          className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
          aria-describedby="classification-server-help"
        />
        <input
          type="password"
          autoComplete="off"
          value={settings.apiAccessToken || ''}
          onChange={(event) => onUpdateSettings({ apiAccessToken: event.target.value })}
          placeholder="Server access token (required for LAN AI)"
          className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
          aria-label="Laptop server access token"
        />
        <p id="classification-server-help" className="text-[11px] text-slate-400 leading-relaxed">
          In the installed Android app, enter your laptop’s local Wi-Fi address and port 3000. Keep both devices on the same trusted network. Set APP_API_TOKEN in the laptop’s ignored .env file and enter the same token here for protected LAN access. The Gemini key stays on the laptop and is never put in the APK. A connection test sends a short text prompt only, not recorded audio.
        </p>
        <button
          type="button"
          onClick={handleTestAI}
          disabled={isTestingAI}
          className="min-h-10 w-full rounded-xl border border-emerald-700/70 bg-emerald-950/60 px-3 py-2 text-xs font-bold text-emerald-200 transition hover:bg-emerald-900/70 disabled:cursor-wait disabled:opacity-60"
        >
          {isTestingAI ? 'Testing AI connection…' : 'Test AI connection'}
        </button>
        {aiTestResult && (
          <p role="status" className={`rounded-xl border px-3 py-2 text-xs leading-relaxed ${
            aiTestResult.kind === 'success'
              ? 'border-emerald-800/70 bg-emerald-950/30 text-emerald-200'
              : aiTestResult.kind === 'warning'
                ? 'border-amber-800/70 bg-amber-950/30 text-amber-200'
                : 'border-red-900/70 bg-red-950/30 text-red-200'
          }`}>
            {aiTestResult.message}
          </p>
        )}
      </div>

      {/* Storage Meter Card */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-4 space-y-3 shadow-xl">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono font-bold text-slate-200">STORAGE CAPACITY</span>
            {storageStats.isNearFull && (
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 font-bold border border-amber-500/40 animate-pulse">
                NEAR FULL ({storageStats.usedPercentage}%)
              </span>
            )}
          </div>
          <span className="text-xs font-mono text-slate-400">
            {formatBytes(storageStats.usedBytes)} / {settings.storageQuotaMB} MB
          </span>
        </div>

        {/* Storage Bar with 85% Warning Marker */}
        <div className="relative w-full h-4 bg-slate-800 rounded-full overflow-hidden p-0.5">
          <div
            className={`h-full rounded-full transition-all duration-300 ${
              storageStats.isNearFull
                ? 'bg-gradient-to-r from-amber-500 to-red-500 animate-pulse'
                : 'bg-gradient-to-r from-emerald-500 to-teal-400'
            }`}
            style={{ width: `${Math.min(100, storageStats.usedPercentage)}%` }}
          />
          {/* Warning threshold vertical line at 85% */}
          <div
            className="absolute top-0 bottom-0 w-0.5 bg-amber-400/90 z-10"
            style={{ left: `${settings.warningThresholdPercent}%` }}
            title={`Warning Threshold (${settings.warningThresholdPercent}%)`}
          />
        </div>

        <div className="flex items-center justify-between text-[11px] font-mono text-slate-400 pt-0.5">
          <span>{storageStats.recordingsCount} recordings stored</span>
          <span className="text-amber-400 flex items-center gap-1">
            <Lock className="w-3 h-3" /> {storageStats.protectedCount} locked / protected
          </span>
        </div>

        {/* Warning notification banner if near full */}
        {storageStats.isNearFull && (
          <div className="p-3 rounded-xl bg-amber-950/40 border border-amber-800/80 text-amber-200 text-xs space-y-1.5">
            <div className="flex items-center gap-2 font-bold text-amber-300">
              <ShieldAlert className="w-4 h-4 text-amber-400" />
              <span>Storage Limit Approaching!</span>
            </div>
            <p className="text-[11px] leading-relaxed text-amber-200/90">
              Storage has crossed {settings.warningThresholdPercent}%. If ignored,
              <strong> oldest recordings will be automatically deleted</strong> to make room for new audio
              events. Locked recordings remain safe.
            </p>
            <button
              onClick={onClearUnprotected}
              className="mt-1 text-[11px] font-mono bg-amber-600 hover:bg-amber-500 text-slate-950 font-bold px-3 py-1 rounded-lg transition active:scale-95"
            >
              Clean Old Unprotected Files Now
            </button>
          </div>
        )}
      </div>

      {/* 1. Storage Quota Allocation */}
      <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <label className="text-xs font-bold text-slate-200 uppercase tracking-wider font-mono">
            Maximum Storage Quota (Phone Allocation)
          </label>
          <span className="text-xs font-mono font-bold text-emerald-400">
            {settings.storageQuotaMB} MB
          </span>
        </div>

        <div className="grid grid-cols-3 gap-2">
          {quotaPresets.map((mb) => (
            <button
              key={mb}
              onClick={() => handleQuotaChange(mb)}
              className={`p-2 rounded-xl text-center font-mono text-xs border transition ${
                settings.storageQuotaMB === mb
                  ? 'bg-emerald-950/80 border-emerald-500 text-emerald-200 font-bold'
                  : 'bg-slate-950/60 border-slate-800 text-slate-400 hover:border-slate-700'
              }`}
            >
              {mb >= 1000 ? `${mb / 1000} GB` : `${mb} MB`}
            </button>
          ))}
        </div>

        {/* Custom Quota Input */}
        <div className="flex items-center gap-2 pt-1">
          <input
            type="number"
            min="5"
            max="10000"
            value={customQuotaInput}
            onChange={(e) => setCustomQuotaInput(e.target.value)}
            placeholder="Custom MB"
            className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
          />
          <button
            onClick={handleCustomQuotaSubmit}
            className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-mono text-slate-200"
          >
            Set Custom MB
          </button>
        </div>
      </div>

      {/* 2. Automated FIFO Oldest-First Overwrite Policy */}
      <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-4 space-y-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <RotateCcw className="w-4 h-4 text-emerald-400" />
            <span className="text-xs font-bold text-slate-200 uppercase tracking-wider font-mono">
              Auto-Delete Oldest When Full (FIFO)
            </span>
          </div>
          <button
            onClick={() => onUpdateSettings({ autoPruneOnFull: !settings.autoPruneOnFull })}
            className={`w-11 h-6 rounded-full transition-colors relative p-0.5 ${
              settings.autoPruneOnFull ? 'bg-emerald-500' : 'bg-slate-800'
            }`}
            aria-label="Toggle auto delete oldest files on full"
          >
            <div
              className={`w-5 h-5 rounded-full bg-white transition-transform ${
                settings.autoPruneOnFull ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        <p className="text-[11px] text-slate-400 leading-relaxed">
          Matches CCTV hardware DVR behavior: if the notification is ignored or storage limit is reached,
          the system automatically purges the oldest unprotected files to make continuous room.
        </p>

        <div className="pt-2 flex items-center justify-between text-xs font-mono text-slate-400 border-t border-slate-800">
          <span>Warning Alert Threshold:</span>
          <select
            value={settings.warningThresholdPercent}
            onChange={(e) => onUpdateSettings({ warningThresholdPercent: Number(e.target.value) })}
            className="bg-slate-950 border border-slate-800 rounded-lg px-2 py-1 text-xs text-slate-200 font-mono"
            aria-label="Select storage warning threshold"
          >
            <option value={75}>At 75% capacity</option>
            <option value={80}>At 80% capacity</option>
            <option value={85}>At 85% capacity (Default)</option>
            <option value={90}>At 90% capacity</option>
          </select>
        </div>
      </div>

      {/* 3. Custom Date & Time Retention Setting */}
      <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Calendar className="w-4 h-4 text-emerald-400" />
            <span className="text-xs font-bold text-slate-200 uppercase tracking-wider font-mono">
              Recording Retention Period
            </span>
          </div>
          <span className="text-xs font-mono font-bold text-emerald-400">
            {settings.retentionMode.toUpperCase()}
          </span>
        </div>

        <p className="text-[11px] text-slate-400">
          Set how long audio remains in your phone before being automatically overwritten.
        </p>

        <div className="grid grid-cols-2 gap-2">
          {retentionOptions.map((opt) => (
            <button
              key={opt.mode}
              onClick={() => onUpdateSettings({ retentionMode: opt.mode })}
              className={`p-2.5 rounded-xl text-left border transition ${
                settings.retentionMode === opt.mode
                  ? 'bg-emerald-950/80 border-emerald-500 text-emerald-200'
                  : 'bg-slate-950/60 border-slate-800 text-slate-400 hover:border-slate-700'
              }`}
            >
              <div className="text-xs font-bold font-mono">{opt.label}</div>
              <div className="text-[10px] text-slate-500">{opt.desc}</div>
            </button>
          ))}
        </div>

        {/* Custom Hours / Days Configuration */}
        {settings.retentionMode === 'custom' && (
          <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-2">
            <div className="text-xs font-mono font-semibold text-slate-300">
              Custom Retention Duration
            </div>
            <div className="flex items-center gap-2">
              <input
                type="number"
                min="1"
                max="720"
                value={customHoursInput}
                onChange={(e) => setCustomHoursInput(e.target.value)}
                placeholder="Hours (e.g. 12, 48)"
                className="flex-1 bg-slate-900 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
              />
              <span className="text-xs text-slate-400 font-mono">Hours</span>
              <button
                onClick={handleCustomHoursSubmit}
                className="px-3 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 font-bold text-xs text-slate-950 font-mono"
              >
                Apply
              </button>
            </div>
            {expirationDate && (
              <div className="text-[10px] text-emerald-400 font-mono">
                Audio will expire after {settings.customRetentionHours} hours (~ {expirationDate.toLocaleDateString()} {expirationDate.toLocaleTimeString()})
              </div>
            )}
          </div>
        )}
      </div>

      {/* 4. Notification Testing & Haptics */}
      <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-4 space-y-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bell className="w-4 h-4 text-amber-400" />
            <span className="text-xs font-bold text-slate-200 uppercase tracking-wider font-mono">
              Notifications & Warnings
            </span>
          </div>
          <button
            onClick={handleTestWarning}
            className="text-[11px] font-mono text-amber-400 hover:text-amber-300 bg-amber-500/10 border border-amber-500/30 px-2.5 py-1 rounded-lg"
          >
            {testNotifSent ? 'Dispatched!' : 'Test Storage Alert'}
          </button>
        </div>

        <div className="space-y-2 pt-1 text-xs">
          <label className="flex items-center justify-between text-slate-300 cursor-pointer">
            <span>Sound Detection Instant Notifications</span>
            <input
              type="checkbox"
              checked={settings.enableNotifications}
              onChange={(e) => onUpdateSettings({ enableNotifications: e.target.checked })}
              className="accent-emerald-500 w-4 h-4 rounded"
            />
          </label>

          <label className="flex items-center justify-between text-slate-300 cursor-pointer">
            <span>Phone Haptic Vibration on Trigger</span>
            <input
              type="checkbox"
              checked={settings.enableVibration}
              onChange={(e) => onUpdateSettings({ enableVibration: e.target.checked })}
              className="accent-emerald-500 w-4 h-4 rounded"
            />
          </label>

          <label className="flex items-center justify-between text-slate-300 cursor-pointer">
            <span>Screen Wake Lock (Keep phone active while monitoring)</span>
            <input
              type="checkbox"
              checked={settings.enableScreenWakeLock}
              onChange={(e) => onUpdateSettings({ enableScreenWakeLock: e.target.checked })}
              className="accent-emerald-500 w-4 h-4 rounded"
            />
          </label>

          <label className="flex items-center justify-between text-slate-300 cursor-pointer">
            <span>Background audio attempt (phone support varies)</span>
            <input
              type="checkbox"
              checked={settings.enableKeepAliveAudio}
              onChange={(e) => onUpdateSettings({ enableKeepAliveAudio: e.target.checked })}
              className="accent-emerald-500 w-4 h-4 rounded"
            />
          </label>
        </div>
      </div>

    </div>
  );
};
