import { BrowserRouter, Routes, Route, NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, RefreshCw, Webhook, Link2, Activity,
  Zap, Menu, X, ExternalLink
} from 'lucide-react'
import { useState } from 'react'

import Dashboard from './pages/Dashboard'
import SyncEvents from './pages/SyncEvents'
import WebhookLogs from './pages/WebhookLogs'
import TaskMappings from './pages/TaskMappings'
import SystemHealth from './pages/SystemHealth'

const NAV = [
  { to: '/',             label: 'Dashboard',     Icon: LayoutDashboard },
  { to: '/sync-events',  label: 'Sync Events',   Icon: RefreshCw },
  { to: '/webhook-logs', label: 'Webhook Logs',  Icon: Webhook },
  { to: '/mappings',     label: 'Task Mappings', Icon: Link2 },
  { to: '/health',       label: 'System Health', Icon: Activity },
]

function Sidebar({ open, onClose }) {
  return (
    <>
      {/* Overlay on mobile */}
      {open && (
        <div
          className="fixed inset-0 bg-black/60 z-20 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={`
          fixed top-0 left-0 h-full w-64 z-30 flex flex-col
          bg-slate-900 border-r border-slate-700/60
          transform transition-transform duration-200
          ${open ? 'translate-x-0' : '-translate-x-full'}
          lg:translate-x-0 lg:static lg:z-auto
        `}
      >
        {/* Logo */}
        <div className="flex items-center gap-3 px-5 py-5 border-b border-slate-700/60">
          <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-indigo-600">
            <Zap size={18} className="text-white" />
          </div>
          <div>
            <p className="text-sm font-bold text-white">ExternConnector</p>
            <p className="text-xs text-slate-400">Linear ↔ ClickUp Sync</p>
          </div>
          <button onClick={onClose} className="ml-auto lg:hidden text-slate-400 hover:text-white">
            <X size={18} />
          </button>
        </div>

        {/* Nav */}
        <nav className="flex-1 py-4 px-3 space-y-1">
          {NAV.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              onClick={() => { if (window.innerWidth < 1024) onClose() }}
              className={({ isActive }) => `
                flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium
                transition-colors duration-150
                ${isActive
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-400 hover:bg-slate-800 hover:text-white'}
              `}
            >
              <Icon size={16} />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="px-4 py-4 border-t border-slate-700/60">
          <a
            href="https://github.com/Kedelin261/ExternConnector"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 text-xs text-slate-500 hover:text-slate-300 transition-colors"
          >
            <ExternalLink size={12} />
            Kedelin261/ExternConnector
          </a>
        </div>
      </aside>
    </>
  )
}

function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()

  const current = NAV.find(n =>
    n.to === '/'
      ? location.pathname === '/'
      : location.pathname.startsWith(n.to)
  )

  return (
    <div className="flex h-screen overflow-hidden bg-slate-950">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top bar */}
        <header className="flex items-center gap-4 px-5 py-3 bg-slate-900 border-b border-slate-700/60 shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="lg:hidden text-slate-400 hover:text-white"
          >
            <Menu size={20} />
          </button>
          <h1 className="text-sm font-semibold text-white">
            {current?.label || 'ExternConnector'}
          </h1>
          <div className="ml-auto flex items-center gap-2">
            <span className="pulse-dot inline-block w-2 h-2 rounded-full bg-emerald-400" />
            <span className="text-xs text-slate-400">Live</span>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto p-5">
          <Routes>
            <Route path="/"             element={<Dashboard />} />
            <Route path="/sync-events"  element={<SyncEvents />} />
            <Route path="/webhook-logs" element={<WebhookLogs />} />
            <Route path="/mappings"     element={<TaskMappings />} />
            <Route path="/health"       element={<SystemHealth />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Layout />
    </BrowserRouter>
  )
}
