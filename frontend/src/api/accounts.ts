import { apiRequest } from './client'
import type { Page } from '../types/api'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../types/account'

export type ListAccountsParams = {
  search?: string
  ownerId?: string
  page?: number
  size?: number
  sort?: string
}

export function listAccounts(params: ListAccountsParams = {}): Promise<Page<Account>> {
  const search = new URLSearchParams()
  if (params.search) search.set('search', params.search)
  if (params.ownerId) search.set('ownerId', params.ownerId)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 50))
  if (params.sort) search.set('sort', params.sort)
  return apiRequest<Page<Account>>(`/api/v1/accounts?${search.toString()}`)
}

export async function listAllAccounts(search?: string): Promise<Account[]> {
  const pageSize = 100
  const first = await listAccounts({ search, page: 0, size: pageSize, sort: 'name,asc' })
  const accounts = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await listAccounts({ search, page, size: pageSize, sort: 'name,asc' })
    accounts.push(...next.content)
  }
  return accounts
}

export function getAccount(id: string): Promise<Account> {
  return apiRequest<Account>(`/api/v1/accounts/${id}`)
}

export function createAccount(body: AccountCreateRequest, idempotencyKey: string): Promise<Account> {
  return apiRequest<Account>('/api/v1/accounts', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function updateAccount(id: string, body: AccountUpdateRequest): Promise<Account> {
  return apiRequest<Account>(`/api/v1/accounts/${id}`, {
    method: 'PUT',
    body,
  })
}

export function deleteAccount(id: string): Promise<void> {
  return apiRequest<void>(`/api/v1/accounts/${id}`, {
    method: 'DELETE',
  })
}
