export type TaskStatus = 'OPEN' | 'COMPLETED' | 'CANCELLED'

export type RelatedRecordType = 'LEAD' | 'ACCOUNT' | 'CONTACT' | 'DEAL'

/** Matches backend TaskResponse */
export interface Task {
  id: string
  relatedType: RelatedRecordType
  relatedId: string
  relatedName: string
  leadId?: string | null
  leadName?: string | null
  accountId?: string | null
  accountName?: string | null
  contactId?: string | null
  contactName?: string | null
  dealId?: string | null
  dealName?: string | null
  assignedToId: string
  assignedToName: string
  title: string
  description: string | null
  dueAt: string
  reminderAt: string | null
  status: TaskStatus
  createdAt: string
  updatedAt: string
}

/** Matches backend TaskCreateRequest */
export interface TaskCreateRequest {
  leadId?: string | null
  accountId?: string | null
  contactId?: string | null
  dealId?: string | null
  assignedToId?: string | null
  title: string
  description?: string | null
  dueAt: string
  reminderAt?: string | null
}

/** Matches backend TaskUpdateRequest */
export interface TaskUpdateRequest {
  leadId?: string | null
  accountId?: string | null
  contactId?: string | null
  dealId?: string | null
  assignedToId: string
  title: string
  description?: string | null
  dueAt: string
  reminderAt?: string | null
  status: TaskStatus
}

export const RELATED_RECORD_TYPES: RelatedRecordType[] = ['LEAD', 'ACCOUNT', 'CONTACT', 'DEAL']

export function relatedTypeLabel(type: RelatedRecordType): string {
  switch (type) {
    case 'LEAD':
      return 'Lead'
    case 'ACCOUNT':
      return 'Account'
    case 'CONTACT':
      return 'Contact'
    case 'DEAL':
      return 'Deal'
    default:
      return type
  }
}

export function formatRelatedRecord(task: Task): string {
  return `${relatedTypeLabel(task.relatedType)} · ${task.relatedName}`
}

export function relationFields(task: Task): Pick<TaskUpdateRequest, 'leadId' | 'accountId' | 'contactId' | 'dealId'> {
  return {
    leadId: task.leadId ?? null,
    accountId: task.accountId ?? null,
    contactId: task.contactId ?? null,
    dealId: task.dealId ?? null,
  }
}
