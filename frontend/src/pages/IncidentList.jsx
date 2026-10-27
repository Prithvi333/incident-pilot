import { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { getIncidents } from '../api/incidentApi';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';
import {
  Plus,
  Search,
  SlidersHorizontal,
  ChevronDown,
  ArrowRight,
  AlertTriangle,
  Inbox,
  X,
} from 'lucide-react';
import clsx from 'clsx';

const STATUS_OPTIONS = [
  { value: 'TRIGGERED', label: 'Triggered', dot: 'bg-rose-500', text: 'text-rose-400', bg: 'bg-rose-500/10' },
  { value: 'ACKNOWLEDGED', label: 'Acknowledged', dot: 'bg-amber-500', text: 'text-amber-400', bg: 'bg-amber-500/10' },
  { value: 'INVESTIGATING', label: 'Investigating', dot: 'bg-blue-500', text: 'text-blue-400', bg: 'bg-blue-500/10' },
  { value: 'RESOLVED', label: 'Resolved', dot: 'bg-emerald-500', text: 'text-emerald-400', bg: 'bg-emerald-500/10' },
  { value: 'POSTMORTEM', label: 'Postmortem', dot: 'bg-zinc-400', text: 'text-zinc-400', bg: 'bg-zinc-500/10' },
];

const SEVERITY_OPTIONS = [
  { value: 'P1_CRITICAL', label: 'P1 Critical', bar: 'bg-rose-500', text: 'text-rose-400', bg: 'bg-rose-500/10' },
  { value: 'P2_HIGH', label: 'P2 High', bar: 'bg-orange-500', text: 'text-orange-400', bg: 'bg-orange-500/10' },
  { value: 'P3_MEDIUM', label: 'P3 Medium', bar: 'bg-amber-500', text: 'text-amber-400', bg: 'bg-amber-500/10' },
  { value: 'P4_LOW', label: 'P4 Low', bar: 'bg-sky-500', text: 'text-sky-400', bg: 'bg-sky-500/10' },
];

function FilterDropdown({ value, onChange, options, placeholder, icon: Icon }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const selected = options.find((o) => o.value === value);

  useEffect(() => {
    function handleClick(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className={clsx(
          'flex items-center gap-2 px-3 py-2.5 rounded-lg border text-sm transition-all cursor-pointer',
          selected
            ? 'border-indigo-500/30 bg-indigo-500/[0.05]'
            : 'border-white/[0.08] bg-zinc-800/50 hover:border-white/[0.12]'
        )}
      >
        {Icon && <Icon size={14} className="text-zinc-500" />}
        {selected ? (
          <span className="flex items-center gap-2">
            {selected.dot && <span className={clsx('w-2 h-2 rounded-full', selected.dot)} />}
            {selected.bar && <span className={clsx('w-1 h-3 rounded-full', selected.bar)} />}
            <span className={selected.text}>{selected.label}</span>
          </span>
        ) : (
          <span className="text-zinc-400">{placeholder}</span>
        )}
        {selected ? (
          <X
            size={14}
            className="text-zinc-500 hover:text-zinc-300 transition-colors ml-1"
            onClick={(e) => { e.stopPropagation(); onChange(''); setOpen(false); }}
          />
        ) : (
          <ChevronDown size={14} className={clsx('text-zinc-500 transition-transform', open && 'rotate-180')} />
        )}
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1.5 w-52 bg-zinc-800 border border-white/[0.08] rounded-xl shadow-2xl shadow-black/40 z-50 overflow-hidden animate-slide-up">
          {options.map((opt) => (
            <button
              key={opt.value}
              onClick={() => { onChange(opt.value); setOpen(false); }}
              className={clsx(
                'w-full flex items-center gap-2.5 px-3.5 py-2.5 text-sm transition-colors text-left',
                value === opt.value
                  ? 'bg-indigo-500/10 text-zinc-100'
                  : 'text-zinc-400 hover:bg-white/[0.04] hover:text-zinc-200'
              )}
            >
              {opt.dot && <span className={clsx('w-2 h-2 rounded-full shrink-0', opt.dot)} />}
              {opt.bar && <span className={clsx('w-1 h-3.5 rounded-full shrink-0', opt.bar)} />}
              <span>{opt.label}</span>
              {value === opt.value && (
                <span className="ml-auto text-indigo-400 text-xs font-semibold">Active</span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function TableSkeleton() {
  return (
    <div className="divide-y divide-white/[0.04] animate-fade-in">
      {[1, 2, 3, 4, 5].map(i => (
        <div key={i} className="flex items-center gap-4 px-5 py-4" style={{ opacity: 1 - i * 0.12 }}>
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
    const debounce = setTimeout(() => {
      async function fetchIncidents() {
        setLoading(true);
        try {
          const params = {};
          if (statusFilter) params.status = statusFilter;
          if (severityFilter) params.severity = severityFilter;
          if (search.trim()) params.search = search.trim();
          const res = await getIncidents(params);
          setIncidents(res.data);
        } catch {
          setIncidents([]);
        } finally {
          setLoading(false);
        }
      }
      fetchIncidents();
    }, search ? 300 : 0);
    return () => clearTimeout(debounce);
  }, [statusFilter, severityFilter, search]);

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
            className="w-full bg-zinc-800/50 border border-white/[0.08] rounded-lg pl-10 pr-4 py-2.5 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors hover:border-white/[0.12]"
          />
        </div>
        <div className="flex gap-2">
          <FilterDropdown
            value={statusFilter}
            onChange={setStatusFilter}
            options={STATUS_OPTIONS}
            placeholder="All Statuses"
            icon={SlidersHorizontal}
          />
          <FilterDropdown
            value={severityFilter}
            onChange={setSeverityFilter}
            options={SEVERITY_OPTIONS}
            placeholder="All Severities"
          />
        </div>
      </div>

      {/* Table */}
      <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] overflow-hidden">
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
        ) : incidents.length === 0 ? (
          <div className="flex flex-col items-center py-16 text-center animate-fade-in">
            <div className="p-3 rounded-full bg-zinc-700/50 mb-3">
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
            {incidents.map((incident, idx) => (
              <Link
                key={incident.id}
                to={`/incidents/${incident.id}`}
                className="group grid grid-cols-1 sm:grid-cols-[3rem_1fr_5rem_7rem_7rem_6rem] gap-2 sm:gap-4 items-center px-5 py-3.5 hover:bg-white/[0.02] transition-colors animate-list-in"
                style={{ animationDelay: `${Math.min(idx * 30, 300)}ms` }}
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
