/**
 * AI Insights panel — fetches similar incidents and resolution suggestions from the RAG API.
 *
 * Handles three states: loading skeleton, error (AI offline), and results with
 * optional AI-generated resolution suggestion + vector similarity matches.
 * Score thresholds (80%+ green, 60%+ amber) help users quickly gauge relevance.
 */
import { useEffect, useState } from 'react';
import { getSimilarIncidents } from '../api/incidentApi';
import { Sparkles, WifiOff, Lightbulb, FileText } from 'lucide-react';
import clsx from 'clsx';

export default function SimilarIncidents({ incidentId }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchSimilar() {
      try {
        const res = await getSimilarIncidents(incidentId);
        setData(res.data);
      } catch {
        setData(null);
      } finally {
        setLoading(false);
      }
    }
    fetchSimilar();
  }, [incidentId]);

  if (loading) {
    return (
      <div className="bg-zinc-900 rounded-xl border border-white/[0.06] p-5">
        <div className="flex items-center gap-2 mb-4">
          <div className="skeleton w-5 h-5 rounded" />
          <div className="skeleton w-48 h-4 rounded" />
        </div>
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="skeleton w-full h-16 rounded-lg" />
          ))}
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="bg-zinc-900 rounded-xl border border-white/[0.06] p-5">
        <div className="flex items-center gap-2 text-zinc-500">
          <WifiOff size={16} />
          <span className="text-sm">Could not load AI suggestions.</span>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-zinc-900 rounded-xl border border-white/[0.06] p-5 animate-fade-in">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-violet-500/10">
            <Sparkles size={16} className="text-violet-400" />
          </div>
          <h3 className="text-sm font-semibold text-zinc-200">AI Insights</h3>
        </div>
        {!data.aiAvailable && (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium bg-zinc-800 text-zinc-400 ring-1 ring-white/[0.06]">
            <WifiOff size={12} />
            AI Offline
          </span>
        )}
      </div>

      {data.resolutionSuggestion && (
        <div className="mb-4 p-4 rounded-lg bg-indigo-500/[0.07] border border-indigo-500/20">
          <div className="flex items-center gap-2 mb-2">
            <Lightbulb size={14} className="text-indigo-400" />
            <span className="text-[11px] font-semibold uppercase tracking-wider text-indigo-400">
              Resolution Suggestion
            </span>
          </div>
          <p className="text-sm text-zinc-300 leading-relaxed whitespace-pre-line">
            {data.resolutionSuggestion}
          </p>
        </div>
      )}

      {data.similarMatches.length === 0 ? (
        <p className="text-sm text-zinc-500">No similar incidents found yet.</p>
      ) : (
        <div className="space-y-2">
          {data.similarMatches.map((match, idx) => (
            <div
              key={idx}
              className="p-3 rounded-lg bg-white/[0.02] border border-white/[0.04] hover:border-white/[0.08] transition-colors"
            >
              <div className="flex items-center justify-between mb-1.5">
                <span className="flex items-center gap-1.5 text-[11px] font-medium text-zinc-400">
                  <FileText size={12} />
                  {match.source}
                </span>
                <span className={clsx(
                  'text-[11px] font-bold px-2 py-0.5 rounded-full',
                  match.score >= 0.8
                    ? 'text-emerald-400 bg-emerald-500/10'
                    : match.score >= 0.6
                      ? 'text-amber-400 bg-amber-500/10'
                      : 'text-zinc-400 bg-zinc-800'
                )}>
                  {(match.score * 100).toFixed(0)}% match
                </span>
              </div>
              <p className="text-sm text-zinc-400 line-clamp-2 leading-relaxed">{match.content}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
