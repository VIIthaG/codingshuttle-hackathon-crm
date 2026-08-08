import type { Task } from '../../types/task'
import { TaskStatusBadge } from '../StatusBadge'
import { dueState, dueStateLabel, formatDateTime } from '../../utils/taskDates'
import { TaskCard } from './TaskCard'

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
      <div className="hidden overflow-x-auto rounded-xl border border-border bg-surface shadow-sm lg:block">
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
                      <div className="mt-0.5 line-clamp-1 max-w-xs text-xs text-muted">
                        {task.description}
                      </div>
                    ) : null}
                  </td>
                  <td className="max-w-[10rem] truncate px-4 py-3 text-slate-600">{task.leadName}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">
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
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">
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
                  <td className="max-w-[8rem] truncate px-4 py-3 text-slate-600">
                    {task.assignedToName}
                  </td>
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
          const busy = actionPendingId === task.id
          return (
            <div key={task.id} className="space-y-2">
              <TaskCard task={task} onOpen={onOpenTask} />
              <div className="flex flex-wrap gap-3 px-1">
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
            </div>
          )
        })}
      </div>
    </>
  )
}
