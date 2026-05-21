import { useEffect, useState, useCallback } from 'react'
import { RefreshCw, Plus, Play, ToggleLeft, ToggleRight, Link2 } from 'lucide-react'
import Badge       from '../components/Badge'
import ErrorAlert  from '../components/ErrorAlert'
import { fetchMappings, enableMapping, disableMapping, triggerSync, createMapping } from '../api'

const fmt = (iso) => iso ? new Date(iso).toLocaleString() : '—'

function CreateMappingModal({ onClose, onCreate }) {
  const [form,    setForm]    = useState({ linearIssueId: '', clickupTaskId: '' })
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.linearIssueId.trim() || !form.clickupTaskId.trim()) {
      setError('Both fields are required.')
      return
    }
    setLoading(true)
    setError(null)
    try {
      const result = await createMapping(form)
      onCreate(result)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to create mapping.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-slate-900 rounded-2xl border border-slate-700 w-full max-w-md p-6">
        <div className="flex items-center gap-3 mb-5">
          <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-indigo-600/20">
            <Link2 size={16} className="text-indigo-400" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">Create Task Mapping</h3>
            <p className="text-xs text-slate-400">Link a Linear issue to a ClickUp task</p>
          </div>
        </div>

        {error && (
          <div className="mb-4 rounded-lg bg-rose-500/10 border border-rose-500/30 px-3 py-2 text-xs text-rose-300">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs text-slate-400 mb-1.5">Linear Issue ID (UUID)</label>
            <input
              value={form.linearIssueId}
              onChange={e => setForm(f => ({ ...f, linearIssueId: e.target.value }))}
              placeholder="e.g. e71d826b-0d60-4489-af42-0f8a2fbf2954"
              className="w-full px-3 py-2.5 text-xs bg-slate-800 border border-slate-700 rounded-lg
                         text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-mono"
            />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1.5">ClickUp Task ID</label>
            <input
              value={form.clickupTaskId}
              onChange={e => setForm(f => ({ ...f, clickupTaskId: e.target.value }))}
              placeholder="e.g. 868j9gkmf"
              className="w-full px-3 py-2.5 text-xs bg-slate-800 border border-slate-700 rounded-lg
                         text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-mono"
            />
          </div>
          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2.5 rounded-lg border border-slate-700 text-slate-400
                         hover:bg-slate-800 text-xs font-medium transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 py-2.5 rounded-lg bg-indigo-600 text-white hover:bg-indigo-500
                         text-xs font-medium transition-colors disabled:opacity-50"
            >
              {loading ? 'Creating…' : 'Create Mapping'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function TaskMappings() {
  const [mappings,   setMappings]   = useState([])
  const [loading,    setLoading]    = useState(true)
  const [error,      setError]      = useState(null)
  const [actionId,   setActionId]   = useState(null)
  const [actionErr,  setActionErr]  = useState({})
  const [showCreate, setShowCreate] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const d = await fetchMappings()
      setMappings(Array.isArray(d) ? d : d.mappings || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const doAction = async (id, fn, label) => {
    setActionId(id)
    setActionErr(prev => ({ ...prev, [id]: null }))
    try {
      await fn(id)
      await load()
    } catch (e) {
      setActionErr(prev => ({
        ...prev,
        [id]: `${label} failed: ${e.response?.data?.message || e.message}`
      }))
    } finally {
      setActionId(null)
    }
  }

  const active   = mappings.filter(m => m.active).length
  const inactive = mappings.filter(m => !m.active).length

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h2 className="text-lg font-semibold text-white">Task Mappings</h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {loading ? 'Loading…' : `${mappings.length} total · ${active} active · ${inactive} inactive`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-3 py-2 rounded-lg bg-indigo-600 text-white
                       hover:bg-indigo-500 text-xs font-medium transition-colors"
          >
            <Plus size={13} />
            New Mapping
          </button>
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
      </div>

      {error && <ErrorAlert message={error} onRetry={load} />}

      {/* Cards */}
      {loading
        ? (
          <div className="grid gap-4">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-28 rounded-xl bg-slate-900 border border-slate-700/60 animate-pulse" />
            ))}
          </div>
        )
        : mappings.length === 0
          ? (
            <div className="rounded-xl border border-slate-700/60 bg-slate-900 px-6 py-12 text-center">
              <Link2 size={32} className="text-slate-600 mx-auto mb-3" />
              <p className="text-sm text-slate-400">No task mappings yet.</p>
              <p className="text-xs text-slate-500 mt-1">Create one to start syncing.</p>
              <button
                onClick={() => setShowCreate(true)}
                className="mt-4 px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs hover:bg-indigo-500 transition-colors"
              >
                Create First Mapping
              </button>
            </div>
          )
          : (
            <div className="space-y-3">
              {mappings.map(m => (
                <div key={m.id}
                  className={`rounded-xl border bg-slate-900 p-5 transition-all
                    ${m.active ? 'border-slate-700/60' : 'border-slate-800/60 opacity-70'}`}
                >
                  <div className="flex items-start justify-between gap-4 flex-wrap">
                    {/* Left: IDs */}
                    <div className="space-y-2 min-w-0">
                      <div className="flex items-center gap-2">
                        <Badge label={`M-${m.id}`} variant="default" />
                        <Badge label={m.active ? 'Active' : 'Inactive'} variant={m.active ? 'active' : 'inactive'} />
                      </div>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-1">
                        <div>
                          <p className="text-xs text-slate-500 mb-0.5">Linear Issue ID</p>
                          <p className="font-mono text-xs text-slate-300 truncate" title={m.linearIssueId}>
                            {m.linearIssueId}
                          </p>
                        </div>
                        <div>
                          <p className="text-xs text-slate-500 mb-0.5">ClickUp Task ID</p>
                          <p className="font-mono text-xs text-slate-300">{m.clickupTaskId}</p>
                        </div>
                        {m.createdAt && (
                          <div>
                            <p className="text-xs text-slate-500 mb-0.5">Created</p>
                            <p className="text-xs text-slate-400">{fmt(m.createdAt)}</p>
                          </div>
                        )}
                        {m.lastSyncedAt && (
                          <div>
                            <p className="text-xs text-slate-500 mb-0.5">Last Synced</p>
                            <p className="text-xs text-slate-400">{fmt(m.lastSyncedAt)}</p>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Right: Actions */}
                    <div className="flex items-center gap-2 shrink-0">
                      {/* Trigger sync */}
                      <button
                        onClick={() => doAction(m.id, triggerSync, 'Sync')}
                        disabled={actionId === m.id || !m.active}
                        title={m.active ? 'Trigger manual sync' : 'Enable mapping first'}
                        className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-emerald-600/20 text-emerald-400
                                   border border-emerald-500/30 hover:bg-emerald-600/30 text-xs font-medium
                                   transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        <Play size={12} />
                        Sync
                      </button>

                      {/* Toggle enable/disable */}
                      <button
                        onClick={() =>
                          m.active
                            ? doAction(m.id, disableMapping, 'Disable')
                            : doAction(m.id, enableMapping,  'Enable')
                        }
                        disabled={actionId === m.id}
                        className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium
                                    border transition-colors disabled:opacity-50
                                    ${m.active
                                      ? 'bg-rose-500/10 text-rose-400 border-rose-500/30 hover:bg-rose-500/20'
                                      : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20'
                                    }`}
                      >
                        {m.active ? <ToggleRight size={13} /> : <ToggleLeft size={13} />}
                        {actionId === m.id ? '…' : m.active ? 'Disable' : 'Enable'}
                      </button>
                    </div>
                  </div>

                  {/* Action error */}
                  {actionErr[m.id] && (
                    <div className="mt-3 rounded-lg bg-rose-500/10 border border-rose-500/20 px-3 py-2 text-xs text-rose-300">
                      {actionErr[m.id]}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )
      }

      {/* Create modal */}
      {showCreate && (
        <CreateMappingModal
          onClose={() => setShowCreate(false)}
          onCreate={(newMapping) => {
            setMappings(prev => [newMapping, ...prev])
          }}
        />
      )}
    </div>
  )
}
