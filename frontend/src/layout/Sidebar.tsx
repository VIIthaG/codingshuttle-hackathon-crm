import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Users, ListTodo, Building2, UserRound, Handshake, X, Inbox, CalendarDays, Phone, Video } from 'lucide-react'

const links = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/workqueue', label: 'Workqueue', icon: Inbox },
  { to: '/calendar', label: 'Calendar', icon: CalendarDays },
  { to: '/accounts', label: 'Accounts', icon: Building2 },
  { to: '/contacts', label: 'Contacts', icon: UserRound },
  { to: '/deals', label: 'Deals', icon: Handshake },
  { to: '/leads', label: 'Leads', icon: Users },
  { to: '/tasks', label: 'Tasks', icon: ListTodo },
  { to: '/meetings', label: 'Meetings', icon: Video },
  { to: '/calls', label: 'Calls', icon: Phone },
]

type SidebarProps = {
  open: boolean
  onClose: () => void
}

export function Sidebar({ open, onClose }: SidebarProps) {
  return (
    <>
      {/* Mobile overlay */}
      <div
        className={[
          'fixed inset-0 z-40 bg-slate-900/40 transition-opacity md:hidden',
          open ? 'opacity-100' : 'pointer-events-none opacity-0',
        ].join(' ')}
        onClick={onClose}
        aria-hidden={!open}
      />

      <aside
        id="app-sidebar"
        className={[
          'fixed inset-y-0 left-0 z-50 flex w-64 max-w-[85vw] flex-col border-r border-border bg-surface transition-transform md:static md:z-0 md:w-60 md:max-w-none md:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        ].join(' ')}
      >
        <div className="flex items-start justify-between border-b border-border px-5 py-5">
          <div>
            <div className="text-lg font-semibold tracking-tight text-ink">FlowCRM</div>
            <p className="mt-0.5 text-xs text-muted">Sales pipeline workspace</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-muted hover:bg-slate-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 md:hidden"
            aria-label="Close navigation"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>
        <nav className="flex flex-1 flex-col gap-1 p-3" aria-label="Primary">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={onClose}
              className={({ isActive }) =>
                [
                  'flex items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500',
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
    </>
  )
}
