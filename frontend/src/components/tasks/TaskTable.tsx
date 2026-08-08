import type { Task } from '../../types/task'
import { TaskStatusBadge } from '../StatusBadge'
import { dueState, dueStateLabel, formatDateTime } from '../../utils/taskDates'

type TaskTableProps = {
  tasks: Task[]
  onOpenTask: (task: Task) => void
  onCompleteTask: (task: Task) => void
  onCancelTask: (task: Task) => void
  onDeleteTask: (task: Task) => void
  actionPendingId?: string | null
}

export function TaskTable({
  tasks,
  onOpenTask,
  onCompleteTask,
  onCancelTask,
  onDeleteTask,
  actionPendingId = null,
}: TaskTableProps) {
  if (tasks.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border bg-surface px-4 py-12 text-center text-sm text-muted">
        No tasks match these filters.
      </div>
    )
  }

  return (
    <>
      <div className="hidden overflow-hidden rounded-xl border border-border bg-surface shadow-sm lg:block">
        <table className="min-w-full divide-y divide-border text-left text-sm">
          <thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-4 py-3 font-semibold">Title</th>
              <th className="px-4 py-3 font-semibold">Lead</th>
              <th className="px-4 py-3 font-semibold">Due</th>
              <th className="px-4 py-3 font-semibold">Reminder</th>
              <th className="px-4 py-3 font-semibold">Status</th>
              <th className="px-4 py-3 font-semibold">Assigned</th>
              <th className="px-4 py-3 font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {tasks.map((task) => {
              const state = dueState(task.dueAt, task.status)
              const label = dueStateLabel(state)
              const busy = actionPendingId === task.id
              return (
                <tr key={task.id} className="hover:bg-slate-50/80">
                  <td className="px-4 py-3">
                    <div className="font-medium text-ink">{task.title}</div>
                    {task.description ? (
                      <div className="mt-0.5 line-clamp-1 text-xs text-muted">{task.description}</div>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{task.leadName}</td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>{formatDateTime(task.dueAt)}</div>
                    {label ? (
                      <div
                        className={[
                          'mt-0.5 text-xs font-semibold',
                          state === 'overdue' ? 'text-red-600' : 'text-amber-700',
                        ].join(' ')}
                      >
                        {label}
                      </div>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {task.reminderAt ? (
                      <div>
                        <div>{formatDateTime(task.reminderAt)}</div>
                        <div className="mt-0.5 text-xs font-medium text-violet-700">Scheduled</div>
                      </div>
                    ) : (
                      <span className="text-muted">None</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <TaskStatusBadge status={task.status} />
                  </td>
                  <td className="px-4 py-3 text-slate-600">{task.assignedToName}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => onOpenTask(task)}
                        className="text-sm font-medium text-brand-600 hover:text-brand-700 disabled:opacity-60"
                      >
                        Open
                      </button>
                      {task.status === 'OPEN' ? (
                        <>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => onCompleteTask(task)}
                            className="text-sm font-medium text-emerald-600 hover:text-emerald-700 disabled:opacity-60"
                          >
                            Complete
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => onCancelTask(task)}
                            className="text-sm font-medium text-slate-600 hover:text-slate-800 disabled:opacity-60"
                          >
                            Cancel
                          </button>
                        </>
                      ) : null}
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => onDeleteTask(task)}
                        className="text-sm font-medium text-red-600 hover:text-red-700 disabled:opacity-60"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <div className="grid gap-3 lg:hidden">
        {tasks.map((task) => {
          const state = dueState(task.dueAt, task.status)
          const label = dueStateLabel(state)
          const busy = actionPendingId === task.id
          return (
            <article
              key={task.id}
              className="rounded-xl border border-border bg-surface p-4 shadow-sm"
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="font-semibold text-ink">{task.title}</h3>
                  <p className="text-sm text-muted">{task.leadName}</p>
                </div>
                <TaskStatusBadge status={task.status} />
              </div>
              <dl className="mt-3 space-y-1 text-sm text-slate-600">
                <div>
                  Due {formatDateTime(task.dueAt)}
                  {label ? (
                    <span
                      className={[
                        'ml-2 text-xs font-semibold',
                        state === 'overdue' ? 'text-red-600' : 'text-amber-700',
                      ].join(' ')}
                    >
                      {label}
                    </span>
                  ) : null}
                </div>
                <div>
                  {task.reminderAt
                    ? `Reminder ${formatDateTime(task.reminderAt)}`
                    : 'No reminder'}
                </div>
                <div className="text-muted">{task.assignedToName}</div>
              </dl>
              <div className="mt-4 flex flex-wrap gap-3">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => onOpenTask(task)}
                  className="text-sm font-medium text-brand-600 disabled:opacity-60"
                >
                  Open
                </button>
                {task.status === 'OPEN' ? (
                  <>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => onCompleteTask(task)}
                      className="text-sm font-medium text-emerald-600 disabled:opacity-60"
                    >
                      Complete
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => onCancelTask(task)}
                      className="text-sm font-medium text-slate-600 disabled:opacity-60"
                    >
                      Cancel
                    </button>
                  </>
                ) : null}
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => onDeleteTask(task)}
                  className="text-sm font-medium text-red-600 disabled:opacity-60"
                >
                  Delete
                </button>
              </div>
            </article>
          )
        })}
      </div>
    </>
  )
}
