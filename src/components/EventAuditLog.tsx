import React, { useState } from 'react';
import {
  ListChecks,
  AlertCircle,
  Radio,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
  Info,
  Sparkles,
  ShieldAlert,
} from 'lucide-react';
import { CCTVEvent } from '../types';

interface EventAuditLogProps {
  events: CCTVEvent[];
}

export const EventAuditLog: React.FC<EventAuditLogProps> = ({ events }) => {
  const [filter, setFilter] = useState<string>('all');

  const filteredEvents = events.filter((e) => {
    if (filter === 'all') return true;
    if (filter === 'classified') return e.type === 'classified' || Boolean(e.tag);
    if (filter === 'threats') {
      return e.threatLevel === 'high' || e.threatLevel === 'critical' || (e.decibels && e.decibels >= 80);
    }
    if (filter === 'glass') return e.tag?.toLowerCase().includes('glass') || e.message.toLowerCase().includes('glass');
    if (filter === 'footsteps') return e.tag?.toLowerCase().includes('footstep') || e.message.toLowerCase().includes('footstep');
    if (filter === 'voice') return e.tag?.toLowerCase().includes('voice') || e.tag?.toLowerCase().includes('speech');
    return e.type === filter;
  });

  const getBadge = (evt: CCTVEvent) => {
    if (evt.type === 'classified' || evt.tag) {
      if (evt.threatLevel === 'high' || evt.threatLevel === 'critical') {
        return {
          icon: <ShieldAlert className="w-3.5 h-3.5 text-red-400" />,
          label: evt.tag ? `🚨 ${evt.tag.toUpperCase()}` : 'THREAT',
          bg: 'bg-red-500/20 border-red-500/40 text-red-300 font-black',
        };
      }
      if (evt.threatLevel === 'medium') {
        return {
          icon: <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />,
          label: evt.tag ? `⚠️ ${evt.tag.toUpperCase()}` : 'MEDIUM ALERT',
          bg: 'bg-amber-500/20 border-amber-500/40 text-amber-300 font-bold',
        };
      }
      return {
        icon: <Sparkles className="w-3.5 h-3.5 text-blue-400" />,
        label: evt.tag ? `🗣️ ${evt.tag.toUpperCase()}` : 'AI CLASSIFIED',
        bg: 'bg-blue-500/20 border-blue-500/40 text-blue-300 font-bold',
      };
    }

    switch (evt.type) {
      case 'detection':
        return {
          icon: <AlertCircle className="w-3.5 h-3.5 text-red-400" />,
          label: 'TRIGGER',
          bg: 'bg-red-500/10 border-red-500/30 text-red-300 font-semibold',
        };
      case 'extension':
        return {
          icon: <Radio className="w-3.5 h-3.5 text-amber-400" />,
          label: 'EXTENDED (+10s)',
          bg: 'bg-amber-500/10 border-amber-500/30 text-amber-300 font-semibold',
        };
      case 'saved':
        return {
          icon: <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />,
          label: 'SAVED',
          bg: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300 font-semibold',
        };
      case 'warning':
        return {
          icon: <AlertTriangle className="w-3.5 h-3.5 text-yellow-400" />,
          label: 'STORAGE WARN',
          bg: 'bg-yellow-500/10 border-yellow-500/30 text-yellow-300 font-semibold',
        };
      case 'pruned':
        return {
          icon: <RotateCcw className="w-3.5 h-3.5 text-purple-400" />,
          label: 'OVERWRITE',
          bg: 'bg-purple-500/10 border-purple-500/30 text-purple-300 font-semibold',
        };
      case 'system':
      default:
        return {
          icon: <Info className="w-3.5 h-3.5 text-blue-400" />,
          label: 'SYSTEM',
          bg: 'bg-blue-500/10 border-blue-500/30 text-blue-300 font-semibold',
        };
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-12">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
            <ListChecks className="w-5 h-5 text-emerald-400" />
            CCTV Sentinel Audit Trail
          </h3>
          <p className="text-xs text-slate-400">
            AI-classified audio detections, 10s extensions & overwriting events
          </p>
        </div>
      </div>

      {/* Filter Chips */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs font-mono">
        {[
          { key: 'all', label: `All (${events.length})` },
          { key: 'classified', label: `AI Classified (${events.filter((e) => e.type === 'classified' || e.tag).length})` },
          { key: 'threats', label: `Threats` },
          { key: 'glass', label: `Glass Break` },
          { key: 'footsteps', label: `Footsteps` },
          { key: 'voice', label: `Voice` },
          { key: 'pruned', label: `Overwrites` },
        ].map((item) => (
          <button
            key={item.key}
            onClick={() => setFilter(item.key)}
            className={`px-2.5 py-1 rounded-lg border whitespace-nowrap transition ${
              filter === item.key
                ? 'bg-slate-800 border-slate-600 text-emerald-400 font-bold'
                : 'border-slate-800 text-slate-400 hover:border-slate-700'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {/* Events List */}
      {filteredEvents.length === 0 ? (
        <div className="p-8 rounded-2xl bg-slate-900/40 border border-slate-800 text-center text-xs text-slate-500 font-mono">
          No matching CCTV audit events recorded.
        </div>
      ) : (
        <div className="space-y-2">
          {filteredEvents.map((evt) => {
            const badge = getBadge(evt);
            const timeStr = new Date(evt.timestamp).toLocaleTimeString();
            const dateStr = new Date(evt.timestamp).toLocaleDateString();

            return (
              <div
                key={evt.id}
                className={`p-3 rounded-xl border flex items-start justify-between gap-3 font-mono text-xs transition-all ${
                  evt.threatLevel === 'high' || evt.threatLevel === 'critical'
                    ? 'bg-red-950/30 border-red-900/50 shadow-md shadow-red-950/20'
                    : evt.type === 'classified'
                    ? 'bg-slate-900/90 border-slate-800/90'
                    : 'bg-slate-900/60 border-slate-800/70'
                }`}
              >
                <div className="flex items-start gap-2.5">
                  <div className="mt-0.5">{badge.icon}</div>
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-[10px] px-2 py-0.5 rounded font-bold border ${badge.bg}`}>
                        {badge.label}
                      </span>
                      {evt.decibels !== undefined && (
                        <span className="text-[10px] text-emerald-400 font-bold">
                          {evt.decibels} dB
                        </span>
                      )}
                      {evt.confidence !== undefined && (
                        <span className="text-[9px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 font-mono">
                          {Math.round(evt.confidence * 100)}% conf
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-300 font-sans mt-1 leading-relaxed">
                      {evt.message}
                    </p>
                  </div>
                </div>

                <div className="text-right text-[10px] text-slate-500 shrink-0 font-mono">
                  <div>{timeStr}</div>
                  <div className="text-[9px]">{dateStr}</div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
