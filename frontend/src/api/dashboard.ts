import { apiRequest } from './client'
import type { DashboardSummary } from '../types/dashboard'

export function fetchDashboardSummary(): Promise<DashboardSummary> {
  return apiRequest<DashboardSummary>('/api/v1/dashboard/summary')
}
