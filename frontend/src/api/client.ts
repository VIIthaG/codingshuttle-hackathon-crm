import { ApiError, type ApiErrorBody } from '../types/api'

const TOKEN_KEY = 'flowcrm.accessToken'

export function getStoredAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredAccessToken(token: string | null): void {
  if (token == null || token === '') {
    localStorage.removeItem(TOKEN_KEY)
    return
  }
  localStorage.setItem(TOKEN_KEY, token)
}

function apiBaseUrl(): string {
  const raw = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (raw == null || raw.trim() === '') {
    return ''
  }
  return raw.replace(/\/$/, '')
}

type RequestOptions = {
  method?: string
  body?: unknown
  token?: string | null
  auth?: boolean
}

async function parseError(response: Response): Promise<ApiError> {
  const retryAfterHeader = response.headers.get('Retry-After')
  const retryAfterSeconds = retryAfterHeader ? Number.parseInt(retryAfterHeader, 10) : null
  const safeRetry =
    retryAfterSeconds != null && Number.isFinite(retryAfterSeconds) ? retryAfterSeconds : null

  let body: ApiErrorBody | null = null
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    body = null
  }

  if (body && typeof body.message === 'string') {
    return new ApiError(
      body.status ?? response.status,
      body.error ?? response.statusText,
      body.message,
      body.fieldErrors ?? null,
      safeRetry,
    )
  }

  if (response.status === 429) {
    return new ApiError(
      429,
      'Too Many Requests',
      'Too many login attempts. Please wait a moment and try again.',
      null,
      safeRetry,
    )
  }

  return new ApiError(
    response.status,
    response.statusText || 'Error',
    `Request failed with status ${response.status}`,
    null,
    safeRetry,
  )
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options
  const headers: Record<string, string> = {
    Accept: 'application/json',
  }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const token = options.token === undefined ? getStoredAccessToken() : options.token
  if (auth && token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    throw await parseError(response)
  }

  if (response.status === 401 && auth) {
    // Defensive: some gateways may return empty 401 bodies.
    throw new ApiError(401, 'Unauthorized', 'Authentication is required')
  }

  return (await response.json()) as T
}
