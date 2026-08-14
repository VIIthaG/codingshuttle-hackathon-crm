import type { RelatedRecordType } from './task'

export type CalendarItemType = 'TASK' | 'MEETING' | 'CALL'

export interface CalendarItem {
  id: string
  itemType: CalendarItemType
  title: string
  startAt: string
  endAt: string | null
  status: string
  relatedType: RelatedRecordType
  relatedId: string
  relatedName: string
  assignedToId: string
  assignedToName: string
  metadata?: Record<string, unknown> | null
}

export interface CalendarResponse {
  from: string
  to: string
  items: CalendarItem[]
}

export interface WorkqueueItem {
  id: string
  itemType: CalendarItemType
  title: string
  timestamp: string
  status: string
  urgency: string
  relatedType: RelatedRecordType
  relatedId: string
  relatedName: string
  assignedToName: string
}

export interface WorkqueueResponse {
  overdueTasks: WorkqueueItem[]
  dueTodayTasks: WorkqueueItem[]
  upcomingTasks: WorkqueueItem[]
  todayMeetings: WorkqueueItem[]
  upcomingMeetings: WorkqueueItem[]
  todayCalls: WorkqueueItem[]
  upcomingCalls: WorkqueueItem[]
  counts: {
    overdueTasks: number
    dueTodayTasks: number
    upcomingTasks: number
    todayMeetings: number
    upcomingMeetings: number
    todayCalls: number
    upcomingCalls: number
  }
}
