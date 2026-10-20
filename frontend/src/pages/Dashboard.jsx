import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getIncidentStats, getIncidents } from '../api/incidentApi';
import StatsCard from '../components/StatsCard';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clock,
  ArrowRight,
  BarChart3,
  Shield,
} from 'lucide-react';
import {
  PieChart, Pie, Cell, ResponsiveContainer, Tooltip,
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts';

const STATUS_COLORS = {
  TRIGGERED: '#f43f5e',
  ACKNOWLEDGED: '#f59e0b',
  INVESTIGATING: '#3b82f6',
  RESOLVED: '#10b981',
  POSTMORTEM: '#71717a',
};

const SEVERITY_COLORS = {
  P1_CRITICAL: '#f43f5e',
  P2_HIGH: '#f97316',
  P3_MEDIUM: '#f59e0b',
  P4_LOW: '#0ea5e9',
};

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-zinc-800 border border-white/[0.08] rounded-lg px-3 py-2 shadow-xl">
      <p className="text-xs text-zinc-300 font-medium">{payload[0].name}</p>
      <p className="text-sm font-bold text-zinc-100">{payload[0].value}</p>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="animate-fade-in">
      <div className="skeleton h-8 w-48 rounded-lg mb-8" />
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {[1, 2, 3, 4].map(i => (
          <div key={i} className="skeleton h-32 rounded-xl" />
        ))}
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-8">
        <div className="skeleton h-64 rounded-xl" />
        <div className="skeleton h-64 rounded-xl" />
      </div>
      <div className="skeleton h-64 rounded-xl" />
    </div>
  );
}

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function fetchData() {
      try {
        const [statsRes, incidentsRes] = await Promise.all([
          getIncidentStats(),
          getIncidents(),
        ]);
        setStats(statsRes.data);
        setIncidents(incidentsRes.data);
      } catch {
        setError('Failed to load dashboard data. Is the backend running?');
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, []);

  if (loading) return <DashboardSkeleton />;
  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh] animate-fade-in">
        <div className="p-4 rounded-full bg-rose-500/10 mb-4">
          <AlertTriangle size={32} className="text-rose-400" />
        </div>
        <p className="text-zinc-400 text-center max-w-md">{error}</p>
      </div>
    );
  }

  const activeIncidents = incidents.filter(
    i => i.status !== 'RESOLVED' && i.status !== 'POSTMORTEM'
  );

  const statusCounts = incidents.reduce((acc, i) => {
    acc[i.status] = (acc[i.status] || 0) + 1;
    return acc;
  }, {});
  const statusData = Object.entries(statusCounts).map(([name, value]) => ({
    name,
    value,
    color: STATUS_COLORS[name] || '#71717a',
  }));

  const severityCounts = incidents.reduce((acc, i) => {
    acc[i.severity] = (acc[i.severity] || 0) + 1;
    return acc;
  }, {});
  const severityData = [
    { name: 'P1', key: 'P1_CRITICAL', color: SEVERITY_COLORS.P1_CRITICAL },
    { name: 'P2', key: 'P2_HIGH', color: SEVERITY_COLORS.P2_HIGH },
    { name: 'P3', key: 'P3_MEDIUM', color: SEVERITY_COLORS.P3_MEDIUM },
    { name: 'P4', key: 'P4_LOW', color: SEVERITY_COLORS.P4_LOW },
  ].map(s => ({ ...s, value: severityCounts[s.key] || 0 }));

  return (
    <div className="animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-zinc-50 tracking-tight">Dashboard</h1>
          <p className="text-sm text-zinc-500 mt-1">Real-time incident overview</p>
        </div>
        <Link
          to="/incidents/new"
          className="inline-flex items-center gap-2 bg-indigo-600 text-white px-4 py-2.5 rounded-lg text-sm font-semibold hover:bg-indigo-500 transition-colors shadow-lg shadow-indigo-500/20"
        >
          <AlertTriangle size={15} />
          Report Incident
        </Link>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatsCard
          title="Total Incidents"
          value={stats.totalIncidents}
          icon={BarChart3}
          iconColor="text-indigo-400"
          subtitle="All time"
        />
        <StatsCard
          title="Active"
          value={stats.activeIncidents}
          icon={Activity}
          iconColor="text-rose-400"
          subtitle="Requires attention"
        />
        <StatsCard
          title="Resolved"
          value={stats.resolvedIncidents}
          icon={CheckCircle2}
          iconColor="text-emerald-400"
          subtitle="Successfully closed"
        />
        <StatsCard
          title="Avg Resolution"
          value={stats.averageResolutionMinutes ? `${Math.round(stats.averageResolutionMinutes)}m` : '--'}
          icon={Clock}
          iconColor="text-amber-400"
          subtitle="Mean time to resolve"
        />
      </div>

      {/* Charts */}
      {incidents.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-8">
          {/* Status Distribution */}
          <div className="bg-zinc-900 rounded-xl border border-white/[0.06] p-5">
            <h3 className="text-sm font-semibold text-zinc-300 mb-4 flex items-center gap-2">
              <Activity size={15} className="text-zinc-500" />
              Status Distribution
            </h3>
            <div className="flex items-center gap-6">
              <div className="w-40 h-40 flex-shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={statusData}
                      cx="50%" cy="50%"
                      innerRadius={38}
                      outerRadius={65}
                      paddingAngle={3}
                      dataKey="value"
                      strokeWidth={0}
                    >
                      {statusData.map((entry, i) => (
                        <Cell key={i} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip content={<ChartTooltip />} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="flex-1 space-y-2">
                {statusData.map(s => (
                  <div key={s.name} className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: s.color }} />
                      <span className="text-xs text-zinc-400">{s.name}</span>
                    </div>
                    <span className="text-sm font-semibold text-zinc-200">{s.value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Severity Distribution */}
          <div className="bg-zinc-900 rounded-xl border border-white/[0.06] p-5">
            <h3 className="text-sm font-semibold text-zinc-300 mb-4 flex items-center gap-2">
              <Shield size={15} className="text-zinc-500" />
              Severity Breakdown
            </h3>
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={severityData} barSize={32}>
                <CartesianGrid strokeDasharray="3 3" stroke="#27272a" vertical={false} />
                <XAxis dataKey="name" tick={{ fill: '#a1a1aa', fontSize: 12 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#71717a', fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} />
                <Tooltip content={<ChartTooltip />} cursor={{ fill: 'rgba(255,255,255,0.03)' }} />
                <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                  {severityData.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Active Incidents */}
      <div className="bg-zinc-900 rounded-xl border border-white/[0.06] overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/[0.06]">
          <h3 className="text-sm font-semibold text-zinc-300 flex items-center gap-2">
            <AlertTriangle size={15} className="text-rose-400" />
            Active Incidents
            {activeIncidents.length > 0 && (
              <span className="ml-1 text-[11px] font-bold px-2 py-0.5 rounded-full bg-rose-500/10 text-rose-400 ring-1 ring-rose-500/20">
                {activeIncidents.length}
              </span>
            )}
          </h3>
          <Link
            to="/incidents"
            className="inline-flex items-center gap-1 text-xs font-medium text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            View all
            <ArrowRight size={13} />
          </Link>
        </div>

        {activeIncidents.length === 0 ? (
          <div className="flex flex-col items-center py-12 text-center">
            <div className="p-3 rounded-full bg-emerald-500/10 mb-3">
              <CheckCircle2 size={24} className="text-emerald-400" />
            </div>
            <p className="text-sm font-medium text-zinc-300">All clear</p>
            <p className="text-xs text-zinc-500 mt-1">No active incidents right now</p>
          </div>
        ) : (
          <div className="divide-y divide-white/[0.04]">
            {activeIncidents.slice(0, 8).map(incident => (
              <Link
                key={incident.id}
                to={`/incidents/${incident.id}`}
                className="flex items-center gap-4 px-5 py-3.5 hover:bg-white/[0.02] transition-colors group"
              >
                <span className="text-xs text-zinc-600 font-mono w-8">#{incident.id}</span>
                <span className="flex-1 text-sm text-zinc-200 font-medium group-hover:text-zinc-50 transition-colors truncate">
                  {incident.title}
                </span>
                <SeverityBadge severity={incident.severity} />
                <StatusBadge status={incident.status} />
                <span className="text-xs text-zinc-600 hidden sm:block w-24 text-right">
                  {incident.serviceName}
                </span>
                <ArrowRight size={14} className="text-zinc-700 group-hover:text-zinc-400 transition-colors" />
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
