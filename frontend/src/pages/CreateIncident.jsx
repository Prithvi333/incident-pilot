import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { createIncident } from '../api/incidentApi';
import {
  AlertTriangle,
  ArrowLeft,
  Server,
  User,
  FileText,
  Shield,
  Loader2,
} from 'lucide-react';
import clsx from 'clsx';

const SEVERITIES = [
  { value: 'P1_CRITICAL', label: 'P1 — Critical', desc: 'Service down, customer impact', color: 'text-rose-400' },
  { value: 'P2_HIGH', label: 'P2 — High', desc: 'Degraded service, partial impact', color: 'text-orange-400' },
  { value: 'P3_MEDIUM', label: 'P3 — Medium', desc: 'Non-critical issue, workaround available', color: 'text-amber-400' },
  { value: 'P4_LOW', label: 'P4 — Low', desc: 'Cosmetic or minor issue', color: 'text-sky-400' },
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
    <div className="max-w-2xl mx-auto animate-fade-in">
      <Link
        to="/incidents"
        className="inline-flex items-center gap-1.5 text-sm text-zinc-500 hover:text-zinc-300 transition-colors mb-6"
      >
        <ArrowLeft size={14} />
        Back to incidents
      </Link>

      <div className="mb-8">
        <h1 className="text-2xl font-bold text-zinc-50 tracking-tight">Report Incident</h1>
        <p className="text-sm text-zinc-500 mt-1">Create a new incident to begin the response process</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-6 space-y-5">
          {/* Title */}
          <div>
            <label className="flex items-center gap-2 text-[12px] font-semibold uppercase tracking-wider text-zinc-400 mb-2">
              <AlertTriangle size={13} />
              Incident Title
              <span className="text-rose-400">*</span>
            </label>
            <input
              type="text"
              value={form.title}
              onChange={handleChange('title')}
              placeholder="Brief description of what's happening"
              className={clsx(
                'w-full bg-zinc-800/50 border rounded-lg px-4 py-3 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors',
                errors.title ? 'border-rose-500/50' : 'border-white/[0.08] hover:border-white/[0.12]'
              )}
            />
            {errors.title && (
              <p className="text-rose-400 text-xs mt-1.5 flex items-center gap-1">
                <span className="w-1 h-1 rounded-full bg-rose-400" />
                {errors.title}
              </p>
            )}
          </div>

          {/* Description */}
          <div>
            <label className="flex items-center gap-2 text-[12px] font-semibold uppercase tracking-wider text-zinc-400 mb-2">
              <FileText size={13} />
              Description
            </label>
            <textarea
              rows={4}
              value={form.description}
              onChange={handleChange('description')}
              placeholder="Detailed information, error messages, affected users, dashboards..."
              className="w-full bg-zinc-800/50 border border-white/[0.08] rounded-lg px-4 py-3 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors hover:border-white/[0.12] resize-none"
            />
          </div>

          {/* Severity */}
          <div>
            <label className="flex items-center gap-2 text-[12px] font-semibold uppercase tracking-wider text-zinc-400 mb-2">
              <Shield size={13} />
              Severity
              <span className="text-rose-400">*</span>
            </label>
            <div className="grid grid-cols-2 gap-2">
              {SEVERITIES.map(({ value, label, desc, color }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setForm({ ...form, severity: value })}
                  className={clsx(
                    'text-left p-3 rounded-lg border transition-all duration-150',
                    form.severity === value
                      ? 'border-indigo-500/40 bg-indigo-500/[0.07] ring-1 ring-indigo-500/20'
                      : 'border-white/[0.06] bg-white/[0.02] hover:border-white/[0.1] hover:bg-white/[0.04]'
                  )}
                >
                  <p className={clsx('text-sm font-semibold', form.severity === value ? color : 'text-zinc-300')}>
                    {label}
                  </p>
                  <p className="text-[11px] text-zinc-500 mt-0.5">{desc}</p>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="bg-zinc-800/50 rounded-xl border border-white/[0.06] p-6 space-y-5">
          {/* Service */}
          <div>
            <label className="flex items-center gap-2 text-[12px] font-semibold uppercase tracking-wider text-zinc-400 mb-2">
              <Server size={13} />
              Service
              <span className="text-rose-400">*</span>
            </label>
            <input
              type="text"
              value={form.serviceName}
              onChange={handleChange('serviceName')}
              placeholder="e.g. payment-service, api-gateway"
              className={clsx(
                'w-full bg-zinc-800/50 border rounded-lg px-4 py-3 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors',
                errors.serviceName ? 'border-rose-500/50' : 'border-white/[0.08] hover:border-white/[0.12]'
              )}
            />
            {errors.serviceName && (
              <p className="text-rose-400 text-xs mt-1.5 flex items-center gap-1">
                <span className="w-1 h-1 rounded-full bg-rose-400" />
                {errors.serviceName}
              </p>
            )}
          </div>

          {/* Assignee */}
          <div>
            <label className="flex items-center gap-2 text-[12px] font-semibold uppercase tracking-wider text-zinc-400 mb-2">
              <User size={13} />
              Assignee
            </label>
            <input
              type="text"
              value={form.assignee}
              onChange={handleChange('assignee')}
              placeholder="Who's handling this?"
              className="w-full bg-zinc-800/50 border border-white/[0.08] rounded-lg px-4 py-3 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors hover:border-white/[0.12]"
            />
          </div>
        </div>

        {serverError && (
          <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20">
            <p className="text-rose-400 text-sm">{serverError}</p>
          </div>
        )}

        <div className="flex justify-end gap-3">
          <Link
            to="/incidents"
            className="px-5 py-2.5 rounded-lg text-sm font-medium text-zinc-400 hover:text-zinc-200 border border-white/[0.08] hover:border-white/[0.12] transition-colors"
          >
            Cancel
          </Link>
          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center gap-2 bg-indigo-600 text-white px-6 py-2.5 rounded-lg text-sm font-semibold hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors shadow-lg shadow-indigo-500/20"
          >
            {submitting ? (
              <>
                <Loader2 size={15} className="animate-spin" />
                Creating...
              </>
            ) : (
              <>
                <AlertTriangle size={15} />
                Create Incident
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
