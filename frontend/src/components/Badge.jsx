export default function Badge({ label, variant = 'default' }) {
  const variants = {
    success:  'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30',
    error:    'bg-rose-500/15    text-rose-400    border border-rose-500/30',
    warning:  'bg-amber-500/15   text-amber-400   border border-amber-500/30',
    info:     'bg-sky-500/15     text-sky-400     border border-sky-500/30',
    purple:   'bg-purple-500/15  text-purple-400  border border-purple-500/30',
    default:  'bg-slate-700/50   text-slate-300   border border-slate-600/50',
    active:   'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30',
    inactive: 'bg-slate-600/30   text-slate-400   border border-slate-600/40',
  }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${variants[variant] || variants.default}`}>
      {label}
    </span>
  )
}
