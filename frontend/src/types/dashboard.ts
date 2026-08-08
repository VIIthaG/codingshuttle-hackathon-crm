import type { LeadStatus } from './lead'

/** Matches backend DashboardSummaryResponse */
export interface DashboardSummary {
  totalLeads: number
  leadsByStatus: Partial<Record<LeadStatus, number>>
  openTasks: number
  overdueTasks: number
  upcomingFollowUps: number
}
