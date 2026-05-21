import { useEffect, useState, useCallback } from 'react'
import {
  RefreshCw, Link2, CheckCircle, XCircle,
  Activity, ArrowRightLeft, Webhook, TrendingUp
} from 'lucide-react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend
} from 'recharts'

import StatCard   from '../components/StatCard'
import Badge      from '../components/Badge'
import ErrorAlert from '../components/ErrorAlert'
import { fetchHealth, fetchApiStatus, fetchSyncEvents, fetchMappings, fetchWebhookLogs } from '../api'

// ── helpers ──────────────────────────────────────────────────────
const fmt = (iso) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const STATUS_COLORS = {
  SUCCESS: '#10b981',
  FAILED:  '#f43f5e',
  SKIPPED: '#f59e0b',
}

// Build hourly histogram from sync events list
function buildHourly(events) {
  const buckets = {}
  const now = Date.now()
  for (let h = 23; h >= 0; h--) {
    const label = `${String(new Date(now - h * 3_600_000).getHours()).padStart(2, '0')}:00`
    buckets[label] = { time: label, success: 0, failed: 0 }
  }
  events.forEach(ev => {
    const d  = new Date(ev.createdAt)
    const h  = String(d.getHours()).padStart(2, '0') + ':00'
    if (buckets[h]) {
      if (ev.status === 'SUCCESS') buckets[h].success++
      else                         buckets[h].failed++
    }
  })
  return Object.values(buckets)
}

// ── component ────────────────────────────────────────────────────
export default function Dashboard() {
  const [health,   setHealth]   = useState(null)
  const [status,   setStatus]   = useState(null)
  const [events,   setEvents]   = useState([])
  const [mappings, setMappings] = useState([])
  const [wLogs,    setWLogs]    = useState(null)
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)
  const [lastRefresh, setLastRefresh] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [h, s, ev, mp, wl] = await Promise.allSettled([
        fetchHealth(),
        fetchApiStatus(),
        fetchSyncEvents(0, 200),
        fetchMappings(),
        fetchWebhookLogs(0, 1),
      ])
      if (h.status  === 'fulfilled') setHealth(h.value)
      if (s.status  === 'fulfilled') setStatus(s.value)
      if (ev.status === 'fulfilled') setEvents(ev.value.events || [])
      if (mp.status === 'fulfilled') setMappings(mp.value)
      if (wl.status === 'fulfilled') setWLogs(wl.value)
      setLastRefresh(new Date())
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  // Auto-refresh every 30s
  useEffect(() => {
    const t = setInterval(load, 30_000)
    return () => clearInterval(t)
  }, [load])

  // Derived stats
  const total    = events.length
  const success  = events.filter(e => e.status === 'SUCCESS').length
  const failed   = events.filter(e => e.status === 'FAILED').length
  const pct      = total > 0 ? Math.round((success / total) * 100) : 100
  const active   = Array.isArray(mappings) ? mappings.filter(m => m.active).length : 0
  const totalMap = Array.isArray(mappings) ? mappings.length : 0

  const hourly = buildHourly(events)

  const piData = [
    { name: 'Success', value: success, color: '#10b981' },
    { name: 'Failed',  value: failed,  color: '#f43f5e' },
  ].filter(d => d.value > 0)

  const recent = [...events].slice(0, 8)

  const apiOk =
    status?.linear?.reachable && status?.clickup?.reachable && status?.database?.connected

  return (
    <div className="space-y-6">
      {/* Header row */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-white">Dashboard</h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {lastRefresh ? `Last refreshed: ${lastRefresh.toLocaleTimeString()}` : 'Loading…'}
          </p>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-800 text-slate-300
                     hover:bg-slate-700 hover:text-white text-xs font-medium transition-colors disabled:opacity-50"
        >
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {error && <ErrorAlert message={error} onRetry={load} />}

      {/* KPI cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Total Syncs"
          value={total}
          sub={`Last 200 events`}
          icon={ArrowRightLeft}
          color="indigo"
          loading={loading}
        />
        <StatCard
          label="Success Rate"
          value={`${pct}%`}
          sub={`${success} succeeded · ${failed} failed`}
          icon={TrendingUp}
          color={pct >= 90 ? 'emerald' : pct >= 70 ? 'amber' : 'rose'}
          loading={loading}
        />
        <StatCard
          label="Active Mappings"
          value={`${active} / ${totalMap}`}
          sub="Linear ↔ ClickUp pairs"
          icon={Link2}
          color="sky"
          loading={loading}
        />
        <StatCard
          label="API Status"
          value={loading ? null : apiOk ? 'All OK' : 'Degraded'}
          sub={loading ? '' : `Linear · ClickUp · DB`}
          icon={Activity}
          color={apiOk ? 'emerald' : 'rose'}
          loading={loading}
        />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Area chart — 24h sync activity */}
        <div className="lg:col-span-2 rounded-xl border border-slate-700/60 bg-slate-900 p-5">
          <p className="text-sm font-semibold text-white mb-4">Sync Activity — Last 24 h</p>
          {loading
            ? <div className="h-48 bg-slate-800 rounded animate-pulse" />
            : (
              <ResponsiveContainer width="100%" height={180}>
                <AreaChart data={hourly} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="cSuccess" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#10b981" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="cFailed" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#f43f5e" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#f43f5e" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis
                    dataKey="time"
                    tick={{ fill: '#64748b', fontSize: 10 }}
                    tickLine={false}
                    axisLine={false}
                    interval={3}
                  />
                  <YAxis
                    tick={{ fill: '#64748b', fontSize: 10 }}
                    tickLine={false}
                    axisLine={false}
                    allowDecimals={false}
                  />
                  <Tooltip
                    contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, fontSize: 12 }}
                    labelStyle={{ color: '#94a3b8' }}
                  />
                  <Area type="monotone" dataKey="success" name="Success" stroke="#10b981" fill="url(#cSuccess)" strokeWidth={2} />
                  <Area type="monotone" dataKey="failed"  name="Failed"  stroke="#f43f5e" fill="url(#cFailed)"  strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            )
          }
        </div>

        {/* Pie chart — success/fail split */}
        <div className="rounded-xl border border-slate-700/60 bg-slate-900 p-5">
          <p className="text-sm font-semibold text-white mb-4">Outcome Distribution</p>
          {loading
            ? <div className="h-48 bg-slate-800 rounded animate-pulse" />
            : piData.length === 0
              ? <div className="h-48 flex items-center justify-center text-slate-500 text-sm">No data yet</div>
              : (
                <ResponsiveContainer width="100%" height={180}>
                  <PieChart>
                    <Pie
                      data={piData}
                      cx="50%"
                      cy="45%"
                      innerRadius={50}
                      outerRadius={75}
                      paddingAngle={3}
                      dataKey="value"
                    >
                      {piData.map((entry) => (
                        <Cell key={entry.name} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, fontSize: 12 }}
                    />
                    <Legend
                      iconType="circle"
                      iconSize={8}
                      formatter={(v) => <span style={{ color: '#94a3b8', fontSize: 11 }}>{v}</span>}
                    />
                  </PieChart>
                </ResponsiveContainer>
              )
          }
        </div>
      </div>

      {/* API status mini cards */}
      {status && (
        <div className="grid grid-cols-3 gap-3">
          {[
            { label: 'Linear API',  ok: status.linear?.reachable,   lat: status.linear?.latencyMs },
            { label: 'ClickUp API', ok: status.clickup?.reachable,  lat: status.clickup?.latencyMs },
            { label: 'Database',    ok: status.database?.connected, lat: null },
          ].map(({ label, ok, lat }) => (
            <div key={label}
              className="rounded-xl border border-slate-700/60 bg-slate-900 p-4 flex items-center gap-3"
            >
              {ok
                ? <CheckCircle size={16} className="text-emerald-400 shrink-0" />
                : <XCircle    size={16} className="text-rose-400 shrink-0" />
              }
              <div>
                <p className="text-xs font-medium text-white">{label}</p>
                <p className="text-xs text-slate-500">
                  {ok ? (lat != null ? `${lat} ms` : 'Connected') : 'Unreachable'}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Recent events table */}
      <div className="rounded-xl border border-slate-700/60 bg-slate-900 overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-700/60">
          <p className="text-sm font-semibold text-white">Recent Sync Events</p>
          <Badge label={`${total} total`} variant="info" />
        </div>
        {loading
          ? (
            <div className="p-5 space-y-3">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="h-8 bg-slate-800 rounded animate-pulse" />
              ))}
            </div>
          )
          : recent.length === 0
            ? <p className="text-slate-500 text-sm p-5">No sync events recorded yet.</p>
            : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="border-b border-slate-700/60">
                      {['ID', 'Direction', 'Event', 'Source Status', 'Target Status', 'Outcome', 'Time'].map(h => (
                        <th key={h} className="text-left text-slate-400 font-medium px-4 py-2.5">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {recent.map(ev => (
                      <tr key={ev.id} className="hover:bg-slate-800/40 transition-colors">
                        <td className="px-4 py-2.5 text-slate-400">#{ev.id}</td>
                        <td className="px-4 py-2.5 text-slate-300 whitespace-nowrap">
                          {ev.sourcePlatform} → {ev.targetPlatform}
                        </td>
                        <td className="px-4 py-2.5 text-slate-400">{ev.eventType}</td>
                        <td className="px-4 py-2.5">
                          <Badge label={ev.sourceStatus || '—'} variant="info" />
                        </td>
                        <td className="px-4 py-2.5">
                          <Badge label={ev.targetStatus || '—'} variant="purple" />
                        </td>
                        <td className="px-4 py-2.5">
                          <Badge
                            label={ev.status}
                            variant={ev.status === 'SUCCESS' ? 'success' : ev.status === 'FAILED' ? 'error' : 'warning'}
                          />
                        </td>
                        <td className="px-4 py-2.5 text-slate-500 whitespace-nowrap">{fmt(ev.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
        }
      </div>
    </div>
  )
}
