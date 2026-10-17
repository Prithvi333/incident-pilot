import axios from 'axios';

/**
 * Centralized API client.
 * In dev, Vite proxies /api to localhost:8080 (see vite.config.js).
 * In prod, this would point to the real backend URL.
 */
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

// ── Incidents ──

export const createIncident = (data) => api.post('/incidents', data);

export const getIncidents = (params = {}) => api.get('/incidents', { params });

export const getIncident = (id) => api.get(`/incidents/${id}`);

export const updateIncidentStatus = (id, status) =>
  api.patch(`/incidents/${id}/status`, { status });

export const getIncidentStats = () => api.get('/incidents/stats');

// ── Timeline ──

export const getTimeline = (incidentId) =>
  api.get(`/incidents/${incidentId}/timeline`);

export const addTimelineEvent = (incidentId, data) =>
  api.post(`/incidents/${incidentId}/timeline`, data);

export default api;
