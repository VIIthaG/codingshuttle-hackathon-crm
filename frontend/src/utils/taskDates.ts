/** Browser-local display helpers for Instant ISO strings from the API. */

export type DueState = 'overdue' | 'due_today' | 'upcoming' | 'none'

function startOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    })
  } catch {
    return iso
  }
}

export function dueState(dueAt: string, status: string): DueState {
  if (status !== 'OPEN') return 'none'
  const due = new Date(dueAt)
  if (Number.isNaN(due.getTime())) return 'none'
  const now = new Date()
  if (due.getTime() < now.getTime()) return 'overdue'
  const today = startOfLocalDay(now)
  const dueDay = startOfLocalDay(due)
  if (dueDay.getTime() === today.getTime()) return 'due_today'
  return 'upcoming'
}

export function dueStateLabel(state: DueState): string | null {
  switch (state) {
    case 'overdue':
      return 'Overdue'
    case 'due_today':
      return 'Due today'
    case 'upcoming':
      return 'Upcoming'
    default:
      return null
  }
}

/** Converts Instant ISO → value for `<input type="datetime-local">`. */
export function toDatetimeLocalValue(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Converts datetime-local value → Instant ISO for the API. */
export function fromDatetimeLocalValue(local: string): string {
  return new Date(local).toISOString()
}

export function defaultDueLocal(): string {
  const d = new Date()
  d.setHours(d.getHours() + 24, 0, 0, 0)
  return toDatetimeLocalValue(d.toISOString())
}
