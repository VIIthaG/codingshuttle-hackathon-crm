import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { getCalendar } from '../api/schedule'
import type { CalendarItem } from '../types/schedule'
import { relatedTypeLabel } from '../types/task'
import { formatApiError } from '../utils/errors'

function startOfWeek(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  d.setHours(0, 0, 0, 0)
  return d
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

function typeStyle(type: CalendarItem['itemType']): string {
  if (type === 'MEETING') return 'border-indigo-200 bg-indigo-50 text-indigo-800'
  if (type === 'CALL') return 'border-amber-200 bg-amber-50 text-amber-800'
  return 'border-sky-200 bg-sky-50 text-sky-800'
}

export function CalendarPage() {
  const navigate = useNavigate()
  const [anchor, setAnchor] = useState(() => startOfWeek(new Date()))
  const [items, setItems] = useState<CalendarItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const from = useMemo(() => anchor, [anchor])
  const to = useMemo(() => addDays(anchor, 7), [anchor])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    void getCalendar(from.toISOString(), to.toISOString())
      .then((res) => {
        if (!cancelled) setItems(res.items)
      })
      .catch((err) => {
        if (!cancelled) setError(formatApiError(err, 'Failed to load calendar'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [from, to])

  const groups = useMemo(() => {
    const map = new Map<string, CalendarItem[]>()
    for (let i = 0; i < 7; i += 1) {
      const day = addDays(anchor, i)
      const key = day.toDateString()
      map.set(key, [])
    }
    for (const item of items) {
      const key = new Date(item.startAt).toDateString()
      const list = map.get(key)
      if (list) list.push(item)
    }
    return [...map.entries()]
  }, [items, anchor])

  function openItem(item: CalendarItem) {
    if (item.itemType === 'TASK') navigate('/tasks')
    if (item.itemType === 'MEETING') navigate('/meetings')
    if (item.itemType === 'CALL') navigate('/calls')
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => setAnchor(addDays(anchor, -7))} className="rounded-lg border border-border p-2"><ChevronLeft className="h-4 w-4" /></button>
          <button type="button" onClick={() => setAnchor(startOfWeek(new Date()))} className="rounded-lg border border-border px-3 py-2 text-sm">This week</button>
          <button type="button" onClick={() => setAnchor(addDays(anchor, 7))} className="rounded-lg border border-border p-2"><ChevronRight className="h-4 w-4" /></button>
        </div>
        <p className="text-sm text-muted">
          {from.toLocaleDateString()} – {addDays(to, -1).toLocaleDateString()} · OPEN tasks, SCHEDULED meetings, PLANNED calls
        </p>
      </div>
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      {loading ? <p className="text-sm text-muted">Loading…</p> : (
        <div className="space-y-4">
          {groups.map(([day, dayItems]) => (
            <section key={day} className="rounded-xl border border-border bg-surface p-4">
              <h2 className="text-sm font-semibold text-ink">{new Date(day).toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })}</h2>
              {dayItems.length === 0 ? <p className="mt-2 text-sm text-muted">Nothing scheduled.</p> : (
                <ul className="mt-3 space-y-2">
                  {dayItems.map((item) => (
                    <li key={`${item.itemType}-${item.id}`}>
                      <button type="button" onClick={() => openItem(item)} className={`w-full rounded-lg border px-3 py-2 text-left ${typeStyle(item.itemType)}`}>
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="text-xs font-semibold uppercase">{item.itemType}</span>
                          <span className="text-xs">{new Date(item.startAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                        </div>
                        <div className="mt-1 font-medium">{item.title}</div>
                        <div className="text-xs">{relatedTypeLabel(item.relatedType)} · {item.relatedName}</div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
