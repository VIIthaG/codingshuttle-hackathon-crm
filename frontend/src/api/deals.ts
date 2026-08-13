import { apiRequest } from './client'
import type { Page } from '../types/api'
import type {
  Deal,
  DealCreateRequest,
  DealStage,
  DealStageUpdateRequest,
  DealUpdateRequest,
} from '../types/deal'

export type ListDealsParams = {
  search?: string
  stage?: DealStage
  accountId?: string
  ownerId?: string
  expectedCloseFrom?: string
  expectedCloseTo?: string
  page?: number
  size?: number
  sort?: string
}

export function listDeals(params: ListDealsParams = {}): Promise<Page<Deal>> {
  const search = new URLSearchParams()
  if (params.search) search.set('search', params.search)
  if (params.stage) search.set('stage', params.stage)
  if (params.accountId) search.set('accountId', params.accountId)
  if (params.ownerId) search.set('ownerId', params.ownerId)
  if (params.expectedCloseFrom) search.set('expectedCloseFrom', params.expectedCloseFrom)
  if (params.expectedCloseTo) search.set('expectedCloseTo', params.expectedCloseTo)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 50))
  if (params.sort) search.set('sort', params.sort)
  return apiRequest<Page<Deal>>(`/api/v1/deals?${search.toString()}`)
}

export async function listAllDeals(params: Omit<ListDealsParams, 'page' | 'size'> = {}): Promise<Deal[]> {
  const pageSize = 100
  const first = await listDeals({ ...params, page: 0, size: pageSize, sort: params.sort ?? 'updatedAt,desc' })
  const deals = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await listDeals({
      ...params,
      page,
      size: pageSize,
      sort: params.sort ?? 'updatedAt,desc',
    })
    deals.push(...next.content)
  }
  return deals
}

export function getDeal(id: string): Promise<Deal> {
  return apiRequest<Deal>(`/api/v1/deals/${id}`)
}

export function createDeal(body: DealCreateRequest, idempotencyKey: string): Promise<Deal> {
  return apiRequest<Deal>('/api/v1/deals', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function updateDeal(id: string, body: DealUpdateRequest): Promise<Deal> {
  return apiRequest<Deal>(`/api/v1/deals/${id}`, {
    method: 'PUT',
    body,
  })
}

export function changeDealStage(id: string, body: DealStageUpdateRequest): Promise<Deal> {
  return apiRequest<Deal>(`/api/v1/deals/${id}/stage`, {
    method: 'PATCH',
    body,
  })
}

export function deleteDeal(id: string): Promise<void> {
  return apiRequest<void>(`/api/v1/deals/${id}`, {
    method: 'DELETE',
  })
}
