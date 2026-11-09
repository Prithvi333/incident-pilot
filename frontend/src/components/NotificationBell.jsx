/**
 * Notification bell with unread badge and a dropdown panel.
 * Clicking a notification marks it read and opens the related incident.
 *
 * The panel is rendered in a portal and positioned with fixed coordinates so it always fits the
 * viewport (it is clamped on phones) and is never clipped by the sidebar/drawer or top bar.
 */
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { Bell, CheckCheck, Inbox, WifiOff } from 'lucide-react';
import clsx from 'clsx';
import { useNotifications } from '../context/NotificationContext';
import { useRealtimeStatus } from '../context/RealtimeContext';

const MARGIN = 12;
const PANEL_WIDTH = 320;

function timeAgo(dateStr) {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'Just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export default function NotificationBell() {
  const { notifications, unreadCount, markRead, markAllRead } = useNotifications();
  const connected = useRealtimeStatus();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState(null);
  const buttonRef = useRef(null);
  const panelRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    function handleClick(e) {
      if (buttonRef.current?.contains(e.target) || panelRef.current?.contains(e.target)) return;
      setOpen(false);
    }
    const close = () => setOpen(false);
    document.addEventListener('mousedown', handleClick);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', handleClick);
      window.removeEventListener('resize', close);
    };
  }, [open]);

  function toggle() {
    if (!open) {
      const rect = buttonRef.current.getBoundingClientRect();
      const viewport = window.innerWidth;
      const width = Math.min(PANEL_WIDTH, viewport - MARGIN * 2);
      const left = Math.max(MARGIN, Math.min(rect.left, viewport - width - MARGIN));
      setPos({ top: rect.bottom + 8, left, width });
    }
    setOpen(!open);
  }

  function handleSelect(notification) {
    if (!notification.read) markRead(notification.id);
    setOpen(false);
    if (notification.incidentId) navigate(`/incidents/${notification.incidentId}`);
  }

  return (
    <>
      <button
        ref={buttonRef}
        onClick={toggle}
        className="relative p-2.5 lg:p-2 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-white/[0.06] transition-colors"
        title="Notifications"
        aria-label={`Notifications${unreadCount ? `, ${unreadCount} unread` : ''}`}
      >
        <Bell size={18} strokeWidth={1.8} />
        {unreadCount > 0 && (
          <span className="absolute top-0.5 right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-rose-500 text-[10px] font-bold text-white flex items-center justify-center">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && pos && createPortal(
        <div
          ref={panelRef}
          style={{
            top: pos.top,
            left: pos.left,
            width: pos.width,
            maxHeight: `min(28rem, calc(100dvh - ${pos.top}px - ${MARGIN}px))`,
          }}
          className="fixed flex flex-col bg-zinc-800 border border-white/[0.08] rounded-xl shadow-2xl shadow-black/40 z-[60] overflow-hidden animate-slide-up"
        >
          <div className="flex items-center justify-between px-4 py-3 border-b border-white/[0.06]">
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-semibold text-zinc-200">Notifications</h3>
              {!connected && (
                <span title="Live updates paused — reconnecting" className="text-amber-400">
                  <WifiOff size={13} />
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                onClick={markAllRead}
                className="inline-flex items-center gap-1 py-1.5 text-[11px] font-medium text-indigo-400 hover:text-indigo-300 transition-colors"
              >
                <CheckCheck size={12} />
                Mark all read
              </button>
            )}
          </div>

          <div className="overflow-y-auto overscroll-contain">
            {notifications.length === 0 ? (
              <div className="flex flex-col items-center py-10 text-center">
                <Inbox size={22} className="text-zinc-600 mb-2" />
                <p className="text-sm text-zinc-500">You're all caught up</p>
              </div>
            ) : (
              <ul className="divide-y divide-white/[0.04]">
                {notifications.map((n) => (
                  <li key={n.id}>
                    <button
                      onClick={() => handleSelect(n)}
                      className={clsx(
                        'w-full text-left px-4 py-3.5 hover:bg-white/[0.04] transition-colors flex gap-3',
                        !n.read && 'bg-indigo-500/[0.05]',
                      )}
                    >
                      <span
                        className={clsx(
                          'mt-1.5 w-2 h-2 rounded-full shrink-0',
                          n.read ? 'bg-transparent' : 'bg-indigo-400',
                        )}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block text-[13px] font-medium text-zinc-200 truncate">{n.title}</span>
                        <span className="block text-xs text-zinc-500 line-clamp-2 mt-0.5 break-words">{n.message}</span>
                        <span className="block text-[11px] text-zinc-600 mt-1">{timeAgo(n.createdAt)}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
