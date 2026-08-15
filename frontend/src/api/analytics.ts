import { apiRequest } from './client'
import type { AnalyticsRangePreset, AnalyticsSummary } from '../types/analytics'

export function fetchAnalyticsSummary(params: {
  range?: AnalyticsRangePreset
  assignedTo?: string
}): Promise<AnalyticsSummary> {
  const search = new URLSearchParams()
  if (params.range) search.set('range', params.range)
  if (params.assignedTo) search.set('assignedTo', params.assignedTo)
  const q = search.toString()
  return apiRequest<AnalyticsSummary>(`/api/v1/analytics/summary${q ? `?${q}` : ''}`)
}
