import type { RelatedRecordType } from './task'

export type MeetingStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'

export interface Meeting {
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
  startAt: string
  endAt: string
  location: string | null
  meetingUrl: string | null
  status: MeetingStatus
  createdAt: string
  updatedAt: string
}

export interface MeetingCreateRequest {
  leadId?: string | null
  accountId?: string | null
  contactId?: string | null
  dealId?: string | null
  assignedToId?: string | null
  title: string
  description?: string | null
  startAt: string
  endAt: string
  location?: string | null
  meetingUrl?: string | null
}

export interface MeetingUpdateRequest extends MeetingCreateRequest {
  assignedToId: string
}
