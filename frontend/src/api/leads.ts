import { apiRequest } from './client'
import type { Page } from '../types/api'
import type {
  Lead,
  LeadCreateRequest,
  LeadConvertRequest,
  LeadStatus,
  LeadStatusUpdateRequest,
  LeadUpdateRequest,
} from '../types/lead'

export type ListLeadsParams = {
  status?: LeadStatus
  page?: number
  size?: number
  sort?: string
}

export function listLeads(params: ListLeadsParams = {}): Promise<Page<Lead>> {
  const search = new URLSearchParams()
  if (params.status) search.set('status', params.status)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 100))
  if (params.sort) search.set('sort', params.sort)
  const qs = search.toString()
  return apiRequest<Page<Lead>>(`/api/v1/leads?${qs}`)
}

/** Loads all pages (backend returns Spring Page). */
export async function listAllLeads(status?: LeadStatus): Promise<Lead[]> {
  const pageSize = 100
  const first = await listLeads({ status, page: 0, size: pageSize, sort: 'updatedAt,desc' })
  const leads = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await listLeads({ status, page, size: pageSize, sort: 'updatedAt,desc' })
    leads.push(...next.content)
  }
  return leads
}

export function getLead(id: string): Promise<Lead> {
  return apiRequest<Lead>(`/api/v1/leads/${id}`)
}

export function createLead(body: LeadCreateRequest, idempotencyKey: string): Promise<Lead> {
  return apiRequest<Lead>('/api/v1/leads', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function updateLead(id: string, body: LeadUpdateRequest): Promise<Lead> {
  return apiRequest<Lead>(`/api/v1/leads/${id}`, {
    method: 'PUT',
    body,
  })
}

export function changeLeadStatus(id: string, body: LeadStatusUpdateRequest): Promise<Lead> {
  return apiRequest<Lead>(`/api/v1/leads/${id}/status`, {
    method: 'PATCH',
    body,
  })
}

export function convertLead(id: string, body: LeadConvertRequest, idempotencyKey: string): Promise<Lead> {
  return apiRequest<Lead>(`/api/v1/leads/${id}/convert`, {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function deleteLead(id: string): Promise<void> {
  return apiRequest<void>(`/api/v1/leads/${id}`, {
    method: 'DELETE',
  })
}
