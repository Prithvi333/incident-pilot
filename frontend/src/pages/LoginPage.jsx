import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/incidentApi';
import { Zap, Eye, EyeOff } from 'lucide-react';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const res = await api.post('/auth/login', form);
      login(res.data);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed. Is the backend running?');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-4">
      {/* Background glow */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[600px] h-[600px] bg-indigo-500/[0.07] rounded-full blur-[120px]" />
        <div className="absolute bottom-0 left-1/4 w-[400px] h-[400px] bg-violet-500/[0.05] rounded-full blur-[100px]" />
      </div>

      <div className="relative w-full max-w-[400px] animate-slide-up">
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shadow-lg shadow-indigo-500/25">
            <Zap size={20} className="text-white" strokeWidth={2.5} />
          </div>
          <span className="text-xl font-bold text-zinc-100 tracking-tight">IncidentPilot</span>
        </div>

        {/* Card */}
        <div className="bg-zinc-900/80 backdrop-blur-xl rounded-2xl border border-white/[0.06] p-8 shadow-2xl shadow-black/40">
          <div className="text-center mb-6">
            <h1 className="text-lg font-semibold text-zinc-100">Welcome back</h1>
            <p className="text-sm text-zinc-500 mt-1">Sign in to your account</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-[12px] font-medium text-zinc-400 mb-1.5">Username</label>
              <input
                type="text"
                value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })}
                className="w-full bg-zinc-800/50 border border-white/[0.08] rounded-lg px-3.5 py-2.5 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors hover:border-white/[0.12]"
                placeholder="Enter your username"
                autoFocus
              />
            </div>

            <div>
              <label className="block text-[12px] font-medium text-zinc-400 mb-1.5">Password</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  className="w-full bg-zinc-800/50 border border-white/[0.08] rounded-lg px-3.5 py-2.5 pr-10 text-sm text-zinc-100 placeholder-zinc-600 focus-ring transition-colors hover:border-white/[0.12]"
                  placeholder="Enter your password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-300 transition-colors"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {error && (
              <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/20">
                <p className="text-rose-400 text-sm">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={submitting || !form.username || !form.password}
              className="w-full bg-gradient-to-r from-indigo-600 to-indigo-500 text-white py-2.5 rounded-lg text-sm font-semibold hover:from-indigo-500 hover:to-indigo-400 disabled:opacity-40 disabled:cursor-not-allowed transition-all duration-200 shadow-lg shadow-indigo-500/20"
            >
              {submitting ? (
                <span className="inline-flex items-center gap-2">
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                  </svg>
                  Signing in...
                </span>
              ) : 'Sign In'}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-white/[0.06] text-center">
            <p className="text-sm text-zinc-500">
              No account?{' '}
              <Link to="/register" className="text-indigo-400 hover:text-indigo-300 font-medium transition-colors">
                Create one
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
