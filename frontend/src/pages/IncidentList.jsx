import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getIncidents } from '../api/incidentApi';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';

const STATUSES = ['', 'TRIGGERED', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'POSTMORTEM'];
const SEVERITIES = ['', 'P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW'];

export default function IncidentList() {
  const [incidents, setIncidents] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');
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

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Incidents</h1>
        <Link
          to="/incidents/new"
          className="bg-blue-600 text-white px-4 py-2 rounded text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          + New Incident
        </Link>
      </div>

      {/* Filters */}
      <div className="flex gap-3 mb-4">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="border border-gray-300 rounded px-3 py-1.5 text-sm bg-white"
        >
          <option value="">All Statuses</option>
          {STATUSES.filter(Boolean).map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <select
          value={severityFilter}
          onChange={(e) => setSeverityFilter(e.target.value)}
          className="border border-gray-300 rounded px-3 py-1.5 text-sm bg-white"
        >
          <option value="">All Severities</option>
          {SEVERITIES.filter(Boolean).map((s) => (
            <option key={s} value={s}>{s.replace('_', ' ')}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      {loading ? (
        <p className="text-gray-500 text-sm">Loading...</p>
      ) : incidents.length === 0 ? (
        <p className="text-gray-400 text-sm">No incidents match the selected filters.</p>
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
                <th className="px-4 py-3">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {incidents.map((incident) => (
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
                  <td className="px-4 py-3 text-gray-400 text-xs">
                    {new Date(incident.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
