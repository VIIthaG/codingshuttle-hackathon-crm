import { apiRequest } from './client'
import type { Page } from '../types/api'
import type { RelatedRecordType } from '../types/task'
import type { Meeting, MeetingCreateRequest, MeetingStatus, MeetingUpdateRequest } from '../types/meeting'

export type ListMeetingsParams = {
  status?: MeetingStatus
  leadId?: string
  accountId?: string
  contactId?: string
  dealId?: string
  relatedType?: RelatedRecordType
  startFrom?: string
  startTo?: string
  page?: number
  size?: number
  sort?: string
}

export function listMeetings(params: ListMeetingsParams = {}): Promise<Page<Meeting>> {
  const search = new URLSearchParams()
  if (params.status) search.set('status', params.status)
  if (params.leadId) search.set('leadId', params.leadId)
  if (params.accountId) search.set('accountId', params.accountId)
  if (params.contactId) search.set('contactId', params.contactId)
  if (params.dealId) search.set('dealId', params.dealId)
  if (params.relatedType) search.set('relatedType', params.relatedType)
  if (params.startFrom) search.set('startFrom', params.startFrom)
  if (params.startTo) search.set('startTo', params.startTo)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 100))
  search.set('sort', params.sort ?? 'startAt,asc')
  return apiRequest<Page<Meeting>>(`/api/v1/meetings?${search.toString()}`)
}

export async function listAllMeetings(params: Omit<ListMeetingsParams, 'page' | 'size'> = {}): Promise<Meeting[]> {
  const first = await listMeetings({ ...params, page: 0, size: 100 })
  const rows = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await listMeetings({ ...params, page, size: 100 })
    rows.push(...next.content)
  }
  return rows
}

export function getMeeting(id: string): Promise<Meeting> {
  return apiRequest<Meeting>(`/api/v1/meetings/${id}`)
}

export function createMeeting(body: MeetingCreateRequest, idempotencyKey: string): Promise<Meeting> {
  return apiRequest<Meeting>('/api/v1/meetings', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function updateMeeting(id: string, body: MeetingUpdateRequest): Promise<Meeting> {
  return apiRequest<Meeting>(`/api/v1/meetings/${id}`, { method: 'PUT', body })
}

export function changeMeetingStatus(id: string, status: MeetingStatus): Promise<Meeting> {
  return apiRequest<Meeting>(`/api/v1/meetings/${id}/status`, {
    method: 'PATCH',
    body: { status },
  })
}

export function deleteMeeting(id: string): Promise<void> {
  return apiRequest<void>(`/api/v1/meetings/${id}`, { method: 'DELETE' })
}
