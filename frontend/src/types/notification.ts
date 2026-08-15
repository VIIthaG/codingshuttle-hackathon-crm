export type NotificationType = 'ASSIGNMENT'

export type NotificationItem = {
  id: string
  type: NotificationType
  title: string
  message: string | null
  relatedEntityType: import('./search').SearchResultType | null
  relatedEntityId: string | null
  readAt: string | null
  createdAt: string
}

export type UnreadCountResponse = {
  count: number
}
