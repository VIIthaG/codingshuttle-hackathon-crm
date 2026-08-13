import { apiRequest } from './client'
import type { Page } from '../types/api'
import type { Contact, ContactCreateRequest, ContactUpdateRequest } from '../types/contact'

export type ListContactsParams = {
  search?: string
  accountId?: string
  ownerId?: string
  page?: number
  size?: number
  sort?: string
}

export function listContacts(params: ListContactsParams = {}): Promise<Page<Contact>> {
  const search = new URLSearchParams()
  if (params.search) search.set('search', params.search)
  if (params.accountId) search.set('accountId', params.accountId)
  if (params.ownerId) search.set('ownerId', params.ownerId)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 50))
  if (params.sort) search.set('sort', params.sort)
  return apiRequest<Page<Contact>>(`/api/v1/contacts?${search.toString()}`)
}

export async function listAllContacts(params: Omit<ListContactsParams, 'page' | 'size'> = {}): Promise<Contact[]> {
  const pageSize = 100
  const first = await listContacts({ ...params, page: 0, size: pageSize, sort: 'lastName,asc' })
  const contacts = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await listContacts({ ...params, page, size: pageSize, sort: 'lastName,asc' })
    contacts.push(...next.content)
  }
  return contacts
}

export function getContact(id: string): Promise<Contact> {
  return apiRequest<Contact>(`/api/v1/contacts/${id}`)
}

export function createContact(body: ContactCreateRequest, idempotencyKey: string): Promise<Contact> {
  return apiRequest<Contact>('/api/v1/contacts', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function updateContact(id: string, body: ContactUpdateRequest): Promise<Contact> {
  return apiRequest<Contact>(`/api/v1/contacts/${id}`, {
    method: 'PUT',
    body,
  })
}

export function deleteContact(id: string): Promise<void> {
  return apiRequest<void>(`/api/v1/contacts/${id}`, {
    method: 'DELETE',
  })
}
