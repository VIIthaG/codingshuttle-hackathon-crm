/** Matches backend ErrorResponse */
export interface ApiErrorBody {
  timestamp?: string
  status: number
  error: string
  message: string
  fieldErrors?: Record<string, string> | null
}

export class ApiError extends Error {
  readonly status: number
  readonly error: string
  readonly fieldErrors: Record<string, string> | null
  readonly retryAfterSeconds: number | null

  constructor(
    status: number,
    error: string,
    message: string,
    fieldErrors: Record<string, string> | null = null,
    retryAfterSeconds: number | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.error = error
    this.fieldErrors = fieldErrors
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/** Spring Data Page JSON shape */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}
