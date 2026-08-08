import { ApiError } from '../types/api'

export function formatApiError(err: unknown, fallback = 'Something went wrong'): string {
  if (err instanceof ApiError) {
    if (err.status === 429) {
      const wait = err.retryAfterSeconds
      if (wait != null && wait > 0) {
        return `${err.message} Try again in about ${wait}s.`
      }
      return err.message || 'Too many login attempts. Please wait and try again.'
    }
    return err.message || fallback
  }
  if (err instanceof Error && err.message) {
    return err.message
  }
  return fallback
}
