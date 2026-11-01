import React, { useState, useEffect } from 'react';
import {
  Radio,
  FileAudio,
  HardDrive,
  ListChecks,
  Maximize2,
  Minimize2,
} from 'lucide-react';
import { useAcousticCCTV } from './hooks/useAcousticCCTV';
import { SurveillanceScreen } from './components/SurveillanceScreen';
import { RecordingsVault } from './components/RecordingsVault';
import { StorageManager } from './components/StorageManager';
import { EventAuditLog } from './components/EventAuditLog';
import { PWAInstallButton } from './components/PWAInstallButton';

export default function App() {
  const [activeTab, setActiveTab] = useState<'surveillance' | 'vault' | 'storage' | 'log'>('surveillance');
  const [isMobileFrameMode, setIsMobileFrameMode] = useState<boolean>(true);
  const [currentTimeCode, setCurrentTimeCode] = useState<string>('');

  const {
    settings,
    updateSettings,
    isArmed,
    setArmed,
    recordings,
    events,
    storageStats,
    currentDecibels,
    currentSoundLevel,
    isRecording,
    silenceCountdown,
    recordingDuration,
    triggersInCurrentSession,
    isMicActive,
    micError,
    storageWarningActive,
    simulateSoundSpike,
    simulateDemoEvent,
    classifyExistingRecording,
    deleteRecording,
    toggleProtect,
    clearUnprotectedRecordings,
  } = useAcousticCCTV();

  // CCTV live timecode clock
  useEffect(() => {
    const updateClock = () => {
      const now = new Date();
      const pad = (n: number) => n.toString().padStart(2, '0');
      const datePart = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
      const timePart = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
      setCurrentTimeCode(`${datePart} ${timePart}`);
    };
    updateClock();
    const timer = setInterval(updateClock, 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center justify-start sm:p-4 selection:bg-emerald-500 selection:text-slate-950 font-sans">
      {/* Outer Shell Wrapper (Mobile Frame or Full Responsive) */}
      <div
        className={`w-full transition-all duration-300 flex flex-col ${
          isMobileFrameMode
            ? 'max-w-[460px] min-h-screen sm:min-h-[880px] sm:my-4 sm:rounded-[36px] sm:border-[8px] sm:border-slate-800 sm:shadow-2xl sm:shadow-emerald-950/20 bg-slate-950 overflow-hidden relative'
            : 'max-w-4xl min-h-screen bg-slate-950 p-4'
        }`}
      >
        {/* Top Mobile Status & Brand Header */}
        <header className="sticky top-0 z-40 bg-slate-950/95 backdrop-blur-md border-b border-slate-800/80 px-4 py-3 flex items-center justify-between" style={{ paddingTop: 'calc(0.75rem + env(safe-area-inset-top))' }}>
          <div className="flex items-center gap-2.5">
            <div className="relative">
              <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-slate-900 via-slate-800 to-emerald-950 border border-emerald-500/30 flex items-center justify-center shadow-md">
                <Radio className={`w-4 h-4 ${isArmed ? 'text-emerald-400' : 'text-slate-400'}`} />
              </div>
              {isArmed && (
                <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-emerald-500 ring-2 ring-slate-950 animate-pulse" />
              )}
            </div>

            <div>
              <div className="flex items-center gap-1.5">
                <h1 className="text-sm font-mono font-black text-slate-100 uppercase tracking-wider">
                  SoundSentinel
                </h1>
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-bold">
                  AUDIO
                </span>
              </div>
              <div className="text-[11px] font-mono text-slate-400 flex items-center gap-1">
                <span>{currentTimeCode || '2026-11-01 00:00:00'}</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Flashing Recording Indicator */}
            {isRecording && (
              <div className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-red-950/80 border border-red-800 text-[10px] font-mono text-red-400 font-bold animate-pulse">
                <span className="w-2 h-2 rounded-full bg-red-500 animate-ping" />
                REC {recordingDuration}s
              </div>
            )}

            {/* PWA Install Button */}
            <PWAInstallButton />

            {/* Frame mode toggle (hidden on small phone viewports) */}
            <button
              onClick={() => setIsMobileFrameMode(!isMobileFrameMode)}
              className="hidden sm:flex p-1.5 rounded-lg text-slate-400 hover:text-slate-200 bg-slate-900 border border-slate-800 text-xs"
              title={isMobileFrameMode ? 'Expand to Full View' : 'Switch to Mobile Frame View'}
              aria-label="Toggle mobile device frame"
            >
              {isMobileFrameMode ? <Maximize2 className="w-3.5 h-3.5" /> : <Minimize2 className="w-3.5 h-3.5" />}
            </button>
          </div>
        </header>

        {/* Main Content Area */}
        <main className="flex-1 p-4 pb-24 overflow-y-auto sm:p-5">
          {activeTab === 'surveillance' && (
            <SurveillanceScreen
              isArmed={isArmed}
              setArmed={setArmed}
              soundLevel={currentSoundLevel}
              currentDecibels={currentDecibels}
              threshold={settings.sensitivityThreshold}
              isRecording={isRecording}
              silenceCountdown={silenceCountdown}
              recordingDuration={recordingDuration}
              triggersCount={triggersInCurrentSession}
              isMicActive={isMicActive}
              micError={micError}
              storageWarningActive={storageWarningActive}
              settings={settings}
              onUpdateSettings={updateSettings}
              onSimulateDemo={simulateDemoEvent}
            />
          )}

          {activeTab === 'vault' && (
            <RecordingsVault
              recordings={recordings}
              onDeleteRecording={deleteRecording}
              onToggleProtect={toggleProtect}
              onClearUnprotected={clearUnprotectedRecordings}
              onSimulateSound={simulateSoundSpike}
              onArm={setArmed}
              onReclassify={classifyExistingRecording}
            />
          )}

          {activeTab === 'storage' && (
            <StorageManager
              settings={settings}
              storageStats={storageStats}
              onUpdateSettings={updateSettings}
              onClearUnprotected={clearUnprotectedRecordings}
            />
          )}

          {activeTab === 'log' && <EventAuditLog events={events} />}
        </main>

        {/* Mobile Bottom Navigation Bar */}
        <nav className="fixed sm:absolute bottom-0 left-0 right-0 z-40 bg-slate-950/95 backdrop-blur-xl border-t border-slate-800/80 px-2 pt-2 flex items-center justify-around" style={{ paddingBottom: 'calc(0.5rem + env(safe-area-inset-bottom))' }}>
          <button
            onClick={() => setActiveTab('surveillance')}
            aria-label="Surveillance View"
            className={`flex min-w-16 min-h-12 flex-col items-center justify-center gap-1 py-1 px-2 rounded-xl transition-all ${
              activeTab === 'surveillance'
                ? 'text-emerald-400 font-bold'
                : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            <div className="relative">
              <Radio className="w-5 h-5" />
              {isArmed && (
                <span className="absolute -top-0.5 -right-0.5 w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping" />
              )}
            </div>
            <span className="text-[10px] font-mono">Sentinel</span>
          </button>

          <button
            onClick={() => setActiveTab('vault')}
            aria-label="Recordings Vault"
            className={`flex min-w-16 min-h-12 flex-col items-center justify-center gap-1 py-1 px-2 rounded-xl transition-all ${
              activeTab === 'vault'
                ? 'text-emerald-400 font-bold'
                : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            <div className="relative">
              <FileAudio className="w-5 h-5" />
              {recordings.length > 0 && (
                <span className="absolute -top-1 -right-2 px-1 py-0.1 rounded-full bg-emerald-500/20 text-emerald-400 text-[8px] font-mono font-bold">
                  {recordings.length}
                </span>
              )}
            </div>
            <span className="text-[10px] font-mono">Vault</span>
          </button>

          <button
            onClick={() => setActiveTab('storage')}
            aria-label="Storage Management"
            className={`flex min-w-16 min-h-12 flex-col items-center justify-center gap-1 py-1 px-2 rounded-xl transition-all ${
              activeTab === 'storage'
                ? 'text-emerald-400 font-bold'
                : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            <div className="relative">
              <HardDrive className="w-5 h-5" />
              {storageWarningActive && (
                <span className="absolute -top-1 -right-1 w-2 h-2 rounded-full bg-amber-500 animate-bounce" />
              )}
            </div>
            <span className="text-[10px] font-mono">Storage</span>
          </button>

          <button
            onClick={() => setActiveTab('log')}
            aria-label="CCTV Audit Log"
            className={`flex min-w-16 min-h-12 flex-col items-center justify-center gap-1 py-1 px-2 rounded-xl transition-all ${
              activeTab === 'log'
                ? 'text-emerald-400 font-bold'
                : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            <ListChecks className="w-5 h-5" />
            <span className="text-[10px] font-mono">Audit Log</span>
          </button>
        </nav>

      </div>
    </div>
  );
}
