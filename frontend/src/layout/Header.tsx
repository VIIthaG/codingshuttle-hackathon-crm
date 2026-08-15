import { LogOut, Menu } from 'lucide-react'
import { useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { GlobalSearch } from './GlobalSearch'
import { NotificationBell } from './NotificationBell'
import { QuickCreateMenu } from './QuickCreateMenu'

type HeaderProps = {
  title: string
  onMenuClick: () => void
}

export function Header({ title, onMenuClick }: HeaderProps) {
  const { user, logout } = useAuth()
  const [toast, setToast] = useState<string | null>(null)

  function showToast(message: string) {
    setToast(message)
    window.setTimeout(() => setToast(null), 2500)
  }

  return (
    <header className="flex h-14 items-center gap-2 border-b border-border bg-surface px-3 sm:h-16 sm:gap-3 sm:px-6">
      <div className="flex min-w-0 shrink-0 items-center gap-2">
        <button
          type="button"
          onClick={onMenuClick}
          className="inline-flex rounded-lg border border-border bg-white p-2 text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 md:hidden"
          aria-label="Open navigation"
          aria-controls="app-sidebar"
        >
          <Menu className="h-5 w-5" aria-hidden />
        </button>
        <h1 className="hidden truncate text-base font-semibold text-ink sm:block sm:text-lg">{title}</h1>
      </div>
      <GlobalSearch />
      <div className="flex shrink-0 items-center gap-2 sm:gap-3">
        <QuickCreateMenu onCreated={showToast} />
        <NotificationBell />
        <div className="hidden text-right lg:block">
          <div className="text-sm font-medium text-ink">{user?.fullName}</div>
          <div className="max-w-[14rem] truncate text-xs text-muted">{user?.email}</div>
        </div>
        <span
          className="hidden rounded-full border border-border bg-canvas px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-600 sm:inline sm:px-2.5 sm:text-xs"
          title={user?.role === 'ADMIN' ? 'Admin' : 'Sales Rep'}
        >
          {user?.role === 'ADMIN' ? 'Admin' : 'Sales Rep'}
        </span>
        <button
          type="button"
          onClick={logout}
          className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-white px-2.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 sm:px-3"
          aria-label="Logout"
        >
          <LogOut className="h-4 w-4" aria-hidden />
          <span className="hidden sm:inline">Logout</span>
        </button>
      </div>
      {toast ? (
        <div className="fixed bottom-4 right-4 z-50 rounded-lg bg-ink px-3 py-2 text-sm text-white shadow-lg">{toast}</div>
      ) : null}
    </header>
  )
}
