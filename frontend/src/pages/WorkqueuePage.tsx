import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { completeTask } from '../api/tasks'
import { changeMeetingStatus } from '../api/meetings'
import { changeCallStatus } from '../api/calls'
import { getWorkqueue } from '../api/schedule'
import type { WorkqueueItem, WorkqueueResponse } from '../types/schedule'
import { relatedTypeLabel } from '../types/task'
import { formatApiError } from '../utils/errors'
import { formatDateTime } from '../utils/taskDates'

function Section({ title, items, onAction }: { title: string; items: WorkqueueItem[]; onAction: (item: WorkqueueItem, action: 'complete' | 'cancel') => void }) {
  const navigate = useNavigate()
  return (
    <section className="rounded-xl border border-border bg-surface p-4">
      <h2 className="text-sm font-semibold text-ink">{title}</h2>
      {items.length === 0 ? <p className="mt-2 text-sm text-muted">Nothing here.</p> : (
        <ul className="mt-3 space-y-2">
          {items.map((item) => (
            <li key={`${item.itemType}-${item.id}`} className="rounded-lg border border-border px-3 py-2">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <button type="button" className="text-left" onClick={() => navigate(item.itemType === 'TASK' ? '/tasks' : item.itemType === 'MEETING' ? '/meetings' : '/calls')}>
                  <div className="text-xs font-semibold uppercase text-muted">{item.itemType} · {item.urgency}</div>
                  <div className="font-medium text-ink">{item.title}</div>
                  <div className="text-xs text-muted">{relatedTypeLabel(item.relatedType)} · {item.relatedName}</div>
                  <div className="text-xs text-muted">{formatDateTime(item.timestamp)}</div>
                </button>
                <div className="flex gap-2">
                  <button type="button" onClick={() => onAction(item, 'complete')} className="rounded-lg bg-emerald-600 px-2 py-1 text-xs text-white">Complete</button>
                  {item.itemType !== 'TASK' ? (
                    <button type="button" onClick={() => onAction(item, 'cancel')} className="rounded-lg border px-2 py-1 text-xs">Cancel</button>
                  ) : null}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export function WorkqueuePage() {
  const [data, setData] = useState<WorkqueueResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await getWorkqueue())
    } catch (err) {
      setError(formatApiError(err, 'Failed to load workqueue'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  async function onAction(item: WorkqueueItem, action: 'complete' | 'cancel') {
    try {
      if (item.itemType === 'TASK' && action === 'complete') await completeTask(item.id)
      if (item.itemType === 'MEETING') await changeMeetingStatus(item.id, action === 'complete' ? 'COMPLETED' : 'CANCELLED')
      if (item.itemType === 'CALL') await changeCallStatus(item.id, action === 'complete' ? 'COMPLETED' : 'CANCELLED')
      await refresh()
    } catch (err) {
      setError(formatApiError(err, 'Action failed'))
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">What needs attention next — overdue first, then today, then upcoming. No AI ranking.</p>
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      {loading || !data ? <p className="text-sm text-muted">Loading…</p> : (
        <div className="grid gap-4 lg:grid-cols-2">
          <Section title={`Needs attention (${data.counts.overdueTasks})`} items={data.overdueTasks} onAction={onAction} />
          <Section title="Today" items={[...data.dueTodayTasks, ...data.todayMeetings, ...data.todayCalls]} onAction={onAction} />
          <Section title="Upcoming" items={[...data.upcomingTasks, ...data.upcomingMeetings, ...data.upcomingCalls]} onAction={onAction} />
        </div>
      )}
    </div>
  )
}
