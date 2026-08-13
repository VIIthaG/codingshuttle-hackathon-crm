import type { RelatedRecordType } from './task'

export interface ActivityItem {
  id: string
  type: string
  title: string
  description?: string | null
  timestamp: string
  actorName?: string | null
  status?: string | null
  relatedType?: RelatedRecordType | null
  relatedId?: string | null
  relatedName?: string | null
  metadata?: Record<string, unknown> | null
}

export interface ActivityTimeline {
  entityType: RelatedRecordType
  entityId: string
  entityName: string
  items: ActivityItem[]
}
