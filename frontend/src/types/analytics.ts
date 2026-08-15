import type { DealStage } from './deal'
import type { LeadStatus } from './lead'

export type AnalyticsRangePreset = '7d' | '30d' | '90d' | 'all'

export interface AnalyticsRange {
  from: string
  toExclusive: string
  preset: 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'LAST_90_DAYS' | 'ALL_TIME' | 'CUSTOM'
  bucket: 'DAY' | 'MONTH'
}

export interface LeadStatusCount {
  status: LeadStatus
  count: number
}

export interface DealStageMetrics {
  stage: DealStage
  count: number
  totalAmount: number | string
}

export interface AnalyticsSummary {
  range: AnalyticsRange
  leads: {
    total: number
    created: number
    converted: number
    lost: number
    conversionRate: number | string
    byStatus: LeadStatusCount[]
  }
  deals: {
    total: number
    created: number
    openCount: number
    wonCount: number
    lostCount: number
    openPipelineValue: number | string
    weightedPipelineValue: number | string
    wonValue: number | string
    lostValue: number | string
    averageOpenDealSize: number | string
    averageWonDealSize: number | string
    byStage: DealStageMetrics[]
  }
  activities: {
    tasks: { created: number; open: number; completed: number; cancelled: number; overdueNow: number }
    meetings: { created: number; scheduled: number; completed: number; cancelled: number }
    calls: { created: number; planned: number; completed: number; cancelled: number }
  }
  trends: {
    leads: { period: string; count: number }[]
    conversions: { period: string; count: number }[]
    deals: { period: string; count: number }[]
    activities: { period: string; tasks: number; meetings: number; calls: number }[]
  }
  team: {
    userId: string
    displayName: string
    openDeals: number
    openPipelineValue: number | string
    wonDeals: number
    wonValue: number | string
    openTasks: number
    overdueTasks: number
    scheduledMeetings: number
    plannedCalls: number
  }[]
}
