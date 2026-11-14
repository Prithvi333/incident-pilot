/**
 * Centralized API client — all backend calls go through this Axios instance.
 *
 * The request interceptor attaches the JWT token from localStorage.
 * The response interceptor auto-redirects to /login on 401 (expired/invalid token),
 * but skips auth endpoints to avoid redirect loops during login/register.
 */
import axios from "axios";

const api = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
});

// Attach JWT Bearer token to every outgoing request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Global 401 handler: clear stale auth state and redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      error.response?.status === 401 &&
      !error.config.url.includes("/auth/")
    ) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  },
);

// ── Incidents ──

export const createIncident = (data) => api.post("/incidents", data);

export const getIncidents = (params = {}) => api.get("/incidents", { params });

export const getIncident = (id) => api.get(`/incidents/${id}`);

export const updateIncidentStatus = (id, status) =>
  api.patch(`/incidents/${id}/status`, { status });

export const updateIncident = (id, data) => api.patch(`/incidents/${id}`, data);

export const assignIncident = (id, assignee) =>
  api.patch(`/incidents/${id}/assignee`, { assignee: assignee || null });

export const deleteIncident = (id) => api.delete(`/incidents/${id}`);

export const getIncidentStats = () => api.get("/incidents/stats");

// ── Organization ──

export const getOrganization = () => api.get("/organization");

export const getMembers = () => api.get("/organization/members");

// ── Notifications ──

export const getNotifications = (params = {}) =>
  api.get("/notifications", { params });

export const getUnreadCount = () => api.get("/notifications/unread-count");

export const markNotificationRead = (id) =>
  api.patch(`/notifications/${id}/read`);

export const markAllNotificationsRead = () => api.post("/notifications/read-all");

// ── Similar Incidents (RAG) ──

export const getSimilarIncidents = (id) => api.get(`/incidents/${id}/similar`);

// Database-only similarity: works even when the AI service is down
export const getRelatedIncidents = (id) => api.get(`/incidents/${id}/related`);

// ── Timeline ──

export const getTimeline = (incidentId) =>
  api.get(`/incidents/${incidentId}/timeline`);

export const addTimelineEvent = (incidentId, data) =>
  api.post(`/incidents/${incidentId}/timeline`, data);

export default api;
