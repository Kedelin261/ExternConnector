import { useEffect, useState, useCallback } from 'react'
import {
  RefreshCw, CheckCircle, XCircle, AlertCircle,
  Database, Zap, Globe, Server, Clock, ShieldCheck
} from 'lucide-react'
import ErrorAlert from '../components/ErrorAlert'
import { fetchHealth, fetchApiStatus, fetchActuatorHealth } from '../api'

const fmt = (iso) => iso ? new Date(iso).toLocaleString() : '—'

function StatusDot({ ok, pulse }) {
  return (
    <span className={`inline-block w-2.5 h-2.5 rounded-full
      ${ok ? 'bg-emerald-400' : 'bg-rose-400'}
      ${pulse && ok ? 'pulse-dot' : ''}`}
    />
  )
}

function ServiceCard({ title, icon: Icon, status, details }) {
  const ok = status === true
  return (
    <div className={`rounded-xl border p-5 bg-slate-900
      ${ok ? 'border-emerald-500/20' : 'border-rose-500/20'}`}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className={`flex items-center justify-center w-9 h-9 rounded-lg
            ${ok ? 'bg-emerald-500/10' : 'bg-rose-500/10'}`}
          >
            <Icon size={16} className={ok ? 'text-emerald-400' : 'text-rose-400'} />
          </div>
          <p className="text-sm font-semibold text-white">{title}</p>
        </div>
        <div className="flex items-center gap-2">
          <StatusDot ok={ok} pulse />
          <span className={`text-xs font-medium ${ok ? 'text-emerald-400' : 'text-rose-400'}`}>
            {ok ? 'Operational' : 'Degraded'}
          </span>
        </div>
      </div>
      <div className="space-y-2">
        {details.map(({ label, value, mono }) => (
          <div key={label} className="flex justify-between items-center text-xs">
            <span className="text-slate-400">{label}</span>
            <span className={`${mono ? 'font-mono' : ''} text-slate-300`}>{value ?? '—'}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function SystemHealth() {
  const [health,    setHealth]    = useState(null)
  const [apiStatus, setApiStatus] = useState(null)
  const [actuator,  setActuator]  = useState(null)
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)
  const [lastCheck, setLastCheck] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [h, s, a] = await Promise.allSettled([
        fetchHealth(),
        fetchApiStatus(),
        fetchActuatorHealth(),
      ])
      if (h.status === 'fulfilled') setHealth(h.value)
      if (s.status === 'fulfilled') setApiStatus(s.value)
      if (a.status === 'fulfilled') setActuator(a.value)
      setLastCheck(new Date())
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  // Auto-refresh every 15s
  useEffect(() => {
    const t = setInterval(load, 15_000)
    return () => clearInterval(t)
  }, [load])

  const appUp  = health?.status === 'UP'
  const linOk  = apiStatus?.linear?.reachable
  const cupOk  = apiStatus?.clickup?.reachable
  const dbOk   = apiStatus?.database?.connected
  const allOk  = appUp && linOk && cupOk && dbOk

  // Actuator details
  const actDb = actuator?.components?.db
  const actDs = actuator?.components?.diskSpace

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h2 className="text-lg font-semibold text-white">System Health</h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {lastCheck ? `Last checked: ${lastCheck.toLocaleTimeString()} · auto-refresh every 15s` : 'Loading…'}
          </p>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-800 text-slate-300
                     hover:bg-slate-700 hover:text-white text-xs font-medium transition-colors disabled:opacity-50"
        >
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
          Check Now
        </button>
      </div>

      {error && <ErrorAlert message={error} onRetry={load} />}

      {/* Overall banner */}
      <div className={`rounded-xl border p-5 flex items-center gap-4
        ${allOk ? 'bg-emerald-500/5 border-emerald-500/30' : 'bg-amber-500/5 border-amber-500/30'}`}
      >
        {allOk
          ? <CheckCircle size={28} className="text-emerald-400 shrink-0" />
          : <AlertCircle size={28} className="text-amber-400 shrink-0" />
        }
        <div>
          <p className={`text-base font-bold ${allOk ? 'text-emerald-300' : 'text-amber-300'}`}>
            {loading ? 'Checking system health…' : allOk ? 'All Systems Operational' : 'Some Services Degraded'}
          </p>
          <p className="text-xs text-slate-400 mt-0.5">
            ExternConnector Linear↔ClickUp Sync — Production Environment
          </p>
        </div>
        {!loading && (
          <div className="ml-auto text-right">
            <p className="text-xs text-slate-500">Active Locks</p>
            <p className="text-xl font-bold text-white">{health?.activeLocks ?? '—'}</p>
          </div>
        )}
      </div>

      {/* Service cards grid */}
      {loading
        ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-44 rounded-xl bg-slate-900 border border-slate-700/60 animate-pulse" />
            ))}
          </div>
        )
        : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* App Service */}
            <ServiceCard
              title="Application"
              icon={Server}
              status={appUp}
              details={[
                { label: 'Status',       value: health?.status || '—' },
                { label: 'Service',      value: health?.service || 'linear-clickup-sync' },
                { label: 'Active Locks', value: health?.activeLocks ?? '—' },
                { label: 'Actuator',     value: actuator?.status || '—' },
              ]}
            />

            {/* Linear API */}
            <ServiceCard
              title="Linear API"
              icon={Zap}
              status={linOk}
              details={[
                { label: 'Reachable',    value: linOk ? 'Yes' : 'No' },
                { label: 'Latency',      value: apiStatus?.linear?.latencyMs != null ? `${apiStatus.linear.latencyMs} ms` : '—' },
                { label: 'Endpoint',     value: 'api.linear.app/graphql', mono: true },
                { label: 'Auth',         value: linOk ? 'Valid token' : 'Check LINEAR_API_KEY' },
              ]}
            />

            {/* ClickUp API */}
            <ServiceCard
              title="ClickUp API"
              icon={Globe}
              status={cupOk}
              details={[
                { label: 'Reachable',    value: cupOk ? 'Yes' : 'No' },
                { label: 'Latency',      value: apiStatus?.clickup?.latencyMs != null ? `${apiStatus.clickup.latencyMs} ms` : '—' },
                { label: 'Endpoint',     value: 'api.clickup.com/api/v2', mono: true },
                { label: 'Auth',         value: cupOk ? 'Valid token' : 'Check CLICKUP_API_KEY' },
              ]}
            />

            {/* Database */}
            <ServiceCard
              title="PostgreSQL Database"
              icon={Database}
              status={dbOk}
              details={[
                { label: 'Connected',      value: dbOk ? 'Yes' : 'No' },
                { label: 'Actuator DB',    value: actDb?.status || '—' },
                { label: 'DB Product',     value: actDb?.details?.database || '—' },
                { label: 'Pool',           value: 'HikariCP (10 max)' },
              ]}
            />
          </div>
        )
      }

      {/* Flyway / migrations */}
      {actuator?.components?.flyway && (
        <div className="rounded-xl border border-slate-700/60 bg-slate-900 p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-sky-500/10">
              <ShieldCheck size={16} className="text-sky-400" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Flyway Migrations</p>
              <p className="text-xs text-slate-400">Database schema version control</p>
            </div>
            <span className={`ml-auto text-xs font-medium px-2 py-1 rounded
              ${actuator.components.flyway.status === 'UP' ? 'bg-emerald-500/15 text-emerald-400' : 'bg-rose-500/15 text-rose-400'}`}
            >
              {actuator.components.flyway.status}
            </span>
          </div>
          {actuator.components.flyway.details?.FlywayDataSource?.migrations && (
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-slate-700/60">
                    {['Version', 'Description', 'Type', 'Script', 'State'].map(h => (
                      <th key={h} className="text-left text-slate-400 font-medium px-3 py-2">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {actuator.components.flyway.details.FlywayDataSource.migrations.map(m => (
                    <tr key={m.version} className="hover:bg-slate-800/40">
                      <td className="px-3 py-2 font-mono text-indigo-400">{m.version}</td>
                      <td className="px-3 py-2 text-slate-300">{m.description}</td>
                      <td className="px-3 py-2 text-slate-400">{m.type}</td>
                      <td className="px-3 py-2 font-mono text-slate-500 text-xs">{m.script}</td>
                      <td className="px-3 py-2">
                        <span className={`text-xs font-medium
                          ${m.state === 'SUCCESS' ? 'text-emerald-400' : 'text-amber-400'}`}>
                          {m.state}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Disk space (from actuator) */}
      {actDs && (
        <div className="rounded-xl border border-slate-700/60 bg-slate-900 p-5">
          <div className="flex items-center gap-3 mb-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-amber-500/10">
              <Clock size={16} className="text-amber-400" />
            </div>
            <p className="text-sm font-semibold text-white">Disk Space</p>
            <span className={`ml-auto text-xs font-medium px-2 py-1 rounded
              ${actDs.status === 'UP' ? 'bg-emerald-500/15 text-emerald-400' : 'bg-amber-500/15 text-amber-400'}`}>
              {actDs.status}
            </span>
          </div>
          {actDs.details && (
            <div className="grid grid-cols-3 gap-4 text-xs">
              {[
                { label: 'Free Space',  value: actDs.details.free  ? `${(actDs.details.free  / 1e9).toFixed(1)} GB` : '—' },
                { label: 'Total Space', value: actDs.details.total ? `${(actDs.details.total / 1e9).toFixed(1)} GB` : '—' },
                { label: 'Threshold',   value: actDs.details.threshold ? `${(actDs.details.threshold / 1e9).toFixed(1)} GB` : '—' },
              ].map(({ label, value }) => (
                <div key={label}>
                  <p className="text-slate-500">{label}</p>
                  <p className="text-slate-200 font-semibold mt-0.5">{value}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
