import { useEffect, useState, useCallback } from 'react'
import { RefreshCw, Search, Shield, ShieldOff } from 'lucide-react'
import Badge       from '../components/Badge'
import Pagination  from '../components/Pagination'
import ErrorAlert  from '../components/ErrorAlert'
import { fetchWebhookLogs } from '../api'

const fmt  = (iso) => iso ? new Date(iso).toLocaleString() : '—'
const fmtR = (iso) => {
  if (!iso) return '—'
  const diff = Math.round((Date.now() - new Date(iso)) / 1000)
  if (diff < 60)   return `${diff}s ago`
  if (diff < 3600) return `${Math.floor(diff/60)}m ago`
  return fmt(iso)
}

export default function WebhookLogs() {
  const [data,    setData]    = useState(null)
  const [page,    setPage]    = useState(0)
  const [size]                = useState(20)
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState(null)
  const [filter,  setFilter]  = useState('')
  const [sigFilter, setSigFilter] = useState('all') // 'all' | 'valid' | 'invalid'

  const load = useCallback(async (p = page) => {
    setLoading(true)
    setError(null)
    try {
      const d = await fetchWebhookLogs(p, size)
      setData(d)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [page, size])

  useEffect(() => { load(page) }, [page])

  const logs = data?.logs || []

  const filtered = logs.filter(l => {
    const textMatch = !filter.trim() ||
      (l.source + l.eventType + (l.eventId || '')).toLowerCase().includes(filter.toLowerCase())
    const sigMatch =
      sigFilter === 'all' ? true :
      sigFilter === 'valid' ? l.signatureValid :
      !l.signatureValid
    return textMatch && sigMatch
  })

  const validCount   = logs.filter(l => l.signatureValid).length
  const invalidCount = logs.filter(l => !l.signatureValid).length

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h2 className="text-lg font-semibold text-white">Webhook Logs</h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {data
              ? `${data.totalElements} total · ${validCount} valid sig · ${invalidCount} invalid`
              : 'Loading…'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Sig filter */}
          <div className="flex rounded-lg overflow-hidden border border-slate-700 text-xs">
            {['all', 'valid', 'invalid'].map(v => (
              <button
                key={v}
                onClick={() => setSigFilter(v)}
                className={`px-3 py-2 capitalize transition-colors ${
                  sigFilter === v
                    ? 'bg-indigo-600 text-white'
                    : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                }`}
              >
                {v}
              </button>
            ))}
          </div>
          {/* Search */}
          <div className="relative">
            <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              value={filter}
              onChange={e => setFilter(e.target.value)}
              placeholder="Filter logs…"
              className="pl-8 pr-3 py-2 text-xs bg-slate-800 border border-slate-700 rounded-lg
                         text-slate-300 placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-44"
            />
          </div>
          <button
            onClick={() => load(page)}
            disabled={loading}
            className="flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-800 text-slate-300
                       hover:bg-slate-700 hover:text-white text-xs font-medium transition-colors disabled:opacity-50"
          >
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
            Refresh
          </button>
        </div>
      </div>

      {error && <ErrorAlert message={error} onRetry={() => load(page)} />}

      {/* Stats strip */}
      {data && (
        <div className="grid grid-cols-4 gap-3">
          {[
            { label: 'Total Received',     value: data.totalElements,    color: 'text-white' },
            { label: 'Valid Signatures',   value: validCount,             color: 'text-emerald-400' },
            { label: 'Invalid Signatures', value: invalidCount,           color: 'text-rose-400' },
            { label: 'Processed',          value: logs.filter(l => l.processed).length, color: 'text-sky-400' },
          ].map(({ label, value, color }) => (
            <div key={label} className="rounded-xl border border-slate-700/60 bg-slate-900 px-4 py-3">
              <p className="text-xs text-slate-500">{label}</p>
              <p className={`text-xl font-bold mt-0.5 ${color}`}>{value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Table */}
      <div className="rounded-xl border border-slate-700/60 bg-slate-900 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-slate-700/60">
                {['#ID', 'Source', 'Event Type', 'Event ID', 'Signature', 'Processed', 'Received'].map(h => (
                  <th key={h} className="text-left text-slate-400 font-medium px-4 py-3 whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {loading
                ? [...Array(10)].map((_, i) => (
                    <tr key={i}>
                      {[...Array(7)].map((_, j) => (
                        <td key={j} className="px-4 py-3">
                          <div className="h-4 bg-slate-800 rounded animate-pulse" />
                        </td>
                      ))}
                    </tr>
                  ))
                : filtered.length === 0
                  ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-10 text-center text-slate-500">
                        {filter || sigFilter !== 'all' ? 'No logs match your filters.' : 'No webhook logs found.'}
                      </td>
                    </tr>
                  )
                  : filtered.map(log => (
                    <tr key={log.id} className="hover:bg-slate-800/40 transition-colors">
                      <td className="px-4 py-3 text-slate-500 font-mono">#{log.id}</td>
                      <td className="px-4 py-3">
                        <Badge
                          label={log.source}
                          variant={log.source === 'LINEAR' ? 'purple' : 'info'}
                        />
                      </td>
                      <td className="px-4 py-3 text-slate-300">{log.eventType}</td>
                      <td className="px-4 py-3 font-mono text-slate-500 max-w-[140px] truncate" title={log.eventId || ''}>
                        {log.eventId ? log.eventId.slice(0, 16) + '…' : '—'}
                      </td>
                      <td className="px-4 py-3">
                        {log.signatureValid
                          ? (
                            <span className="flex items-center gap-1 text-emerald-400">
                              <Shield size={12} /> Valid
                            </span>
                          )
                          : (
                            <span className="flex items-center gap-1 text-rose-400">
                              <ShieldOff size={12} /> Invalid
                            </span>
                          )
                        }
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          label={log.processed ? 'Yes' : 'No'}
                          variant={log.processed ? 'success' : 'warning'}
                        />
                      </td>
                      <td className="px-4 py-3 text-slate-500 whitespace-nowrap"
                          title={fmt(log.receivedAt)}>
                        {fmtR(log.receivedAt)}
                      </td>
                    </tr>
                  ))
              }
            </tbody>
          </table>
        </div>
      </div>

      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        onPageChange={p => { setPage(p); load(p) }}
      />
    </div>
  )
}
