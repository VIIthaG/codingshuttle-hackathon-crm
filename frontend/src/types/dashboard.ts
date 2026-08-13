import type { LeadStatus } from './lead'
import type { DealStage } from './deal'

/** Matches backend DashboardSummaryResponse */
export interface DashboardSummary {
  totalLeads: number
  leadsByStatus: Partial<Record<LeadStatus, number>>
  openTasks: number
  overdueTasks: number
  upcomingFollowUps: number
  openDeals: number
  openPipelineValue: number | string
  weightedPipelineValue: number | string
  dealsByStage: Partial<Record<DealStage, number>>
  wonDeals: number
  wonDealValue: number | string
}
