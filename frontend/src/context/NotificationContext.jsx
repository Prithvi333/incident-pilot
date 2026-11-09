/**
 * Notification state: the persisted list + unread count, kept live by WebSocket pushes.
 *
 * Loads from the REST API on mount and after every reconnect (so nothing is missed), prepends
 * pushed notifications, and ignores a push whose id is already in the list (no duplicates).
 */
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import {
  getNotifications,
  getUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
} from '../api/incidentApi';
import { useNotificationPush, useResync } from './RealtimeContext';

const NotificationContext = createContext(null);

export function NotificationProvider({ children }) {
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const refresh = useCallback(async () => {
    try {
      const [list, count] = await Promise.all([getNotifications({ limit: 50 }), getUnreadCount()]);
      setNotifications(list.data);
      setUnreadCount(count.data.count);
    } catch {
      // The bell is non-critical; keep whatever we already have.
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useResync(refresh);

  useNotificationPush((notification) => {
    setNotifications((current) =>
      current.some((n) => n.id === notification.id)
        ? current
        : [notification, ...current].slice(0, 50),
    );
    // The server is the source of truth for the unread count, so a duplicate push cannot inflate it.
    getUnreadCount()
      .then((res) => setUnreadCount(res.data.count))
      .catch(() => {});
  });

  const markRead = useCallback(async (id) => {
    try {
      await markNotificationRead(id);
      setNotifications((current) => current.map((n) => (n.id === id ? { ...n, read: true } : n)));
      const count = await getUnreadCount();
      setUnreadCount(count.data.count);
    } catch {
      // ignore — the next refresh corrects the state
    }
  }, []);

  const markAllRead = useCallback(async () => {
    try {
      await markAllNotificationsRead();
      setNotifications((current) => current.map((n) => ({ ...n, read: true })));
      setUnreadCount(0);
    } catch {
      // ignore
    }
  }, []);

  return (
    <NotificationContext.Provider value={{ notifications, unreadCount, markRead, markAllRead, refresh }}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) throw new Error('useNotifications must be used within NotificationProvider');
  return context;
}
