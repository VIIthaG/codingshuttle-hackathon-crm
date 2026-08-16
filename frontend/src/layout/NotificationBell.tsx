import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell } from 'lucide-react'
import {
  getUnreadNotificationCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notifications'
import type { NotificationItem } from '../types/notification'
import { searchPath, type SearchResultType } from '../types/search'
import { formatApiError } from '../utils/errors'

function relativeTime(iso: string): string {
  const delta = Date.now() - new Date(iso).getTime()
  const minutes = Math.max(0, Math.round(delta / 60000))
  if (minutes < 1) return 'now'
  if (minutes < 60) return `${minutes}m`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h`
  return `${Math.round(hours / 24)}d`
}

export function NotificationBell() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [count, setCount] = useState(0)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    function load() {
      void getUnreadNotificationCount()
        .then((res) => {
          if (!cancelled) setCount(res.count)
        })
        .catch(() => {})
    }
    load()
    const id = window.setInterval(load, 45000)
    return () => {
      cancelled = true
      window.clearInterval(id)
    }
  }, [])

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

  useEffect(() => {
    if (!open) return
    let cancelled = false
    void listNotifications({ size: 20 })
      .then((page) => {
        if (cancelled) return
        setItems(page.content)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(formatApiError(err, 'Failed to load notifications'))
      })
    return () => {
      cancelled = true
    }
  }, [open])

  async function openItem(item: NotificationItem) {
    if (!item.readAt) {
      try {
        const updated = await markNotificationRead(item.id)
        setItems((prev) => prev.map((n) => (n.id === updated.id ? updated : n)))
        setCount((c) => Math.max(0, c - 1))
      } catch {
        /* keep navigating */
      }
    }
    setOpen(false)
    if (item.relatedEntityType && item.relatedEntityId) {
      navigate(searchPath(item.relatedEntityType as SearchResultType, item.relatedEntityId))
    }
  }

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        className="icon-btn relative"
        aria-label="Notifications"
        title="Notifications"
        onClick={() => setOpen((v) => !v)}
      >
        <Bell className="h-4 w-4" />
        {count > 0 ? (
          <span className="absolute -right-1 -top-1 min-w-4 rounded-full bg-brand-600 px-1 text-center text-[10px] font-semibold text-white">
            {count > 99 ? '99+' : count}
          </span>
        ) : null}
      </button>
      {open ? (
        <div className="dropdown-panel absolute right-0 z-40 mt-2 w-[min(100vw-2rem,20rem)]">
          <div className="flex items-center justify-between border-b border-border px-3 py-2">
            <span className="text-sm font-semibold text-ink">Notifications</span>
            <button
              type="button"
              className="text-xs font-medium text-brand-700 hover:underline"
              onClick={() => {
                void markAllNotificationsRead().then((res) => {
                  setCount(res.count)
                  setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })))
                })
              }}
            >
              Mark all read
            </button>
          </div>
          {error ? <p className="px-3 py-3 text-sm text-[color:var(--app-danger-text)]">{error}</p> : null}
          {items.length === 0 && !error ? <p className="px-3 py-4 text-sm text-muted">You are all caught up.</p> : null}
          <ul className="max-h-80 overflow-y-auto">
            {items.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className={`flex w-full flex-col items-start px-3 py-2.5 text-left hover:bg-canvas ${item.readAt ? 'opacity-60' : ''}`}
                  onClick={() => void openItem(item)}
                >
                  {!item.readAt ? (
                    <span className="text-[10px] font-semibold uppercase tracking-wide text-brand-700">New</span>
                  ) : null}
                  <span className="text-sm font-medium text-ink">{item.title}</span>
                  {item.message ? <span className="text-xs text-muted">{item.message}</span> : null}
                  <span className="mt-0.5 text-[10px] text-muted">{relativeTime(item.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  )
}
