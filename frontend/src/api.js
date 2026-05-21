import axios from 'axios'

// Base URL: in dev, Vite proxy forwards /sync/* → localhost:8080
// In production, set VITE_API_BASE_URL to the backend URL
const BASE = import.meta.env.VITE_API_BASE_URL || ''

const api = axios.create({ baseURL: BASE, timeout: 10000 })

// ── Health & Status ──────────────────────────────────────────────
export const fetchHealth = () =>
  api.get('/sync/health').then(r => r.data)

export const fetchApiStatus = () =>
  api.get('/sync/api-status').then(r => r.data)

export const fetchActuatorHealth = () =>
  api.get('/actuator/health').then(r => r.data)

// ── Sync Events ──────────────────────────────────────────────────
export const fetchSyncEvents = (page = 0, size = 20) =>
  api.get('/sync/events', { params: { page, size } }).then(r => r.data)

// ── Webhook Logs ─────────────────────────────────────────────────
export const fetchWebhookLogs = (page = 0, size = 20) =>
  api.get('/sync/webhook-logs', { params: { page, size } }).then(r => r.data)

// ── Task Mappings ────────────────────────────────────────────────
export const fetchMappings = () =>
  api.get('/sync/mappings').then(r => r.data)

export const createMapping = (payload) =>
  api.post('/sync/mappings', payload).then(r => r.data)

export const enableMapping = (id) =>
  api.put(`/sync/mappings/${id}/enable`).then(r => r.data)

export const disableMapping = (id) =>
  api.put(`/sync/mappings/${id}/disable`).then(r => r.data)

export const triggerSync = (id) =>
  api.post(`/sync/mappings/${id}/sync`).then(r => r.data)

// ── Status Mappings ──────────────────────────────────────────────
export const fetchStatusMappings = () =>
  api.get('/sync/status-mappings').then(r => r.data)
