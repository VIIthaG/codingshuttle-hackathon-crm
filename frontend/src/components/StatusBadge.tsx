import type { LeadSource, LeadStatus } from '../types/lead'
import type { DealStage } from '../types/deal'
import type { TaskStatus } from '../types/task'
import type { MeetingStatus } from '../types/meeting'
import type { CallStatus } from '../types/call'
import { formatDealStage } from '../utils/dealTransitions'
import { formatLeadSource } from '../utils/leadTransitions'

type Tone = 'success' | 'danger' | 'warning' | 'info' | 'brand'

const leadTone: Record<LeadStatus, Tone> = {
  NEW: 'info',
  CONTACTED: 'brand',
  QUALIFIED: 'brand',
  CONVERTED: 'success',
  LOST: 'danger',
}

const dealTone: Record<DealStage, Tone> = {
  PROSPECTING: 'info',
  QUALIFICATION: 'info',
  PROPOSAL: 'brand',
  NEGOTIATION: 'warning',
  CLOSED_WON: 'success',
  CLOSED_LOST: 'danger',
}

const taskTone: Record<TaskStatus, Tone> = {
  OPEN: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

const meetingTone: Record<MeetingStatus, Tone> = {
  SCHEDULED: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

const callTone: Record<CallStatus, Tone> = {
  PLANNED: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

export function StatusBadge({ label, tone }: { label: string; tone: Tone }) {
  return <span className={`badge badge-${tone}`}>{label}</span>
}

export function LeadStatusBadge({ status }: { status: LeadStatus }) {
  return <StatusBadge label={status} tone={leadTone[status]} />
}

export function DealStageBadge({ stage }: { stage: DealStage }) {
  return <StatusBadge label={formatDealStage(stage)} tone={dealTone[stage]} />
}

export function LeadSourceBadge({ source }: { source: LeadSource }) {
  return <StatusBadge label={formatLeadSource(source)} tone="info" />
}

export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  return <StatusBadge label={status} tone={taskTone[status]} />
}

export function MeetingStatusBadge({ status }: { status: MeetingStatus }) {
  return <StatusBadge label={status} tone={meetingTone[status]} />
}

export function CallStatusBadge({ status }: { status: CallStatus }) {
  return <StatusBadge label={status} tone={callTone[status]} />
}

export function UrgencyBadge({ urgency }: { urgency: string }) {
  const value = urgency.toUpperCase()
  const tone: Tone =
    value.includes('OVERDUE') ? 'danger' : value.includes('TODAY') ? 'warning' : 'info'
  const label = value.replaceAll('_', ' ')
  return <StatusBadge label={label} tone={tone} />
}
