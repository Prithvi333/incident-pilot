import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import clsx from 'clsx';
import NotificationBell from './NotificationBell';
import {
  LayoutDashboard,
  AlertTriangle,
  PlusCircle,
  BookOpen,
  LogOut,
  Menu,
  X,
  Zap,
  ChevronRight,
  Copy,
  Check,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { getOrganization } from '../api/incidentApi';

const NAV_SECTIONS = [
  {
    label: 'Overview',
    items: [
      { path: '/', label: 'Dashboard', icon: LayoutDashboard },
    ],
  },
  {
    label: 'Management',
    items: [
      { path: '/incidents', label: 'Incidents', icon: AlertTriangle },
      { path: '/incidents/new', label: 'New Incident', icon: PlusCircle },
    ],
  },
  {
    label: 'AI & Data',
    items: [
      { path: '/documents', label: 'Knowledge Base', icon: BookOpen },
    ],
  },
];

/**
 * Copies text to the clipboard. navigator.clipboard only exists on HTTPS/localhost, so on plain-HTTP
 * deployments (and some mobile browsers) fall back to a hidden textarea + execCommand("copy").
 */
async function copyToClipboard(text) {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // fall through to the legacy path
    }
  }
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.style.position = 'fixed';
  area.style.top = '0';
  area.style.left = '-9999px';
  area.style.opacity = '0';
  document.body.appendChild(area);
  area.focus();
  area.select();
  area.setSelectionRange(0, text.length); // needed on iOS
  let ok = false;
  try {
    ok = document.execCommand('copy');
  } catch {
    ok = false;
  }
  document.body.removeChild(area);
  return ok;
}

/** ADMIN-only: shows the organization invite code so teammates can register into it. */
function InviteCode() {
  const [code, setCode] = useState(null);
  const [feedback, setFeedback] = useState(null); // 'copied' | 'failed' | null

  useEffect(() => {
    getOrganization()
      .then((res) => setCode(res.data.inviteCode))
      .catch(() => setCode(null));
  }, []);

  if (!code) return null;

  async function copy() {
    const ok = await copyToClipboard(code);
    setFeedback(ok ? 'copied' : 'failed');
    setTimeout(() => setFeedback(null), 2500);
  }

  return (
    <div className="mx-3 mb-3 px-3 py-2.5 rounded-lg bg-white/[0.03] border border-white/[0.06]">
      <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-600 mb-1">Invite code</p>
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-[11px] text-zinc-400 truncate select-all">{code}</span>
        <button
          onClick={copy}
          className="p-2 -m-1 rounded-md text-zinc-500 hover:text-zinc-200 hover:bg-white/[0.06] transition-colors shrink-0"
          title="Copy invite code"
          aria-label="Copy invite code"
        >
          {feedback === 'copied' ? (
            <Check size={14} className="text-emerald-400" />
          ) : (
            <Copy size={14} />
          )}
        </button>
      </div>
      <p
        role="status"
        className={
          'text-[11px] mt-1 min-h-[1rem] ' +
          (feedback === 'copied' ? 'text-emerald-400' : feedback === 'failed' ? 'text-rose-400' : 'text-transparent')
        }
      >
        {feedback === 'copied'
          ? 'Invite code copied'
          : feedback === 'failed'
            ? 'Could not copy - select the code and copy it manually'
            : '.'}
      </p>
    </div>
  );
}

function NavItem({ path, label, icon: Icon, isActive, onClick }) {
  return (
    <Link
      to={path}
      onClick={onClick}
      className={clsx(
        'group flex items-center gap-3 px-3 py-2.5 lg:py-2 rounded-lg text-[13px] font-medium transition-all duration-150',
        isActive
          ? 'bg-indigo-500/10 text-indigo-400'
          : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/[0.04]'
      )}
    >
      <Icon
        size={18}
        strokeWidth={1.8}
        className={clsx(
          'flex-shrink-0 transition-colors',
          isActive ? 'text-indigo-400' : 'text-zinc-500 group-hover:text-zinc-300'
        )}
      />
      <span>{label}</span>
      {isActive && (
        <ChevronRight size={14} className="ml-auto text-indigo-400/60" />
      )}
    </Link>
  );
}

export default function Sidebar() {
  const location = useLocation();
  const { user, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  function isActive(path) {
    if (path === '/') return location.pathname === '/';
    return location.pathname.startsWith(path);
  }

  const sidebarContent = (
    <div className="flex flex-col h-full">
      {/* Logo */}
      <div className="px-5 py-5 flex items-center gap-2.5">
        <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shadow-lg shadow-indigo-500/20">
          <Zap size={16} className="text-white" strokeWidth={2.5} />
        </div>
        <div className="min-w-0 flex-1">
          <span className="block text-[15px] font-bold text-zinc-100 tracking-tight">
            IncidentPilot
          </span>
          {user?.organizationName && (
            <span className="block text-[11px] text-zinc-500 truncate" title={user.organizationName}>
              {user.organizationName}
            </span>
          )}
        </div>
        {/* On small screens the bell lives in the top bar instead */}
        <div className="hidden lg:block">
          <NotificationBell />
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-2 space-y-6 overflow-y-auto">
        {NAV_SECTIONS.map((section) => (
          <div key={section.label}>
            <p className="px-3 mb-1.5 text-[10px] font-semibold uppercase tracking-widest text-zinc-600">
              {section.label}
            </p>
            <div className="space-y-0.5">
              {section.items.map((item) => (
                <NavItem
                  key={item.path}
                  {...item}
                  isActive={isActive(item.path)}
                  onClick={() => setMobileOpen(false)}
                />
              ))}
            </div>
          </div>
        ))}
      </nav>

      {user?.role === 'ADMIN' && <InviteCode />}

      {/* User section */}
      {user && (
        <div className="px-3 py-4 border-t border-white/[0.06]">
          <div className="flex items-center gap-3 px-3 py-2">
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-zinc-700 to-zinc-800 flex items-center justify-center text-xs font-bold text-zinc-300 uppercase ring-1 ring-white/[0.06]">
              {user.username?.charAt(0)}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-[13px] font-medium text-zinc-200 truncate">{user.username}</p>
              <p className="text-[11px] text-zinc-500">{user.role}</p>
            </div>
            <button
              onClick={logout}
              className="p-2.5 lg:p-1.5 rounded-md text-zinc-500 hover:text-zinc-300 hover:bg-white/[0.06] transition-colors"
              title="Sign out"
            >
              <LogOut size={15} strokeWidth={1.8} />
            </button>
          </div>
        </div>
      )}
    </div>
  );

  return (
    <>
      {/* Mobile/tablet top bar: menu, brand and notifications stay reachable without opening the drawer */}
      <div className="lg:hidden fixed top-0 inset-x-0 z-30 h-14 px-3 flex items-center gap-2 bg-zinc-900/95 backdrop-blur border-b border-white/[0.06]">
        <button
          onClick={() => setMobileOpen(true)}
          className="p-2.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-white/[0.06] transition-colors"
          aria-label="Open menu"
        >
          <Menu size={20} />
        </button>
        <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shrink-0">
          <Zap size={14} className="text-white" strokeWidth={2.5} />
        </div>
        <div className="min-w-0 flex-1">
          <span className="block text-sm font-bold text-zinc-100 tracking-tight leading-tight">IncidentPilot</span>
          {user?.organizationName && (
            <span className="block text-[11px] text-zinc-500 truncate leading-tight">{user.organizationName}</span>
          )}
        </div>
        <NotificationBell />
      </div>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="lg:hidden fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Mobile sidebar */}
      <aside
        className={clsx(
          'lg:hidden fixed inset-y-0 left-0 z-50 w-64 bg-zinc-900 border-r border-white/[0.06] transition-transform duration-300',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <button
          onClick={() => setMobileOpen(false)}
          className="absolute top-4 -right-11 p-2 rounded-lg bg-zinc-800 border border-white/[0.06] text-zinc-400 hover:text-zinc-200 transition-colors"
        >
          <X size={18} />
        </button>
        {sidebarContent}
      </aside>

      {/* Desktop sidebar */}
      <aside className="hidden lg:flex lg:flex-col lg:w-64 lg:flex-shrink-0 bg-zinc-900 border-r border-white/[0.06] h-screen sticky top-0">
        {sidebarContent}
      </aside>
    </>
  );
}
