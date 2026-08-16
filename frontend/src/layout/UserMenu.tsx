import { useEffect, useRef, useState } from 'react'
import { LogOut, Moon, Sun } from 'lucide-react'
import { useAuth } from '../auth/useAuth'
import { useTheme } from '../theme/useTheme'
import { initialsFromName } from '../utils/initials'
import { Avatar } from '../components/ui/Feedback'

export function UserMenu() {
  const { user, logout } = useAuth()
  const { theme, setTheme } = useTheme()
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const name = user?.fullName ?? 'User'
  const initials = initialsFromName(name)

  useEffect(() => {
    if (!open) return
    function onClick(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    window.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        className="inline-flex items-center gap-2 rounded-lg p-0.5 hover:bg-canvas"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
        title="Account"
        onClick={() => setOpen((v) => !v)}
      >
        <Avatar name={name} />
        <span className="sr-only">{initials}</span>
      </button>
      {open ? (
        <div
          role="menu"
          className="dropdown-panel absolute right-0 z-50 mt-2 w-[min(calc(100vw-1.5rem),18rem)] origin-top-right animate-[fadeIn_120ms_ease-out]"
        >
          <div className="flex items-start gap-3 border-b border-border px-3 py-3">
            <Avatar name={name} size="lg" />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-ink">{name}</p>
              <p className="truncate text-xs text-muted">{user?.email}</p>
              <span className="badge badge-info mt-1.5">
                {user?.role === 'ADMIN' ? 'Admin' : 'Sales Rep'}
              </span>
            </div>
          </div>
          <div className="px-3 py-2">
            <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wide text-muted">Theme</p>
            <div className="grid grid-cols-2 gap-1 rounded-lg border border-border p-1">
              <button
                type="button"
                className={`btn btn-sm ${theme === 'light' ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setTheme('light')}
              >
                <Sun className="h-3.5 w-3.5" aria-hidden />
                Light
              </button>
              <button
                type="button"
                className={`btn btn-sm ${theme === 'dark' ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setTheme('dark')}
              >
                <Moon className="h-3.5 w-3.5" aria-hidden />
                Dark
              </button>
            </div>
          </div>
          <button
            type="button"
            role="menuitem"
            className="flex w-full items-center gap-2 border-t border-border px-3 py-2.5 text-left text-sm text-ink hover:bg-canvas"
            onClick={logout}
          >
            <LogOut className="h-4 w-4 text-muted" aria-hidden />
            Logout
          </button>
        </div>
      ) : null}
    </div>
  )
}
