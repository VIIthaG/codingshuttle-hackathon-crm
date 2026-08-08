import { LogOut } from 'lucide-react'
import { useAuth } from '../auth/useAuth'

type HeaderProps = {
  title: string
}

export function Header({ title }: HeaderProps) {
  const { user, logout } = useAuth()

  return (
    <header className="flex h-16 items-center justify-between border-b border-border bg-surface px-6">
      <h1 className="text-lg font-semibold text-ink">{title}</h1>
      <div className="flex items-center gap-3">
        <div className="text-right">
          <div className="text-sm font-medium text-ink">{user?.fullName}</div>
          <div className="text-xs text-muted">{user?.email}</div>
        </div>
        <span className="rounded-full border border-border bg-canvas px-2.5 py-1 text-xs font-semibold uppercase tracking-wide text-slate-600">
          {user?.role === 'ADMIN' ? 'Admin' : 'Sales Rep'}
        </span>
        <button
          type="button"
          onClick={logout}
          className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <LogOut className="h-4 w-4" aria-hidden />
          Logout
        </button>
      </div>
    </header>
  )
}
