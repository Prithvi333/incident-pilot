import { useEffect, useState } from 'react';
import { getSimilarIncidents } from '../api/incidentApi';

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

  if (loading) return <p className="text-sm text-gray-400">Searching for similar incidents...</p>;
  if (!data) return <p className="text-sm text-gray-400">Could not load AI suggestions.</p>;

  return (
    <div className="bg-white rounded-lg shadow p-5">
      <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3 flex items-center gap-2">
        AI-Powered Similar Incidents
        {!data.aiAvailable && (
          <span className="text-xs text-amber-600 bg-amber-50 px-2 py-0.5 rounded">AI Offline</span>
        )}
      </h2>

      {data.resolutionSuggestion && (
        <div className="bg-blue-50 border border-blue-200 rounded p-3 mb-4">
          <p className="text-xs font-semibold text-blue-700 uppercase mb-1">Resolution Suggestion</p>
          <p className="text-sm text-blue-900 whitespace-pre-line">{data.resolutionSuggestion}</p>
        </div>
      )}

      {data.similarMatches.length === 0 ? (
        <p className="text-sm text-gray-400">No similar incidents found yet.</p>
      ) : (
        <div className="space-y-2">
          {data.similarMatches.map((match, idx) => (
            <div key={idx} className="border border-gray-200 rounded p-3">
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-medium text-gray-500">{match.source}</span>
                <span className="text-xs text-gray-400">{(match.score * 100).toFixed(0)}% match</span>
              </div>
              <p className="text-sm text-gray-700 line-clamp-3">{match.content}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
