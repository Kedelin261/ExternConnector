import { useEffect, useState, useCallback } from 'react'
import { RefreshCw, Search, Filter } from 'lucide-react'
import Badge       from '../components/Badge'
import Pagination  from '../components/Pagination'
import ErrorAlert  from '../components/ErrorAlert'
import { fetchSyncEvents } from '../api'

const fmt = (iso) => iso ? new Date(iso).toLocaleString() : '—'

const SHORT = (str, n = 12) =>
  str ? (str.length > n ? str.slice(0, n) + '…' : str) : '—'

export default function SyncEvents() {
  const [data,    setData]    = useState(null)
  const [page,    setPage]    = useState(0)
  const [size]                = useState(20)
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState(null)
  const [filter,  setFilter]  = useState('')

  const load = useCallback(async (p = page) => {
    setLoading(true)
    setError(null)
    try {
      const d = await fetchSyncEvents(p, size)
      setData(d)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [page, size])

  useEffect(() => { load(page) }, [page])

  const events = data?.events || []

  const filtered = filter.trim()
    ? events.filter(ev =>
        (ev.sourcePlatform + ev.targetPlatform + ev.eventType + (ev.sourceStatus || '') +
          (ev.targetStatus || '') + ev.status + (ev.linearIssueId || '') + (ev.clickupTaskId || ''))
          .toLowerCase().includes(filter.toLowerCase())
      )
    : events

  const badgeVariant = (s) =>
    s === 'SUCCESS' ? 'success' : s === 'FAILED' ? 'error' : 'warning'

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h2 className="text-lg font-semibold text-white">Sync Events</h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {data ? `${data.totalElements} total events · Page ${data.page + 1} of ${data.totalPages}` : 'Loading…'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Search */}
          <div className="relative">
            <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              value={filter}
              onChange={e => setFilter(e.target.value)}
              placeholder="Filter events…"
              className="pl-8 pr-3 py-2 text-xs bg-slate-800 border border-slate-700 rounded-lg
                         text-slate-300 placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-48"
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

      {/* Table */}
      <div className="rounded-xl border border-slate-700/60 bg-slate-900 overflow-hidden">
        {/* Summary bar */}
        {data && (
          <div className="flex items-center gap-4 px-5 py-3 border-b border-slate-700/60 bg-slate-800/40 text-xs text-slate-400">
            <span className="text-emerald-400 font-medium">
              ✓ {events.filter(e => e.status === 'SUCCESS').length} success
            </span>
            <span className="text-rose-400 font-medium">
              ✗ {events.filter(e => e.status === 'FAILED').length} failed
            </span>
            <span className="text-amber-400 font-medium">
              ~ {events.filter(e => e.status !== 'SUCCESS' && e.status !== 'FAILED').length} other
            </span>
            {filter && (
              <span className="ml-auto text-indigo-400">
                <Filter size={12} className="inline mr-1" />
                Showing {filtered.length} filtered
              </span>
            )}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-slate-700/60">
                {['#', 'Direction', 'Mapping', 'Event Type', 'Source Status', 'Target Status',
                  'Linear ID', 'ClickUp ID', 'Outcome', 'Idempotency Key', 'Created At'].map(h => (
                  <th key={h} className="text-left text-slate-400 font-medium px-4 py-3 whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {loading
                ? [...Array(8)].map((_, i) => (
                    <tr key={i}>
                      {[...Array(11)].map((_, j) => (
                        <td key={j} className="px-4 py-3">
                          <div className="h-4 bg-slate-800 rounded animate-pulse" />
                        </td>
                      ))}
                    </tr>
                  ))
                : filtered.length === 0
                  ? (
                    <tr>
                      <td colSpan={11} className="px-4 py-10 text-center text-slate-500">
                        {filter ? 'No events match your filter.' : 'No sync events found.'}
                      </td>
                    </tr>
                  )
                  : filtered.map(ev => (
                    <tr key={ev.id} className="hover:bg-slate-800/40 transition-colors group">
                      <td className="px-4 py-3 text-slate-500 font-mono">#{ev.id}</td>
                      <td className="px-4 py-3 text-slate-300 whitespace-nowrap">
                        <span className="text-slate-500">{ev.sourcePlatform}</span>
                        {' → '}
                        <span className="text-white">{ev.targetPlatform}</span>
                      </td>
                      <td className="px-4 py-3 text-slate-400 font-mono">
                        {ev.taskMappingId ? `M-${ev.taskMappingId}` : '—'}
                      </td>
                      <td className="px-4 py-3 text-slate-400">{ev.eventType}</td>
                      <td className="px-4 py-3">
                        {ev.sourceStatus
                          ? <Badge label={ev.sourceStatus} variant="info" />
                          : <span className="text-slate-600">—</span>}
                      </td>
                      <td className="px-4 py-3">
                        {ev.targetStatus
                          ? <Badge label={ev.targetStatus} variant="purple" />
                          : <span className="text-slate-600">—</span>}
                      </td>
                      <td className="px-4 py-3 font-mono text-slate-500" title={ev.linearIssueId || ''}>
                        {SHORT(ev.linearIssueId, 8)}
                      </td>
                      <td className="px-4 py-3 font-mono text-slate-500">
                        {ev.clickupTaskId || '—'}
                      </td>
                      <td className="px-4 py-3">
                        <Badge label={ev.status} variant={badgeVariant(ev.status)} />
                      </td>
                      <td className="px-4 py-3 font-mono text-slate-600 max-w-[180px] truncate" title={ev.idempotencyKey || ''}>
                        {SHORT(ev.idempotencyKey, 22)}
                      </td>
                      <td className="px-4 py-3 text-slate-500 whitespace-nowrap">{fmt(ev.createdAt)}</td>
                    </tr>
                  ))
              }
            </tbody>
          </table>
        </div>

        {/* Error message column */}
        {!loading && filtered.some(e => e.errorMessage) && (
          <div className="border-t border-slate-700/60 px-5 py-4">
            <p className="text-xs font-medium text-slate-400 mb-3">Error Details</p>
            <div className="space-y-2">
              {filtered.filter(e => e.errorMessage).map(e => (
                <div key={e.id}
                  className="rounded-lg bg-rose-500/10 border border-rose-500/20 px-3 py-2 text-xs">
                  <span className="text-rose-400 font-mono mr-2">#{e.id}</span>
                  <span className="text-rose-300">{e.errorMessage}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        onPageChange={p => { setPage(p); load(p) }}
      />
    </div>
  )
}
