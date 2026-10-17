import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  getIncident,
  updateIncidentStatus,
  getTimeline,
  addTimelineEvent,
} from '../api/incidentApi';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';

/**
 * Maps each status to its single valid next status.
 * Mirrors the backend state machine so the UI only shows valid actions.
 */
const NEXT_STATUS = {
  TRIGGERED: 'ACKNOWLEDGED',
  ACKNOWLEDGED: 'INVESTIGATING',
  INVESTIGATING: 'RESOLVED',
  RESOLVED: 'POSTMORTEM',
};

const ACTION_LABELS = {
  ACKNOWLEDGED: 'Acknowledge',
  INVESTIGATING: 'Start Investigation',
  RESOLVED: 'Resolve',
  POSTMORTEM: 'Begin Postmortem',
};

export default function IncidentDetail() {
  const { id } = useParams();
  const [incident, setIncident] = useState(null);
  const [timeline, setTimeline] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [transitioning, setTransitioning] = useState(false);

  // Timeline form state
  const [comment, setComment] = useState('');
  const [author, setAuthor] = useState('');
  const [eventType, setEventType] = useState('COMMENT');
  const [submitting, setSubmitting] = useState(false);

  async function fetchData() {
    try {
      const [incRes, tlRes] = await Promise.all([
        getIncident(id),
        getTimeline(id),
      ]);
      setIncident(incRes.data);
      setTimeline(tlRes.data);
    } catch {
      setError('Failed to load incident.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchData();
  }, [id]);

  async function handleTransition() {
    const nextStatus = NEXT_STATUS[incident.status];
    if (!nextStatus) return;
    setTransitioning(true);
    try {
      await updateIncidentStatus(id, nextStatus);
      await fetchData(); // Refresh incident + timeline (auto-created event)
    } catch {
      setError('Failed to update status.');
    } finally {
      setTransitioning(false);
    }
  }

  async function handleAddEvent(e) {
    e.preventDefault();
    if (!comment.trim() || !author.trim()) return;
    setSubmitting(true);
    try {
      await addTimelineEvent(id, {
        type: eventType,
        message: comment,
        author,
      });
      setComment('');
      const tlRes = await getTimeline(id);
      setTimeline(tlRes.data);
    } catch {
      setError('Failed to add timeline event.');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <p className="text-center mt-10 text-gray-500">Loading...</p>;
  if (error && !incident) return <p className="text-center mt-10 text-red-500">{error}</p>;

  const nextStatus = NEXT_STATUS[incident.status];

  return (
    <div>
      <Link to="/incidents" className="text-sm text-blue-600 hover:underline">
        ← Back to incidents
      </Link>

      {/* Header */}
      <div className="mt-4 mb-6">
        <div className="flex items-center gap-3 mb-2">
          <SeverityBadge severity={incident.severity} />
          <StatusBadge status={incident.status} />
          <span className="text-gray-400 text-sm">#{incident.id}</span>
        </div>
        <h1 className="text-2xl font-bold">{incident.title}</h1>
        {incident.description && (
          <p className="text-gray-600 mt-2">{incident.description}</p>
        )}
      </div>

      {/* Info grid + transition button */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        <div className="bg-white rounded-lg shadow p-5">
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Details</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Service</dt>
              <dd className="font-medium">{incident.serviceName}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Assignee</dt>
              <dd className="font-medium">{incident.assignee || '—'}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Created</dt>
              <dd>{new Date(incident.createdAt).toLocaleString()}</dd>
            </div>
            {incident.resolvedAt && (
              <div className="flex justify-between">
                <dt className="text-gray-500">Resolved</dt>
                <dd>{new Date(incident.resolvedAt).toLocaleString()}</dd>
              </div>
            )}
          </dl>
        </div>

        {nextStatus && (
          <div className="bg-white rounded-lg shadow p-5 flex flex-col justify-center items-center">
            <p className="text-sm text-gray-500 mb-3">Next action:</p>
            <button
              onClick={handleTransition}
              disabled={transitioning}
              className="bg-blue-600 text-white px-6 py-2.5 rounded font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {transitioning ? 'Updating...' : ACTION_LABELS[nextStatus]}
            </button>
          </div>
        )}
      </div>

      {/* Timeline */}
      <h2 className="text-lg font-semibold mb-3">Timeline</h2>

      {/* Add event form */}
      <form onSubmit={handleAddEvent} className="bg-white rounded-lg shadow p-4 mb-4">
        <div className="flex gap-3 mb-3">
          <select
            value={eventType}
            onChange={(e) => setEventType(e.target.value)}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm bg-white"
          >
            <option value="COMMENT">Comment</option>
            <option value="ACTION">Action</option>
          </select>
          <input
            type="text"
            placeholder="Your name"
            value={author}
            onChange={(e) => setAuthor(e.target.value)}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm flex-shrink-0 w-40"
          />
        </div>
        <div className="flex gap-3">
          <input
            type="text"
            placeholder="What happened? What did you do?"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm flex-1"
          />
          <button
            type="submit"
            disabled={submitting || !comment.trim() || !author.trim()}
            className="bg-gray-800 text-white px-4 py-1.5 rounded text-sm font-medium hover:bg-gray-900 disabled:opacity-50 transition-colors"
          >
            {submitting ? 'Adding...' : 'Add'}
          </button>
        </div>
      </form>

      {/* Timeline entries */}
      {timeline.length === 0 ? (
        <p className="text-gray-400 text-sm">No timeline events yet.</p>
      ) : (
        <div className="space-y-3">
          {timeline.map((event) => (
            <div
              key={event.id}
              className={`bg-white rounded-lg shadow p-4 border-l-4 ${
                event.type === 'STATUS_CHANGE'
                  ? 'border-blue-400'
                  : event.type === 'ACTION'
                    ? 'border-green-400'
                    : 'border-gray-300'
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-medium text-gray-500 uppercase">{event.type}</span>
                <span className="text-xs text-gray-400">
                  {new Date(event.createdAt).toLocaleString()}
                </span>
              </div>
              <p className="text-sm">{event.message}</p>
              <p className="text-xs text-gray-400 mt-1">by {event.author}</p>
            </div>
          ))}
        </div>
      )}

      {error && <p className="text-red-500 text-sm mt-4">{error}</p>}
    </div>
  );
}
