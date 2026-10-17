import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getIncidentStats, getIncidents } from '../api/incidentApi';
import StatsCard from '../components/StatsCard';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [activeIncidents, setActiveIncidents] = useState([]);
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
        // Show non-resolved incidents, sorted by severity
        const active = incidentsRes.data.filter(
          (i) => i.status !== 'RESOLVED' && i.status !== 'POSTMORTEM'
        );
        setActiveIncidents(active);
      } catch (err) {
        setError('Failed to load dashboard data. Is the backend running on port 8080?');
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, []);

  if (loading) return <p className="text-center mt-10 text-gray-500">Loading dashboard...</p>;
  if (error) return <p className="text-center mt-10 text-red-500">{error}</p>;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatsCard title="Total Incidents" value={stats.totalIncidents} />
        <StatsCard title="Active" value={stats.activeIncidents} color="text-red-600" />
        <StatsCard title="Resolved" value={stats.resolvedIncidents} color="text-green-600" />
        <StatsCard
          title="Avg Resolution"
          value={stats.averageResolutionMinutes ? `${Math.round(stats.averageResolutionMinutes)}m` : '—'}
          subtitle="Mean time to resolve"
        />
      </div>

      {/* Active incidents */}
      <h2 className="text-lg font-semibold mb-3">Active Incidents</h2>
      {activeIncidents.length === 0 ? (
        <p className="text-gray-400 text-sm">No active incidents. All clear! 🎉</p>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-gray-500 uppercase text-xs">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Severity</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Service</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {activeIncidents.map((incident) => (
                <tr key={incident.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 text-gray-400">#{incident.id}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/incidents/${incident.id}`}
                      className="text-blue-600 hover:underline font-medium"
                    >
                      {incident.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3"><SeverityBadge severity={incident.severity} /></td>
                  <td className="px-4 py-3"><StatusBadge status={incident.status} /></td>
                  <td className="px-4 py-3 text-gray-500">{incident.serviceName}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
