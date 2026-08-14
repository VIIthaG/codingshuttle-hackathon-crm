import { apiRequest } from './client'
import type { CalendarResponse, WorkqueueResponse } from '../types/schedule'

export function getCalendar(from: string, to: string): Promise<CalendarResponse> {
  const search = new URLSearchParams({ from, to })
  return apiRequest<CalendarResponse>(`/api/v1/calendar?${search.toString()}`)
}

export function getWorkqueue(): Promise<WorkqueueResponse> {
  return apiRequest<WorkqueueResponse>('/api/v1/workqueue')
}
