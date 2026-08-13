import { apiRequest } from './client'
import type { Page } from '../types/api'
import type { RelatedRecordType, Task, TaskCreateRequest, TaskStatus, TaskUpdateRequest } from '../types/task'

export type ListTasksParams = {
  status?: TaskStatus
  leadId?: string
  accountId?: string
  contactId?: string
  dealId?: string
  relatedType?: RelatedRecordType
  assignedToId?: string
  overdue?: boolean
  page?: number
  size?: number
  sort?: string
}

export function listTasks(params: ListTasksParams = {}): Promise<Page<Task>> {
  const search = new URLSearchParams()
  if (params.status) search.set('status', params.status)
  if (params.leadId) search.set('leadId', params.leadId)
  if (params.accountId) search.set('accountId', params.accountId)
  if (params.contactId) search.set('contactId', params.contactId)
  if (params.dealId) search.set('dealId', params.dealId)
  if (params.relatedType) search.set('relatedType', params.relatedType)
  if (params.assignedToId) search.set('assignedToId', params.assignedToId)
  if (params.overdue === true) search.set('overdue', 'true')
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 100))
  if (params.sort) search.set('sort', params.sort)
  return apiRequest<Page<Task>>(`/api/v1/tasks?${search.toString()}`)
}

/** Loads all matching pages. */
export async function listAllTasks(params: Omit<ListTasksParams, 'page' | 'size'> = {}): Promise<Task[]> {
  const pageSize = 100
  const first = await listTasks({ ...params, page: 0, size: pageSize, sort: 'dueAt,asc' })
  const tasks = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await listTasks({ ...params, page, size: pageSize, sort: 'dueAt,asc' })
    tasks.push(...next.content)
  }
  return tasks
}

export function getTask(id: string): Promise<Task> {
  return apiRequest<Task>(`/api/v1/tasks/${id}`)
}

export function createTask(body: TaskCreateRequest, idempotencyKey: string): Promise<Task> {
  return apiRequest<Task>('/api/v1/tasks', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function updateTask(id: string, body: TaskUpdateRequest): Promise<Task> {
  return apiRequest<Task>(`/api/v1/tasks/${id}`, {
    method: 'PUT',
    body,
  })
}

export function completeTask(id: string): Promise<Task> {
  return apiRequest<Task>(`/api/v1/tasks/${id}/complete`, {
    method: 'PATCH',
  })
}

export function deleteTask(id: string): Promise<void> {
  return apiRequest<void>(`/api/v1/tasks/${id}`, {
    method: 'DELETE',
  })
}
