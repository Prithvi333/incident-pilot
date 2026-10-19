import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getIncidents } from '../api/incidentApi';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';
import {
  Plus,
  Search,
  SlidersHorizontal,
  ArrowRight,
  AlertTriangle,
  Inbox,
} from 'lucide-react';
import clsx from 'clsx';

const STATUSES = ['', 'TRIGGERED', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'POSTMORTEM'];
const SEVERITIES = ['', 'P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW'];

const STATUS_LABELS = {
  TRIGGERED: 'Triggered',
  ACKNOWLEDGED: 'Acknowledged',
  INVESTIGATING: 'Investigating',
  RESOLVED: 'Resolved',
  POSTMORTEM: 'Postmortem',
};

const SEVERITY_LABELS = {
  P1_CRITICAL: 'P1 Critical',
  P2_HIGH: 'P2 High',
  P3_MEDIUM: 'P3 Medium',
  P4_LOW: 'P4 Low',
};

function TableSkeleton() {
  return (
    <div className="divide-y divide-white/[0.04]">
      {[1, 2, 3, 4, 5].map(i => (
        <div key={i} className="flex items-center gap-4 px-5 py-4">
          <div className="skeleton w-8 h-4 rounded" />
          <div className="skeleton flex-1 h-4 rounded" />
          <div className="skeleton w-16 h-6 rounded-md" />
          <div className="skeleton w-24 h-6 rounded-full" />
          <div className="skeleton w-24 h-4 rounded" />
          <div className="skeleton w-28 h-4 rounded" />
        </div>
      ))}
    </div>
  );
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  const now = new Date();
  const diff = now - d;
  const mins = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (mins < 1) return 'Just now';
  if (mins < 60) return `${mins}m ago`;
  if (hours < 24) return `${hours}h ago`;
  if (days < 7) return `${days}d ago`;
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export default function IncidentList() {
  const [incidents, setIncidents] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchIncidents() {
      setLoading(true);
      try {
        const params = {};
        if (statusFilter) params.status = statusFilter;
        if (severityFilter) params.severity = severityFilter;
        const res = await getIncidents(params);
        setIncidents(res.data);
      } catch {
        setIncidents([]);
      } finally {
        setLoading(false);
      }
    }
    fetchIncidents();
  }, [statusFilter, severityFilter]);

  const filtered = search
    ? incidents.filter(i =>
        i.title.toLowerCase().includes(search.toLowerCase()) ||
        i.serviceName?.toLowerCase().includes(search.toLowerCase())
      )
    : incidents;

  return (
    <div className="animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-zinc-50 tracking-tight">Incidents</h1>
          <p className="text-sm text-zinc-500 mt-1">
            {incidents.length} total incident{incidents.length !== 1 ? 's' : ''}
          </p>
        </div>
        <Link
          to="/incidents/new"
          className="inline-flex items-center gap-2 bg-indigo-600 text-white px-4 py-2.5 rounded-lg text-sm font-semibold hover:bg-indigo-500 transition-colors shadow-lg shadow-indigo-500/20"
        >
          <Plus size={16} strokeWidth={2.5} />
          New Incident
        </Link>
      </div>

      {/* Search and filters */}
      <div className="flex flex-col sm:flex-row gap-3 mb-5">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by title or service..."
            className="w-full bg-zinc-900 border border-white/[0.08] rounded-lg pl-10 pr-4 py-2.5 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors hover:border-white/[0.12]"
          />
        </div>
        <div className="flex gap-2">
          <div className="relative">
            <SlidersHorizontal size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500 pointer-events-none" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className={clsx(
                'appearance-none bg-zinc-900 border rounded-lg pl-9 pr-8 py-2.5 text-sm focus-ring transition-colors cursor-pointer',
                statusFilter
                  ? 'border-indigo-500/30 text-indigo-400'
                  : 'border-white/[0.08] text-zinc-400 hover:border-white/[0.12]'
              )}
            >
              <option value="">All Statuses</option>
              {STATUSES.filter(Boolean).map((s) => (
                <option key={s} value={s}>{STATUS_LABELS[s]}</option>
              ))}
            </select>
          </div>
          <select
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value)}
            className={clsx(
              'appearance-none bg-zinc-900 border rounded-lg px-4 py-2.5 text-sm focus-ring transition-colors cursor-pointer',
              severityFilter
                ? 'border-indigo-500/30 text-indigo-400'
                : 'border-white/[0.08] text-zinc-400 hover:border-white/[0.12]'
            )}
          >
            <option value="">All Severities</option>
            {SEVERITIES.filter(Boolean).map((s) => (
              <option key={s} value={s}>{SEVERITY_LABELS[s]}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-zinc-900 rounded-xl border border-white/[0.06] overflow-hidden">
        {/* Table header */}
        <div className="hidden sm:grid grid-cols-[3rem_1fr_5rem_7rem_7rem_6rem] gap-4 px-5 py-3 border-b border-white/[0.06] text-[11px] font-semibold uppercase tracking-wider text-zinc-500">
          <span>ID</span>
          <span>Title</span>
          <span>Severity</span>
          <span>Status</span>
          <span>Service</span>
          <span className="text-right">Created</span>
        </div>

        {loading ? (
          <TableSkeleton />
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center py-16 text-center">
            <div className="p-3 rounded-full bg-zinc-800 mb-3">
              {search || statusFilter || severityFilter ? (
                <Search size={24} className="text-zinc-500" />
              ) : (
                <Inbox size={24} className="text-zinc-500" />
              )}
            </div>
            <p className="text-sm font-medium text-zinc-400">
              {search || statusFilter || severityFilter
                ? 'No incidents match your filters'
                : 'No incidents yet'}
            </p>
            <p className="text-xs text-zinc-600 mt-1">
              {search || statusFilter || severityFilter
                ? 'Try adjusting your search or filter criteria'
                : 'Create your first incident to get started'}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-white/[0.04]">
            {filtered.map((incident) => (
              <Link
                key={incident.id}
                to={`/incidents/${incident.id}`}
                className="group grid grid-cols-1 sm:grid-cols-[3rem_1fr_5rem_7rem_7rem_6rem] gap-2 sm:gap-4 items-center px-5 py-3.5 hover:bg-white/[0.02] transition-colors"
              >
                <span className="text-xs text-zinc-600 font-mono">#{incident.id}</span>
                <div className="flex items-center gap-2 min-w-0">
                  {incident.severity === 'P1_CRITICAL' && (
                    <AlertTriangle size={14} className="text-rose-500 shrink-0" />
                  )}
                  <span className="text-sm text-zinc-200 font-medium group-hover:text-zinc-50 transition-colors truncate">
                    {incident.title}
                  </span>
                </div>
                <SeverityBadge severity={incident.severity} />
                <StatusBadge status={incident.status} />
                <span className="text-xs text-zinc-500 truncate">{incident.serviceName}</span>
                <span className="text-xs text-zinc-600 text-right flex items-center justify-end gap-1">
                  {formatDate(incident.createdAt)}
                  <ArrowRight size={12} className="text-zinc-700 group-hover:text-zinc-400 transition-colors hidden sm:block" />
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
