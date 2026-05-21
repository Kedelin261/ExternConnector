import { AlertTriangle, RefreshCw } from 'lucide-react'

export default function ErrorBox({ message, onRetry }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-4">
      <div className="flex items-center justify-center w-14 h-14 rounded-full bg-rose-500/10 border border-rose-500/30">
        <AlertTriangle size={24} className="text-rose-400" />
      </div>
      <div className="text-center">
        <p className="text-sm font-medium text-slate-300">{message || 'Failed to load data'}</p>
        <p className="text-xs text-slate-500 mt-1">Check backend connection at localhost:8080</p>
      </div>
      {onRetry && (
        <button
          onClick={onRetry}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition-colors"
        >
          <RefreshCw size={13} />
          Retry
        </button>
      )}
    </div>
  )
}
