import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createIncident } from '../api/incidentApi';

const SEVERITIES = [
  { value: 'P1_CRITICAL', label: 'P1 — Critical' },
  { value: 'P2_HIGH', label: 'P2 — High' },
  { value: 'P3_MEDIUM', label: 'P3 — Medium' },
  { value: 'P4_LOW', label: 'P4 — Low' },
];

export default function CreateIncident() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    title: '',
    description: '',
    severity: 'P2_HIGH',
    assignee: '',
    serviceName: '',
  });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState(null);

  function validate() {
    const errs = {};
    if (!form.title.trim()) errs.title = 'Title is required';
    if (!form.serviceName.trim()) errs.serviceName = 'Service name is required';
    return errs;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const errs = validate();
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setSubmitting(true);
    setServerError(null);
    try {
      const res = await createIncident(form);
      navigate(`/incidents/${res.data.id}`);
    } catch (err) {
      if (err.response?.data?.errors) {
        setServerError(err.response.data.errors.join(', '));
      } else {
        setServerError('Failed to create incident. Is the backend running?');
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleChange(field) {
    return (e) => {
      setForm({ ...form, [field]: e.target.value });
      if (errors[field]) setErrors({ ...errors, [field]: null });
    };
  }

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold mb-6">Create Incident</h1>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow p-6 space-y-4">
        {/* Title */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Title *</label>
          <input
            type="text"
            value={form.title}
            onChange={handleChange('title')}
            placeholder="Brief description of what's happening"
            className={`w-full border rounded px-3 py-2 text-sm ${errors.title ? 'border-red-400' : 'border-gray-300'}`}
          />
          {errors.title && <p className="text-red-500 text-xs mt-1">{errors.title}</p>}
        </div>

        {/* Description */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea
            rows={3}
            value={form.description}
            onChange={handleChange('description')}
            placeholder="Detailed information, error messages, affected users..."
            className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          />
        </div>

        {/* Severity */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Severity *</label>
          <select
            value={form.severity}
            onChange={handleChange('severity')}
            className="w-full border border-gray-300 rounded px-3 py-2 text-sm bg-white"
          >
            {SEVERITIES.map(({ value, label }) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {/* Service Name */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Service *</label>
          <input
            type="text"
            value={form.serviceName}
            onChange={handleChange('serviceName')}
            placeholder="e.g. payment-service, api-gateway"
            className={`w-full border rounded px-3 py-2 text-sm ${errors.serviceName ? 'border-red-400' : 'border-gray-300'}`}
          />
          {errors.serviceName && <p className="text-red-500 text-xs mt-1">{errors.serviceName}</p>}
        </div>

        {/* Assignee */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Assignee</label>
          <input
            type="text"
            value={form.assignee}
            onChange={handleChange('assignee')}
            placeholder="Who's handling this?"
            className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          />
        </div>

        {serverError && (
          <p className="text-red-500 text-sm bg-red-50 p-3 rounded">{serverError}</p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-blue-600 text-white py-2.5 rounded font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
        >
          {submitting ? 'Creating...' : 'Create Incident'}
        </button>
      </form>
    </div>
  );
}
