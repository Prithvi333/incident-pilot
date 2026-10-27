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
import SimilarIncidents from '../components/SimilarIncidents';
import {
  ArrowLeft,
  Server,
  User,
  Calendar,
  CheckCircle2,
  ArrowRight,
  MessageSquare,
  Zap,
  GitCommit,
  Send,
  Loader2,
  AlertTriangle,
  Clock,
} from 'lucide-react';
import clsx from 'clsx';

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

const ACTION_COLORS = {
  ACKNOWLEDGED: 'from-amber-600 to-amber-500 shadow-amber-500/20',
  INVESTIGATING: 'from-blue-600 to-blue-500 shadow-blue-500/20',
  RESOLVED: 'from-emerald-600 to-emerald-500 shadow-emerald-500/20',
  POSTMORTEM: 'from-zinc-600 to-zinc-500 shadow-zinc-500/20',
};

const EVENT_ICONS = {
  STATUS_CHANGE: GitCommit,
  ACTION: Zap,
  COMMENT: MessageSquare,
};

const EVENT_COLORS = {
  STATUS_CHANGE: 'text-blue-400 bg-blue-500/10',
  ACTION: 'text-emerald-400 bg-emerald-500/10',
  COMMENT: 'text-zinc-400 bg-zinc-500/10',
};

function DetailSkeleton() {
  return (
    <div className="animate-fade-in">
      <div className="skeleton w-32 h-4 rounded mb-8" />
      <div className="skeleton w-3/4 h-8 rounded-lg mb-4" />
      <div className="flex gap-3 mb-8">
        <div className="skeleton w-16 h-6 rounded-md" />
        <div className="skeleton w-24 h-6 rounded-full" />
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          <div className="skeleton h-48 rounded-xl" />
          <div className="skeleton h-64 rounded-xl" />
        </div>
        <div className="skeleton h-48 rounded-xl" />
      </div>
    </div>
  );
}

function formatDate(dateStr) {
  if (!dateStr) return null;
  return new Date(dateStr).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function IncidentDetail() {
  const { id } = useParams();
  const [incident, setIncident] = useState(null);
  const [timeline, setTimeline] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [transitioning, setTransitioning] = useState(false);

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
      await fetchData();
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

  if (loading) return <DetailSkeleton />;
  if (error && !incident) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh] animate-fade-in">
        <div className="p-4 rounded-full bg-rose-500/10 mb-4">
          <AlertTriangle size={32} className="text-rose-400" />
        </div>
        <p className="text-zinc-400">{error}</p>
        <Link to="/incidents" className="mt-4 text-sm text-indigo-400 hover:text-indigo-300 transition-colors">
          Back to incidents
        </Link>
      </div>
    );
  }

  const nextStatus = NEXT_STATUS[incident.status];

  return (
    <div className="animate-fade-in">
      {/* Back link */}
      <Link
        to="/incidents"
        className="inline-flex items-center gap-1.5 text-sm text-zinc-500 hover:text-zinc-300 transition-colors mb-6"
      >
        <ArrowLeft size={14} />
        Back to incidents
      </Link>

      {/* Header */}
      <div className="mb-8">
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <SeverityBadge severity={incident.severity} showLabel />
          <StatusBadge status={incident.status} />
          <span className="text-zinc-600 text-sm font-mono">INC-{incident.id}</span>
        </div>
        <h1 className="text-2xl font-bold text-zinc-50 tracking-tight mb-2">
          {incident.title}
        </h1>
        {incident.description && (
          <p className="text-sm text-zinc-400 leading-relaxed max-w-3xl">
            {incident.description}
          </p>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left column - Main content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Transition action */}
          {nextStatus && (
            <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-5">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-500 mb-1">Next Step</p>
                  <p className="text-sm text-zinc-300">
                    Transition to <span className="font-semibold text-zinc-100">{nextStatus}</span>
                  </p>
                </div>
                <button
                  onClick={handleTransition}
                  disabled={transitioning}
                  className={clsx(
                    'inline-flex items-center gap-2 text-white px-5 py-2.5 rounded-lg text-sm font-semibold transition-all duration-200 bg-gradient-to-r shadow-lg disabled:opacity-40',
                    ACTION_COLORS[nextStatus]
                  )}
                >
                  {transitioning ? (
                    <Loader2 size={15} className="animate-spin" />
                  ) : (
                    <ArrowRight size={15} />
                  )}
                  {transitioning ? 'Updating...' : ACTION_LABELS[nextStatus]}
                </button>
              </div>
            </div>
          )}

          {/* AI Similar Incidents */}
          <SimilarIncidents incidentId={id} />

          {/* Timeline */}
          <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] overflow-hidden">
            <div className="px-5 py-4 border-b border-white/[0.06]">
              <h3 className="text-sm font-semibold text-zinc-300 flex items-center gap-2">
                <Clock size={15} className="text-zinc-500" />
                Activity Timeline
                {timeline.length > 0 && (
                  <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-zinc-700/50 text-zinc-400">
                    {timeline.length}
                  </span>
                )}
              </h3>
            </div>

            {/* Add event form */}
            <form onSubmit={handleAddEvent} className="px-5 py-4 border-b border-white/[0.06]">
              <div className="flex gap-2 mb-3">
                <select
                  value={eventType}
                  onChange={(e) => setEventType(e.target.value)}
                  className="appearance-none bg-zinc-700/50 border border-white/[0.08] rounded-lg px-3 py-2 text-xs font-medium text-zinc-300 focus-ring cursor-pointer"
                >
                  <option value="COMMENT">Comment</option>
                  <option value="ACTION">Action</option>
                </select>
                <input
                  type="text"
                  placeholder="Your name"
                  value={author}
                  onChange={(e) => setAuthor(e.target.value)}
                  className="bg-zinc-700/50 border border-white/[0.08] rounded-lg px-3 py-2 text-sm text-zinc-100 placeholder-zinc-600 focus-ring w-36"
                />
              </div>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="What happened? What did you do?"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  className="flex-1 bg-zinc-700/50 border border-white/[0.08] rounded-lg px-3 py-2 text-sm text-zinc-100 placeholder-zinc-600 focus-ring"
                />
                <button
                  type="submit"
                  disabled={submitting || !comment.trim() || !author.trim()}
                  className="inline-flex items-center gap-1.5 bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-indigo-500 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  {submitting ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
                  Add
                </button>
              </div>
            </form>

            {/* Timeline entries */}
            {timeline.length === 0 ? (
              <div className="px-5 py-10 text-center">
                <p className="text-sm text-zinc-500">No activity yet</p>
              </div>
            ) : (
              <div className="divide-y divide-white/[0.04]">
                {timeline.map((event) => {
                  const EventIcon = EVENT_ICONS[event.type] || MessageSquare;
                  const colorClass = EVENT_COLORS[event.type] || EVENT_COLORS.COMMENT;
                  return (
                    <div key={event.id} className="px-5 py-4 hover:bg-white/[0.01] transition-colors">
                      <div className="flex gap-3">
                        <div className={clsx('w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-0.5', colorClass)}>
                          <EventIcon size={14} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between mb-1">
                            <div className="flex items-center gap-2">
                              <span className="text-[11px] font-semibold uppercase tracking-wider text-zinc-500">
                                {event.type.replace('_', ' ')}
                              </span>
                              <span className="text-[11px] text-zinc-600">by {event.author}</span>
                            </div>
                            <span className="text-[11px] text-zinc-600">
                              {formatDate(event.createdAt)}
                            </span>
                          </div>
                          <p className="text-sm text-zinc-300 leading-relaxed">{event.message}</p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Right column - Details sidebar */}
        <div className="space-y-6">
          <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-5">
            <h3 className="text-[11px] font-semibold uppercase tracking-wider text-zinc-500 mb-4">
              Incident Details
            </h3>
            <dl className="space-y-4">
              <div>
                <dt className="flex items-center gap-1.5 text-[11px] font-medium text-zinc-500 mb-1">
                  <Server size={12} /> Service
                </dt>
                <dd className="text-sm font-medium text-zinc-200">{incident.serviceName}</dd>
              </div>
              <div>
                <dt className="flex items-center gap-1.5 text-[11px] font-medium text-zinc-500 mb-1">
                  <User size={12} /> Assignee
                </dt>
                <dd className="text-sm text-zinc-200">{incident.assignee || <span className="text-zinc-600">Unassigned</span>}</dd>
              </div>
              <div>
                <dt className="flex items-center gap-1.5 text-[11px] font-medium text-zinc-500 mb-1">
                  <Calendar size={12} /> Created
                </dt>
                <dd className="text-sm text-zinc-300">{formatDate(incident.createdAt)}</dd>
              </div>
              {incident.resolvedAt && (
                <div>
                  <dt className="flex items-center gap-1.5 text-[11px] font-medium text-zinc-500 mb-1">
                    <CheckCircle2 size={12} /> Resolved
                  </dt>
                  <dd className="text-sm text-emerald-400">{formatDate(incident.resolvedAt)}</dd>
                </div>
              )}
            </dl>
          </div>

          {/* Quick stats */}
          <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-5">
            <h3 className="text-[11px] font-semibold uppercase tracking-wider text-zinc-500 mb-4">
              Quick Info
            </h3>
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs text-zinc-500">Timeline events</span>
                <span className="text-sm font-semibold text-zinc-200">{timeline.length}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-zinc-500">Severity</span>
                <SeverityBadge severity={incident.severity} />
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-zinc-500">Status</span>
                <StatusBadge status={incident.status} />
              </div>
            </div>
          </div>
        </div>
      </div>

      {error && (
        <div className="mt-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/20">
          <p className="text-rose-400 text-sm">{error}</p>
        </div>
      )}
    </div>
  );
}
