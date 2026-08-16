import type { Task } from '../../types/task'
import { formatRelatedRecord } from '../../types/task'
import { TaskStatusBadge } from '../StatusBadge'
import { dueState, dueStateLabel, formatDateTime } from '../../utils/taskDates'

type TaskCardProps = {
  task: Task
  onOpen: (task: Task) => void
}

export function TaskCard({ task, onOpen }: TaskCardProps) {
  const state = dueState(task.dueAt, task.status)
  const label = dueStateLabel(state)

  return (
    <button
      type="button"
      onClick={() => onOpen(task)}
      className="w-full rounded-xl border border-border bg-surface p-4 text-left shadow-sm transition hover:border-brand-100 hover:shadow"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-ink">{task.title}</div>
          <div className="mt-0.5 truncate text-xs text-muted">{formatRelatedRecord(task)}</div>
        </div>
        <TaskStatusBadge status={task.status} />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted">
        <span>Due {formatDateTime(task.dueAt)}</span>
        {label ? (
          <span className={`badge ${state === 'overdue' ? 'badge-danger' : state === 'due_today' ? 'badge-warning' : 'badge-info'}`}>
            {label}
          </span>
        ) : null}
        {task.reminderAt ? (
          <span className="badge badge-brand">Reminder scheduled</span>
        ) : (
          <span className="text-muted">No reminder</span>
        )}
      </div>
      <div className="mt-2 truncate text-xs text-muted">→ {task.assignedToName}</div>
    </button>
  )
}
