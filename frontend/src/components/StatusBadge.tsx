import type { LeadSource, LeadStatus } from '../types/lead'
import type { DealStage } from '../types/deal'
import type { TaskStatus } from '../types/task'
import { formatDealStage } from '../utils/dealTransitions'
import { formatLeadSource } from '../utils/leadTransitions'

const leadStyles: Record<LeadStatus, string> = {
  NEW: 'bg-sky-50 text-sky-700 border-sky-200',
  CONTACTED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  QUALIFIED: 'bg-violet-50 text-violet-700 border-violet-200',
  CONVERTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  LOST: 'bg-slate-100 text-slate-600 border-slate-200',
}

const sourceStyles =
  'bg-slate-50 text-slate-600 border-slate-200'

const taskStyles: Record<TaskStatus, string> = {
  OPEN: 'bg-amber-50 text-amber-800 border-amber-200',
  COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  CANCELLED: 'bg-slate-100 text-slate-600 border-slate-200',
}

const dealStyles: Record<DealStage, string> = {
  PROSPECTING: 'bg-sky-50 text-sky-700 border-sky-200',
  QUALIFICATION: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  PROPOSAL: 'bg-violet-50 text-violet-700 border-violet-200',
  NEGOTIATION: 'bg-amber-50 text-amber-800 border-amber-200',
  CLOSED_WON: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  CLOSED_LOST: 'bg-slate-100 text-slate-600 border-slate-200',
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

export function DealStageBadge({ stage }: { stage: DealStage }) {
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${dealStyles[stage]}`}
    >
      {formatDealStage(stage)}
    </span>
  )
}

export function LeadSourceBadge({ source }: { source: LeadSource }) {
  return (
    <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${sourceStyles}`}>
      {formatLeadSource(source)}
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
