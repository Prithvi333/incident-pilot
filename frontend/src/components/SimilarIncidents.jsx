/**
 * Similar incidents panel, in two independent parts:
 *
 *  1. "Similar incidents" — matches computed from the database (same service + overlapping words).
 *     This is core data: it loads on its own and never depends on the AI service.
 *  2. "AI insights" — optional LLM resolution suggestion and knowledge-base matches. It has its own
 *     loading and error state; if the AI service is slow or down, only this section degrades.
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getRelatedIncidents, getSimilarIncidents } from '../api/incidentApi';
import { Sparkles, WifiOff, Lightbulb, GitCompare } from 'lucide-react';
import clsx from 'clsx';
import StatusBadge from './StatusBadge';
import SeverityBadge from './SeverityBadge';

function ScoreChip({ score }) {
  return (
    <span className={clsx(
      'shrink-0 text-[11px] font-bold px-2 py-0.5 rounded-full',
      score >= 0.8
        ? 'text-emerald-400 bg-emerald-500/10'
        : score >= 0.6
          ? 'text-amber-400 bg-amber-500/10'
          : 'text-zinc-400 bg-zinc-700/50',
    )}>
      {(score * 100).toFixed(0)}% match
    </span>
  );
}

function RelatedIncidents({ incidentId }) {
  const [items, setItems] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setItems(null);
    setFailed(false);
    getRelatedIncidents(incidentId)
      .then((res) => { if (!cancelled) setItems(res.data); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [incidentId]);

  return (
    <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-4 sm:p-5">
      <div className="flex items-center gap-2 mb-4">
        <div className="p-1.5 rounded-lg bg-sky-500/10">
          <GitCompare size={16} className="text-sky-400" />
        </div>
        <h3 className="text-sm font-semibold text-zinc-200">Similar incidents</h3>
      </div>

      {items === null && !failed && (
        <div className="space-y-2">
          {[1, 2].map((i) => <div key={i} className="skeleton w-full h-14 rounded-lg" />)}
        </div>
      )}
      {failed && <p className="text-sm text-zinc-500">Could not load similar incidents.</p>}
      {items && items.length === 0 && (
        <p className="text-sm text-zinc-500">No similar incidents found yet.</p>
      )}
      {items && items.length > 0 && (
        <div className="space-y-2">
          {items.map((item) => (
            <Link
              key={item.id}
              to={`/incidents/${item.id}`}
              className="block p-3 rounded-lg bg-white/[0.02] border border-white/[0.04] hover:border-white/[0.1] transition-colors"
            >
              <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5 mb-1.5">
                <span className="text-xs text-zinc-600 font-mono">#{item.id}</span>
                <SeverityBadge severity={item.severity} />
                <StatusBadge status={item.status} />
                <span className="ml-auto"><ScoreChip score={item.score} /></span>
              </div>
              <p className="text-sm text-zinc-300 break-words">{item.title}</p>
              <p className="text-[11px] text-zinc-600 mt-0.5 truncate">{item.serviceName}</p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

function AiInsights({ incidentId }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setData(null);
    getSimilarIncidents(incidentId)
      .then((res) => { if (!cancelled) setData(res.data); })
      .catch(() => { if (!cancelled) setData(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [incidentId]);

  const header = (
    <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
      <div className="flex items-center gap-2">
        <div className="p-1.5 rounded-lg bg-violet-500/10">
          <Sparkles size={16} className="text-violet-400" />
        </div>
        <h3 className="text-sm font-semibold text-zinc-200">AI insights</h3>
      </div>
      {!loading && (!data || !data.aiAvailable) && (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium bg-zinc-700/50 text-zinc-400 ring-1 ring-white/[0.06]">
          <WifiOff size={12} />
          AI offline
        </span>
      )}
    </div>
  );

  if (loading) {
    return (
      <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-4 sm:p-5">
        {header}
        <div className="skeleton w-full h-16 rounded-lg" />
      </div>
    );
  }

  if (!data) {
    return (
      <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-4 sm:p-5">
        {header}
        <p className="text-sm text-zinc-500">AI insights are unavailable right now. Everything else on this page still works.</p>
      </div>
    );
  }

  return (
    <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-4 sm:p-5 animate-fade-in">
      {header}

      {data.resolutionSuggestion && (
        <div className="p-4 rounded-lg bg-indigo-500/[0.07] border border-indigo-500/20">
          <div className="flex items-center gap-2 mb-2">
            <Lightbulb size={14} className="text-indigo-400" />
            <span className="text-[11px] font-semibold uppercase tracking-wider text-indigo-400">
              Resolution suggestion
            </span>
          </div>
          <p className="text-sm text-zinc-300 leading-relaxed whitespace-pre-line break-words">
            {data.resolutionSuggestion}
          </p>
        </div>
      )}
    </div>
  );
}

export default function SimilarIncidents({ incidentId }) {
  return (
    <div className="space-y-6">
      <RelatedIncidents incidentId={incidentId} />
      <AiInsights incidentId={incidentId} />
    </div>
  );
}
