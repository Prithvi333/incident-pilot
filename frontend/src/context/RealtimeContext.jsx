/**
 * Real-time connection (STOMP over WebSocket) shared by the whole app.
 *
 * - Authenticates with the same JWT as the REST API (sent in the STOMP CONNECT frame).
 * - Subscribes to the organization's incident topic and the user's private notification queue.
 * - Reconnects automatically (5s) and tells listeners to resync afterwards, because events
 *   published while the socket was down are not replayed.
 * - Drops duplicate events: every incident event carries a unique eventId.
 */
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useAuth } from './AuthContext';

const RealtimeContext = createContext(null);

const MAX_REMEMBERED_EVENTS = 500;

function websocketUrl() {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${window.location.host}/ws`;
}

export function RealtimeProvider({ children }) {
  const { token, user, logout } = useAuth();
  const [connected, setConnected] = useState(false);

  const listeners = useRef({ incident: new Set(), notification: new Set(), resync: new Set() });
  const seenEvents = useRef(new Set());
  const logoutRef = useRef(logout);
  useEffect(() => {
    logoutRef.current = logout;
  });

  useEffect(() => {
    if (!token || !user?.organizationId) return undefined;

    let hasConnectedBefore = false;

    const client = new Client({
      brokerURL: websocketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        setConnected(true);

        client.subscribe(`/topic/org.${user.organizationId}.incidents`, (frame) => {
          const event = JSON.parse(frame.body);
          if (seenEvents.current.has(event.eventId)) return;
          seenEvents.current.add(event.eventId);
          if (seenEvents.current.size > MAX_REMEMBERED_EVENTS) {
            seenEvents.current.delete(seenEvents.current.values().next().value);
          }
          listeners.current.incident.forEach((fn) => fn(event));
        });

        client.subscribe('/user/queue/notifications', (frame) => {
          const notification = JSON.parse(frame.body);
          listeners.current.notification.forEach((fn) => fn(notification));
        });

        // After a reconnect, anything published while we were offline was missed: refetch.
        if (hasConnectedBefore) {
          listeners.current.resync.forEach((fn) => fn());
        }
        hasConnectedBefore = true;
      },

      onWebSocketClose: () => setConnected(false),

      onStompError: (frame) => {
        const message = frame.headers?.message || '';
        // Invalid/expired token: retrying with the same token can never succeed.
        if (/token|authorization/i.test(message)) {
          client.deactivate();
          logoutRef.current();
        }
      },
    });

    client.activate();
    return () => {
      client.deactivate();
      setConnected(false);
    };
  }, [token, user?.organizationId]);

  const subscribe = useCallback((kind, handler) => {
    listeners.current[kind].add(handler);
    return () => listeners.current[kind].delete(handler);
  }, []);

  return <RealtimeContext.Provider value={{ connected, subscribe }}>{children}</RealtimeContext.Provider>;
}

function useRealtime() {
  const context = useContext(RealtimeContext);
  if (!context) throw new Error('Realtime hooks must be used within RealtimeProvider');
  return context;
}

/** True while the WebSocket is connected. */
export function useRealtimeStatus() {
  return useRealtime().connected;
}

/** Runs the handler for every incident event of the user's organization (latest handler is always used). */
export function useIncidentEvents(handler) {
  const { subscribe } = useRealtime();
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  useEffect(() => subscribe('incident', (event) => latest.current(event)), [subscribe]);
}

/** Runs the handler for each personal notification pushed to the user. */
export function useNotificationPush(handler) {
  const { subscribe } = useRealtime();
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  useEffect(() => subscribe('notification', (n) => latest.current(n)), [subscribe]);
}

/** Runs the handler after a reconnect, when data should be refetched to catch up. */
export function useResync(handler) {
  const { subscribe } = useRealtime();
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  useEffect(() => subscribe('resync', () => latest.current()), [subscribe]);
}
