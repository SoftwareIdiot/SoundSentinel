import React, { useState, useRef, useEffect } from 'react';
import {
  Play,
  Pause,
  Download,
  Trash2,
  Lock,
  Unlock,
  Volume2,
  Clock,
  HardDrive,
  Search,
  Sparkles,
  FileAudio,
  ShieldAlert,
} from 'lucide-react';
import { AudioRecording } from '../types';
import { formatBytes, formatDuration } from '../utils/cctvLogic';

interface RecordingsVaultProps {
  recordings: AudioRecording[];
  onDeleteRecording: (id: string) => void;
  onToggleProtect: (id: string, isProtected: boolean) => void;
  onClearUnprotected: () => void;
  onReclassify?: (id: string) => void;
}

export const RecordingsVault: React.FC<RecordingsVaultProps> = ({
  recordings,
  onDeleteRecording,
  onToggleProtect,
  onClearUnprotected,
  onReclassify,
}) => {
  const [activeRecordingId, setActiveRecordingId] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(1);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterMode, setFilterMode] = useState<'all' | 'threats' | 'protected' | 'voice' | 'glass'>('all');
  const [reclassifyingId, setReclassifyingId] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Stop playback when active recording changes
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.pause();
      setIsPlaying(false);
      setCurrentTime(0);
    }
  }, [activeRecordingId]);

  const activeRecording = recordings.find((r) => r.id === activeRecordingId);

  const handleTogglePlay = (recording: AudioRecording) => {
    if (activeRecordingId === recording.id) {
      if (audioRef.current) {
        if (isPlaying) {
          audioRef.current.pause();
          setIsPlaying(false);
        } else {
          audioRef.current.play().catch(() => {});
          setIsPlaying(true);
        }
      }
    } else {
      setActiveRecordingId(recording.id);
      setIsPlaying(true);
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = Number(e.target.value);
    setCurrentTime(val);
    if (audioRef.current) {
      audioRef.current.currentTime = val;
    }
  };

  const handleSpeedChange = (speed: number) => {
    setPlaybackSpeed(speed);
    if (audioRef.current) {
      audioRef.current.playbackRate = speed;
    }
  };

  const handleDownload = (rec: AudioRecording) => {
    if (!rec.blob && !rec.audioUrl) return;
    const url = rec.audioUrl || (rec.blob ? URL.createObjectURL(rec.blob) : '');
    const a = document.createElement('a');
    a.href = url;
    a.download = `CCTV-${rec.classification?.tag ? rec.classification.tag.replace(/\s+/g, '_') : 'Audio'}-${rec.id}.webm`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const handleTriggerReclassify = async (id: string) => {
    if (!onReclassify) return;
    setReclassifyingId(id);
    await onReclassify(id);
    setReclassifyingId(null);
  };

  // Filter recordings
  const filteredRecordings = recordings.filter((r) => {
    const tag = r.classification?.tag?.toLowerCase() || '';
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const match =
        r.dateString.toLowerCase().includes(q) ||
        r.id.toLowerCase().includes(q) ||
        tag.includes(q) ||
        (r.classification?.explanation && r.classification.explanation.toLowerCase().includes(q));
      if (!match) return false;
    }

    if (filterMode === 'protected') return r.isProtected;
    if (filterMode === 'threats') {
      return (
        r.classification?.threatLevel === 'high' ||
        r.classification?.threatLevel === 'critical' ||
        r.peakDecibels >= 80
      );
    }
    if (filterMode === 'voice') return tag.includes('voice') || tag.includes('speech');
    if (filterMode === 'glass') return tag.includes('glass') || tag.includes('crash');
    return true;
  });

  return (
    <div className="flex flex-col gap-4 pb-12">
      {/* Header & Stats */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
            <FileAudio className="w-5 h-5 text-emerald-400" />
            CCTV Recordings Vault
          </h3>
          <p className="text-xs text-slate-400">
            {recordings.length} captured event{recordings.length !== 1 ? 's' : ''} stored locally in phone
          </p>
        </div>

        {recordings.some((r) => !r.isProtected) && (
          <button
            onClick={onClearUnprotected}
            className="text-[11px] font-mono text-red-400 hover:text-red-300 border border-red-900/60 bg-red-950/40 px-2.5 py-1 rounded-lg transition active:scale-95"
            title="Delete all unprotected recordings"
          >
            Clear Unprotected
          </button>
        )}
      </div>

      {/* Floating / Active Audio Player if a track is selected */}
      {activeRecording && (
        <div className="sticky top-2 z-30 bg-slate-900/95 backdrop-blur-xl border border-emerald-500/40 rounded-2xl p-3.5 shadow-2xl space-y-2.5">
          <audio
            ref={audioRef}
            src={activeRecording.audioUrl || (activeRecording.blob ? URL.createObjectURL(activeRecording.blob) : '')}
            onTimeUpdate={() => {
              if (audioRef.current) setCurrentTime(audioRef.current.currentTime);
            }}
            onEnded={() => setIsPlaying(false)}
            autoPlay
          />

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <button
                onClick={() => handleTogglePlay(activeRecording)}
                className="w-10 h-10 rounded-full bg-emerald-500 hover:bg-emerald-400 text-slate-950 flex items-center justify-center font-bold shadow-lg transition active:scale-95"
                aria-label={isPlaying ? 'Pause audio playback' : 'Play audio recording'}
              >
                {isPlaying ? <Pause className="w-5 h-5 fill-slate-950" /> : <Play className="w-5 h-5 fill-slate-950 ml-0.5" />}
              </button>
              <div>
                <div className="text-xs font-mono font-bold text-slate-100 flex items-center gap-2">
                  <span>{activeRecording.dateString}</span>
                  {activeRecording.isProtected && (
                    <span className="text-[10px] text-amber-400 flex items-center gap-0.5">
                      <Lock className="w-3 h-3" /> Locked
                    </span>
                  )}
                </div>
                <div className="text-[10px] text-slate-400 font-mono">
                  Peak level {activeRecording.peakDecibels}/100 • {formatBytes(activeRecording.sizeBytes)} • Re-triggered {activeRecording.triggersCount}x
                </div>
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              {onReclassify && (
                <button
                  onClick={() => handleTriggerReclassify(activeRecording.id)}
                  disabled={reclassifyingId === activeRecording.id}
                  className="flex items-center gap-1 text-[10px] font-mono px-2 py-1 rounded-lg bg-emerald-950/90 border border-emerald-500/40 text-emerald-300 hover:bg-emerald-900 transition active:scale-95 disabled:opacity-50"
                  title="Re-analyze sound event with AI"
                >
                  <Sparkles className="w-3 h-3 text-emerald-400" />
                  <span>{reclassifyingId === activeRecording.id ? 'Analyzing...' : 'AI Classify'}</span>
                </button>
              )}

              {/* Playback speed buttons */}
              {[1, 1.5, 2].map((spd) => (
                <button
                  key={spd}
                  onClick={() => handleSpeedChange(spd)}
                  className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${playbackSpeed === spd ? 'bg-emerald-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-400'}`}
                >
                  {spd}x
                </button>
              ))}
              <button
                onClick={() => handleDownload(activeRecording)}
                className="p-1.5 rounded-lg text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 ml-1"
                title="Download Audio"
                aria-label="Download recorded audio"
              >
                <Download className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* AI Classification Tag Banner on active player */}
          {activeRecording.classification && (
            <div className="p-2 rounded-xl bg-slate-950/80 border border-slate-800 flex items-center justify-between text-xs font-mono">
              <div className="flex items-center gap-1.5">
                <span
                  className={`px-2 py-0.5 rounded-md font-bold text-[10px] border ${
                    activeRecording.classification.threatLevel === 'high' || activeRecording.classification.threatLevel === 'critical'
                      ? 'bg-red-500/20 text-red-300 border-red-500/40'
                      : activeRecording.classification.threatLevel === 'medium'
                      ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                      : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                  }`}
                >
                  {activeRecording.classification.tag}
                </span>
                <span className="text-[10px] text-slate-400 font-sans">
                  {activeRecording.classification.explanation}
                </span>
              </div>
              <div className="shrink-0 text-right text-[10px] text-slate-400 font-mono">
                <div>{Math.round(activeRecording.classification.confidence * 100)}% conf</div>
                <div className={activeRecording.classification.engine === 'gemini' ? 'text-emerald-400' : 'text-amber-400'}>
                  {activeRecording.classification.engine === 'gemini' ? 'Gemini AI' : 'Local fallback'}
                </div>
              </div>
            </div>
          )}
          {activeRecording.classification?.providerMessage && activeRecording.classification.engine !== 'gemini' && (
            <p className="rounded-lg border border-amber-900/50 bg-amber-950/30 px-2 py-1.5 text-[10px] leading-relaxed text-amber-200/90">
              {activeRecording.classification.providerMessage}
            </p>
          )}

          {/* Scrubber slider and time display */}
          <div className="space-y-1">
            <input
              type="range"
              min="0"
              max={activeRecording.duration || 1}
              step="0.1"
              value={currentTime}
              onChange={handleSeek}
              className="w-full accent-emerald-500 cursor-pointer h-1.5 bg-slate-800 rounded-lg appearance-none"
              aria-label="Audio playback seek scrubber"
            />
            <div className="flex justify-between text-[10px] font-mono text-slate-400">
              <span>{formatDuration(currentTime)}</span>
              <span>{formatDuration(activeRecording.duration)}</span>
            </div>
          </div>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="flex flex-col gap-2">
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search recordings by date, sound tag or note..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-2 bg-slate-900 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-emerald-500 font-mono"
          />
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs font-mono">
          <button
            onClick={() => setFilterMode('all')}
            className={`px-3 py-1 rounded-lg border transition ${filterMode === 'all' ? 'bg-slate-800 border-slate-600 text-emerald-400 font-bold' : 'border-slate-800 text-slate-400'}`}
          >
            All ({recordings.length})
          </button>
          <button
            onClick={() => setFilterMode('threats')}
            className={`px-3 py-1 rounded-lg border transition flex items-center gap-1 ${filterMode === 'threats' ? 'bg-slate-800 border-slate-600 text-red-400 font-bold' : 'border-slate-800 text-slate-400'}`}
          >
            <ShieldAlert className="w-3 h-3 text-red-400" />
            Threats ({recordings.filter((r) => r.classification?.threatLevel === 'high' || r.peakDecibels >= 80).length})
          </button>
          <button
            onClick={() => setFilterMode('protected')}
            className={`px-3 py-1 rounded-lg border transition flex items-center gap-1 ${filterMode === 'protected' ? 'bg-slate-800 border-slate-600 text-amber-400 font-bold' : 'border-slate-800 text-slate-400'}`}
          >
            <Lock className="w-3 h-3" />
            Locked ({recordings.filter((r) => r.isProtected).length})
          </button>
          <button
            onClick={() => setFilterMode('voice')}
            className={`px-3 py-1 rounded-lg border transition ${filterMode === 'voice' ? 'bg-slate-800 border-slate-600 text-blue-400 font-bold' : 'border-slate-800 text-slate-400'}`}
          >
            Voice
          </button>
          <button
            onClick={() => setFilterMode('glass')}
            className={`px-3 py-1 rounded-lg border transition ${filterMode === 'glass' ? 'bg-slate-800 border-slate-600 text-red-300 font-bold' : 'border-slate-800 text-slate-400'}`}
          >
            Glass/Crash
          </button>
        </div>
      </div>

      {/* Recordings List */}
      {filteredRecordings.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-8 rounded-2xl bg-slate-900/40 border border-slate-800 text-center">
          <Volume2 className="w-10 h-10 text-slate-600 mb-2" />
          <p className="text-xs font-semibold text-slate-300">No recordings in vault</p>
          <p className="text-[11px] text-slate-500 mt-1 max-w-xs leading-relaxed">
            Arm the monitor and a real sound event will be recorded here. No sample events are shown.
          </p>
        </div>
      ) : (
        <div className="space-y-2.5">
          {filteredRecordings.map((rec) => {
            const isThisPlaying = activeRecordingId === rec.id && isPlaying;
            return (
              <div
                key={rec.id}
                className={`p-3 rounded-2xl border transition-all ${
                  activeRecordingId === rec.id
                    ? 'bg-slate-900 border-emerald-500/60 shadow-lg'
                    : 'bg-slate-900/60 border-slate-800/80 hover:border-slate-700'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => handleTogglePlay(rec)}
                      className={`w-9 h-9 rounded-xl flex items-center justify-center transition active:scale-95 ${
                        isThisPlaying
                          ? 'bg-red-500 text-white'
                          : 'bg-emerald-500/20 text-emerald-400 hover:bg-emerald-500 hover:text-slate-950'
                      }`}
                      aria-label={`Play recording from ${rec.dateString}`}
                    >
                      {isThisPlaying ? <Pause className="w-4 h-4 fill-white" /> : <Play className="w-4 h-4 fill-current ml-0.5" />}
                    </button>

                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-mono font-bold text-slate-200">
                          {rec.dateString}
                        </span>
                        {rec.isProtected && (
                          <span className="text-[10px] text-amber-400 bg-amber-500/10 px-1.5 py-0.2 rounded font-mono font-semibold flex items-center gap-0.5">
                            <Lock className="w-2.5 h-2.5" /> Locked
                          </span>
                        )}
                      </div>

                      {/* AI Classification Tag */}
                      {rec.classification && (
                        <div className="flex items-center gap-1.5 mt-0.5">
                          <span
                            className={`text-[9px] px-1.5 py-0.2 rounded font-mono font-bold border ${
                              rec.classification.threatLevel === 'high' || rec.classification.threatLevel === 'critical'
                                ? 'bg-red-500/20 text-red-300 border-red-500/40'
                                : rec.classification.threatLevel === 'medium'
                                ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                                : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                            }`}
                          >
                            {rec.classification.tag}
                          </span>
                          <span className="text-[9px] text-slate-400 font-mono">
                            {Math.round(rec.classification.confidence * 100)}% conf
                          </span>
                          <span className={`text-[9px] font-mono ${rec.classification.engine === 'gemini' ? 'text-emerald-400' : 'text-amber-400'}`}>
                            {rec.classification.engine === 'gemini' ? 'Gemini' : 'Local'}
                          </span>
                        </div>
                      )}

                      <div className="flex items-center gap-3 text-[10px] font-mono text-slate-400 mt-0.5">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3 text-slate-500" />
                          {formatDuration(rec.duration)}
                        </span>
                        <span className="flex items-center gap-1">
                          <Volume2 className="w-3 h-3 text-emerald-500" />
                          {rec.peakDecibels}/100
                        </span>
                        <span className="flex items-center gap-1">
                          <HardDrive className="w-3 h-3 text-slate-500" />
                          {formatBytes(rec.sizeBytes)}
                        </span>
                        {rec.triggersCount > 1 && (
                          <span className="text-amber-400">+{rec.triggersCount - 1}x re-trigger</span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Actions: Re-classify, Lock, Download, Delete */}
                  <div className="flex items-center gap-1">
                    {onReclassify && !rec.classification && (
                      <button
                        onClick={() => handleTriggerReclassify(rec.id)}
                        disabled={reclassifyingId === rec.id}
                        className="p-1.5 rounded-lg text-emerald-400 hover:bg-emerald-950/60 transition"
                        title="Analyze with AI"
                        aria-label="Classify recording"
                      >
                        <Sparkles className="w-3.5 h-3.5" />
                      </button>
                    )}

                    <button
                      onClick={() => onToggleProtect(rec.id, !rec.isProtected)}
                      className={`p-1.5 rounded-lg text-xs transition ${
                        rec.isProtected
                          ? 'text-amber-400 bg-amber-500/15 hover:bg-amber-500/25'
                          : 'text-slate-500 hover:text-slate-300 hover:bg-slate-800'
                      }`}
                      title={rec.isProtected ? 'Protected from auto-overwrite (Click to unlock)' : 'Lock / Protect from circular auto-overwrite'}
                      aria-label={rec.isProtected ? 'Unlock recording' : 'Lock recording'}
                    >
                      {rec.isProtected ? <Lock className="w-3.5 h-3.5" /> : <Unlock className="w-3.5 h-3.5" />}
                    </button>

                    <button
                      onClick={() => handleDownload(rec)}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition"
                      title="Download audio recording"
                      aria-label="Download audio recording"
                    >
                      <Download className="w-3.5 h-3.5" />
                    </button>

                    <button
                      onClick={() => onDeleteRecording(rec.id)}
                      className="p-1.5 rounded-lg text-slate-500 hover:text-red-400 hover:bg-red-950/40 transition"
                      title="Delete recording"
                      aria-label="Delete recording"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Mini audio amplitude bar preview */}
                {rec.waveformData && rec.waveformData.length > 0 && (
                  <div className="mt-2 flex items-end gap-0.5 h-4 px-1 pt-1 opacity-60 overflow-hidden">
                    {rec.waveformData.slice(0, 32).map((val, idx) => (
                      <div
                        key={idx}
                        className={`w-1 rounded-t transition-all ${val > 60 ? 'bg-red-400' : 'bg-emerald-500'}`}
                        style={{ height: `${Math.max(15, (val / 100) * 100)}%` }}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
