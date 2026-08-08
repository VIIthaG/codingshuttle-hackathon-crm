import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Users, ListTodo } from 'lucide-react'

const links = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/leads', label: 'Leads', icon: Users },
  { to: '/tasks', label: 'Tasks', icon: ListTodo },
]

export function Sidebar() {
  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-border bg-surface">
      <div className="border-b border-border px-5 py-5">
        <div className="text-lg font-semibold tracking-tight text-ink">FlowCRM</div>
        <p className="mt-0.5 text-xs text-muted">Sales pipeline workspace</p>
      </div>
      <nav className="flex flex-1 flex-col gap-1 p-3">
        {links.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              [
                'flex items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-brand-50 text-brand-700'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-ink',
              ].join(' ')
            }
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
