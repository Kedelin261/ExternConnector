export default function StatCard({ label, value, sub, icon: Icon, color = 'indigo', loading }) {
  const colors = {
    indigo:  { bg: 'bg-indigo-500/10',  icon: 'text-indigo-400',  border: 'border-indigo-500/20' },
    emerald: { bg: 'bg-emerald-500/10', icon: 'text-emerald-400', border: 'border-emerald-500/20' },
    amber:   { bg: 'bg-amber-500/10',   icon: 'text-amber-400',   border: 'border-amber-500/20' },
    rose:    { bg: 'bg-rose-500/10',    icon: 'text-rose-400',    border: 'border-rose-500/20' },
    sky:     { bg: 'bg-sky-500/10',     icon: 'text-sky-400',     border: 'border-sky-500/20' },
  }
  const c = colors[color] || colors.indigo

  return (
    <div className={`rounded-xl border ${c.border} bg-slate-900 p-5 flex items-start gap-4`}>
      {Icon && (
        <div className={`flex items-center justify-center w-10 h-10 rounded-lg ${c.bg} shrink-0`}>
          <Icon size={20} className={c.icon} />
        </div>
      )}
      <div className="min-w-0">
        <p className="text-xs text-slate-400 mb-1 truncate">{label}</p>
        {loading
          ? <div className="h-7 w-20 bg-slate-700 rounded animate-pulse" />
          : <p className="text-2xl font-bold text-white leading-none">{value ?? '—'}</p>
        }
        {sub && <p className="text-xs text-slate-500 mt-1 truncate">{sub}</p>}
      </div>
    </div>
  )
}
