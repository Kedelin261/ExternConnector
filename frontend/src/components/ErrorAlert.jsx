import { AlertCircle, RefreshCw } from 'lucide-react'

export default function ErrorAlert({ message, onRetry }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3">
      <AlertCircle size={16} className="text-rose-400 shrink-0" />
      <p className="text-sm text-rose-300 flex-1">{message || 'Failed to load data.'}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="flex items-center gap-1 text-xs text-rose-400 hover:text-rose-200 transition-colors"
        >
          <RefreshCw size={12} /> Retry
        </button>
      )}
    </div>
  )
}
