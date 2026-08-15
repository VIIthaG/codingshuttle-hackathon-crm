export type SearchResultType =
  | 'LEAD'
  | 'ACCOUNT'
  | 'CONTACT'
  | 'DEAL'
  | 'TASK'
  | 'MEETING'
  | 'CALL'

export type SearchResult = {
  type: SearchResultType
  id: string
  title: string
  subtitle: string | null
  status: string | null
  relatedType: 'LEAD' | 'ACCOUNT' | 'CONTACT' | 'DEAL' | null
  relatedId: string | null
  relatedName: string | null
  metadata: Record<string, unknown> | null
}

export type SearchResponse = {
  query: string
  results: SearchResult[]
}

export function searchPath(type: SearchResultType, id: string): string {
  const page =
    type === 'LEAD'
      ? '/leads'
      : type === 'ACCOUNT'
        ? '/accounts'
        : type === 'CONTACT'
          ? '/contacts'
          : type === 'DEAL'
            ? '/deals'
            : type === 'TASK'
              ? '/tasks'
              : type === 'MEETING'
                ? '/meetings'
                : '/calls'
  return `${page}?open=${id}`
}
