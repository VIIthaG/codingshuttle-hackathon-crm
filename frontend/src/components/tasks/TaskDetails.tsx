import type { Task } from '../../types/task'
import { formatRelatedRecord } from '../../types/task'
import { TaskStatusBadge } from '../StatusBadge'
import { dueState, dueStateLabel, formatDateTime } from '../../utils/taskDates'

type TaskDetailsProps = {
  open: boolean
  task: Task | null
  actionPending?: boolean
  actionError?: string | null
  onClose: () => void
  onEdit: (task: Task) => void
  onComplete: (task: Task) => void
  onCancel: (task: Task) => void
  onDelete: (task: Task) => void
}

export function TaskDetails({
  open,
  task,
  actionPending = false,
  actionError = null,
  onClose,
  onEdit,
  onComplete,
  onCancel,
  onDelete,
}: TaskDetailsProps) {
  if (!open || !task) return null

  const state = dueState(task.dueAt, task.status)
  const label = dueStateLabel(state)
  const isOpen = task.status === 'OPEN'

  return (
    <div className="fixed inset-0 z-40 flex justify-end overlay-backdrop">
      <button type="button" aria-label="Close task details" className="flex-1 cursor-default" onClick={onClose} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-details-title"
        className="flex h-full w-full max-w-md flex-col border-l border-border bg-surface shadow-xl"
      >
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h2 id="task-details-title" className="truncate text-lg font-semibold text-ink">
              {task.title}
            </h2>
            <p className="mt-0.5 text-sm text-muted">{formatRelatedRecord(task)}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-canvas"
          >
            Close
          </button>
        </header>

        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          <div className="flex flex-wrap items-center gap-2">
            <TaskStatusBadge status={task.status} />
            {label ? (
              <span
                className={`badge ${state === 'overdue' ? 'badge-danger' : state === 'due_today' ? 'badge-warning' : 'badge-info'}`}
              >
                {label}
              </span>
            ) : null}
            {task.reminderAt ? (
              <span className="badge badge-brand">Reminder scheduled</span>
            ) : (
              <span className="badge badge-info">No reminder</span>
            )}
          </div>

          {task.description ? (
            <p className="whitespace-pre-wrap text-sm text-ink">{task.description}</p>
          ) : (
            <p className="text-sm text-muted">No description</p>
          )}

          <dl className="space-y-3 text-sm">
            <Row label="Due" value={formatDateTime(task.dueAt)} />
            <Row
              label="Reminder"
              value={task.reminderAt ? formatDateTime(task.reminderAt) : 'None'}
            />
            <Row label="Assigned to" value={task.assignedToName} />
            <Row label="Created" value={formatDateTime(task.createdAt)} />
            <Row label="Updated" value={formatDateTime(task.updatedAt)} />
          </dl>

          {isOpen ? (
            <section className="space-y-2">
              <h3 className="text-sm font-semibold text-ink">Actions</h3>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={actionPending}
                  onClick={() => onComplete(task)}
                  className="btn btn-primary"
                >
                  {actionPending ? 'Working…' : 'Complete'}
                </button>
                <button
                  type="button"
                  disabled={actionPending}
                  onClick={() => onCancel(task)}
                  className="rounded-lg border border-border bg-surface px-3 py-1.5 text-sm font-medium text-ink hover:bg-canvas disabled:opacity-60"
                >
                  Cancel task
                </button>
              </div>
            </section>
          ) : (
            <p className="text-sm text-muted">
              This task is {task.status}. Complete/Cancel actions are only available for OPEN tasks.
            </p>
          )}

          {actionError ? (
            <div className="alert alert-error">
              {actionError}
            </div>
          ) : null}
        </div>

        <footer className="flex gap-2 border-t border-border px-5 py-4">
          <button
            type="button"
            onClick={() => onEdit(task)}
            disabled={actionPending}
            className="flex-1 btn btn-primary disabled:opacity-60"
          >
            Edit
          </button>
          <button
            type="button"
            onClick={() => onDelete(task)}
            disabled={actionPending}
            className="rounded-lg border border-red-200 bg-surface px-3 py-2 text-sm font-medium text-[color:var(--app-danger-text)] hover:bg-red-50 disabled:opacity-60"
          >
            Delete
          </button>
        </footer>
      </aside>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] gap-2">
      <dt className="text-muted">{label}</dt>
      <dd className="break-words text-ink">{value}</dd>
    </div>
  )
}
