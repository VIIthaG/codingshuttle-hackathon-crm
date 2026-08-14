import type { RelatedRecordType } from './task'

export type CallStatus = 'PLANNED' | 'COMPLETED' | 'CANCELLED'
export type CallDirection = 'INBOUND' | 'OUTBOUND'

export interface Call {
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
  scheduledAt: string
  durationMinutes: number | null
  direction: CallDirection
  status: CallStatus
  phoneNumber: string | null
  outcome: string | null
  createdAt: string
  updatedAt: string
}

export interface CallCreateRequest {
  leadId?: string | null
  accountId?: string | null
  contactId?: string | null
  dealId?: string | null
  assignedToId?: string | null
  title: string
  description?: string | null
  scheduledAt: string
  durationMinutes?: number | null
  direction: CallDirection
  phoneNumber?: string | null
  outcome?: string | null
}

export interface CallUpdateRequest extends CallCreateRequest {
  assignedToId: string
}
