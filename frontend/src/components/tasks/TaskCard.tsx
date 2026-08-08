import type { Task } from '../../types/task'
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
      className="w-full rounded-xl border border-border bg-white p-4 text-left shadow-sm transition hover:border-brand-100 hover:shadow"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-ink">{task.title}</div>
          <div className="mt-0.5 truncate text-xs text-muted">Lead: {task.leadName}</div>
        </div>
        <TaskStatusBadge status={task.status} />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-slate-600">
        <span>Due {formatDateTime(task.dueAt)}</span>
        {label ? (
          <span
            className={[
              'rounded-full border px-2 py-0.5 font-semibold',
              state === 'overdue'
                ? 'border-red-200 bg-red-50 text-red-700'
                : state === 'due_today'
                  ? 'border-amber-200 bg-amber-50 text-amber-800'
                  : 'border-sky-200 bg-sky-50 text-sky-700',
            ].join(' ')}
          >
            {label}
          </span>
        ) : null}
        {task.reminderAt ? (
          <span className="rounded-full border border-violet-200 bg-violet-50 px-2 py-0.5 font-semibold text-violet-700">
            Reminder scheduled
          </span>
        ) : (
          <span className="text-muted">No reminder</span>
        )}
      </div>
      <div className="mt-2 truncate text-xs text-muted">→ {task.assignedToName}</div>
    </button>
  )
}
