import { apiRequest } from './client'
import type { Page } from '../types/api'
import type { RelatedRecordType } from '../types/task'
import type { Call, CallCreateRequest, CallDirection, CallStatus, CallUpdateRequest } from '../types/call'

export type ListCallsParams = {
  status?: CallStatus
  direction?: CallDirection
  relatedType?: RelatedRecordType
  leadId?: string
  accountId?: string
  contactId?: string
  dealId?: string
  scheduledFrom?: string
  scheduledTo?: string
  page?: number
  size?: number
  sort?: string
}

export function listCalls(params: ListCallsParams = {}): Promise<Page<Call>> {
  const search = new URLSearchParams()
  if (params.status) search.set('status', params.status)
  if (params.direction) search.set('direction', params.direction)
  if (params.relatedType) search.set('relatedType', params.relatedType)
  if (params.leadId) search.set('leadId', params.leadId)
  if (params.accountId) search.set('accountId', params.accountId)
  if (params.contactId) search.set('contactId', params.contactId)
  if (params.dealId) search.set('dealId', params.dealId)
  if (params.scheduledFrom) search.set('scheduledFrom', params.scheduledFrom)
  if (params.scheduledTo) search.set('scheduledTo', params.scheduledTo)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 100))
  search.set('sort', params.sort ?? 'scheduledAt,asc')
  return apiRequest<Page<Call>>(`/api/v1/calls?${search.toString()}`)
}

export async function listAllCalls(params: Omit<ListCallsParams, 'page' | 'size'> = {}): Promise<Call[]> {
  const first = await listCalls({ ...params, page: 0, size: 100 })
  const rows = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await listCalls({ ...params, page, size: 100 })
    rows.push(...next.content)
  }
  return rows
}

export function getCall(id: string): Promise<Call> {
  return apiRequest<Call>(`/api/v1/calls/${id}`)
}

export function createCall(body: CallCreateRequest, idempotencyKey: string): Promise<Call> {
  return apiRequest<Call>('/api/v1/calls', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function updateCall(id: string, body: CallUpdateRequest): Promise<Call> {
  return apiRequest<Call>(`/api/v1/calls/${id}`, { method: 'PUT', body })
}

export function changeCallStatus(id: string, status: CallStatus, outcome?: string | null): Promise<Call> {
  return apiRequest<Call>(`/api/v1/calls/${id}/status`, {
    method: 'PATCH',
    body: { status, outcome: outcome ?? null },
  })
}

export function deleteCall(id: string): Promise<void> {
  return apiRequest<void>(`/api/v1/calls/${id}`, { method: 'DELETE' })
}
