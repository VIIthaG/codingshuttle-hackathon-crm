import type { LeadStatus } from '../types/lead'
import type { TaskStatus } from '../types/task'

const leadStyles: Record<LeadStatus, string> = {
  NEW: 'bg-sky-50 text-sky-700 border-sky-200',
  CONTACTED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  QUALIFIED: 'bg-violet-50 text-violet-700 border-violet-200',
  CONVERTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  LOST: 'bg-slate-100 text-slate-600 border-slate-200',
}

const taskStyles: Record<TaskStatus, string> = {
  OPEN: 'bg-amber-50 text-amber-800 border-amber-200',
  COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  CANCELLED: 'bg-slate-100 text-slate-600 border-slate-200',
}

export function LeadStatusBadge({ status }: { status: LeadStatus }) {
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${leadStyles[status]}`}
    >
      {status}
    </span>
  )
}

export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${taskStyles[status]}`}
    >
      {status}
    </span>
  )
}
