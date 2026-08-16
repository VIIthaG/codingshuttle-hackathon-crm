import { useEffect, useMemo, useState } from 'react'
import { getActivityTimeline } from '../../api/activities'
import type { ActivityItem, ActivityTimeline as Timeline } from '../../types/activity'
import type { RelatedRecordType } from '../../types/task'
import { formatApiError } from '../../utils/errors'
import { formatDateTime } from '../../utils/taskDates'

type ActivityTimelineProps = {
  entityType: RelatedRecordType
  entityId: string
  refreshKey?: number
}

function itemGlyph(type: string): string {
  if (type === 'TASK_COMPLETED' || type === 'MEETING_COMPLETED' || type === 'CALL_COMPLETED') return '✓'
  if (type === 'TASK_CREATED' || type === 'MEETING_CREATED' || type === 'CALL_CREATED') return '○'
  if (type === 'TASK_CANCELLED' || type === 'MEETING_CANCELLED' || type === 'CALL_CANCELLED') return '×'
  return '●'
}

function dayLabel(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const today = new Date()
  const startToday = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const startThat = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const diff = (startToday.getTime() - startThat.getTime()) / 86400000
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Yesterday'
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

export function ActivityTimeline({ entityType, entityId, refreshKey = 0 }: ActivityTimelineProps) {
  const [data, setData] = useState<Timeline | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    void getActivityTimeline(entityType, entityId)
      .then((timeline) => {
        if (!cancelled) setData(timeline)
      })
      .catch((err) => {
        if (!cancelled) setError(formatApiError(err, 'Failed to load activity'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [entityType, entityId, refreshKey])

  const groups = useMemo(() => {
    const map = new Map<string, ActivityItem[]>()
    for (const item of data?.items ?? []) {
      const key = dayLabel(item.timestamp)
      const list = map.get(key) ?? []
      list.push(item)
      map.set(key, list)
    }
    return [...map.entries()]
  }, [data])

  return (
    <section>
      <h3 className="text-sm font-semibold text-ink">Activity</h3>
      <p className="mt-1 text-xs text-muted">Lifecycle, tasks, meetings, and calls — not a full audit log.</p>
      {loading ? <p className="mt-2 text-sm text-muted">Loading activity…</p> : null}
      {error ? <p className="mt-2 text-sm text-[color:var(--app-danger-text)]">{error}</p> : null}
      {!loading && !error && groups.length === 0 ? (
        <p className="mt-2 text-sm text-muted">No activity yet.</p>
      ) : null}
      <div className="mt-3 space-y-4">
        {groups.map(([day, items]) => (
          <div key={day}>
            <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">{day}</div>
            <ol className="space-y-2 border-l border-border pl-3">
              {items.map((item) => (
                <li key={item.id} className="relative">
                  <div className="text-sm text-ink">
                    <span className="mr-1.5 text-muted">{itemGlyph(item.type)}</span>
                    <span className="font-medium">{item.title}</span>
                    <span className="ml-2 text-xs text-muted">{formatDateTime(item.timestamp)}</span>
                  </div>
                  {item.description ? <div className="mt-0.5 text-sm text-muted">{item.description}</div> : null}
                </li>
              ))}
            </ol>
          </div>
        ))}
      </div>
    </section>
  )
}
