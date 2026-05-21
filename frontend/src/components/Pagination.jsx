import { ChevronLeft, ChevronRight } from 'lucide-react'

export default function Pagination({ page, totalPages, onPageChange }) {
  if (totalPages <= 1) return null

  const pages = []
  const start = Math.max(0, page - 2)
  const end   = Math.min(totalPages - 1, page + 2)
  for (let i = start; i <= end; i++) pages.push(i)

  const btn = (label, disabled, onClick, active = false) => (
    <button
      key={label}
      onClick={onClick}
      disabled={disabled}
      className={`
        px-3 py-1.5 rounded text-xs font-medium transition-colors
        ${active
          ? 'bg-indigo-600 text-white'
          : 'bg-slate-800 text-slate-300 hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed'}
      `}
    >
      {label}
    </button>
  )

  return (
    <div className="flex items-center gap-1 justify-end mt-4">
      {btn(<ChevronLeft size={14} />, page === 0, () => onPageChange(page - 1))}
      {start > 0 && <>{btn('1', false, () => onPageChange(0))} <span className="text-slate-500 text-xs px-1">…</span></>}
      {pages.map(p => btn(p + 1, false, () => onPageChange(p), p === page))}
      {end < totalPages - 1 && <><span className="text-slate-500 text-xs px-1">…</span>{btn(totalPages, false, () => onPageChange(totalPages - 1))}</>}
      {btn(<ChevronRight size={14} />, page >= totalPages - 1, () => onPageChange(page + 1))}
    </div>
  )
}
