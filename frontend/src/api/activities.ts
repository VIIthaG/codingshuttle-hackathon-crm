import { apiRequest } from './client'
import type { ActivityTimeline } from '../types/activity'
import type { RelatedRecordType } from '../types/task'

export function getActivityTimeline(entityType: RelatedRecordType, entityId: string): Promise<ActivityTimeline> {
  const search = new URLSearchParams()
  search.set('entityType', entityType)
  search.set('entityId', entityId)
  return apiRequest<ActivityTimeline>(`/api/v1/activities/timeline?${search.toString()}`)
}
