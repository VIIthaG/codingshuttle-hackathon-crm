import { apiRequest } from './client'
import type { Page } from '../types/api'
import type { NotificationItem, UnreadCountResponse } from '../types/notification'

export function listNotifications(params: { unreadOnly?: boolean; page?: number; size?: number } = {}): Promise<
  Page<NotificationItem>
> {
  const search = new URLSearchParams()
  if (params.unreadOnly) search.set('unreadOnly', 'true')
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 20))
  search.set('sort', 'createdAt,desc')
  return apiRequest<Page<NotificationItem>>(`/api/v1/notifications?${search.toString()}`)
}

export function getUnreadNotificationCount(): Promise<UnreadCountResponse> {
  return apiRequest<UnreadCountResponse>('/api/v1/notifications/unread-count')
}

export function markNotificationRead(id: string): Promise<NotificationItem> {
  return apiRequest<NotificationItem>(`/api/v1/notifications/${id}/read`, { method: 'PATCH' })
}

export function markAllNotificationsRead(): Promise<UnreadCountResponse> {
  return apiRequest<UnreadCountResponse>('/api/v1/notifications/read-all', { method: 'PATCH' })
}
