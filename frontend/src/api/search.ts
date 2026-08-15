import { apiRequest } from './client'
import type { SearchResponse, SearchResultType } from '../types/search'

export function globalSearch(query: string, types?: SearchResultType[], limit?: number): Promise<SearchResponse> {
  const search = new URLSearchParams()
  search.set('q', query)
  if (types && types.length > 0) search.set('types', types.join(','))
  if (limit != null) search.set('limit', String(limit))
  return apiRequest<SearchResponse>(`/api/v1/search?${search.toString()}`)
}
