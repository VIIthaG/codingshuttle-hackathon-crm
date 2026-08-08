import { apiRequest } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '../types/auth'

export function login(payload: LoginRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: payload,
    auth: false,
  })
}

export function register(payload: RegisterRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: payload,
    auth: false,
  })
}

export function fetchMe(token?: string): Promise<User> {
  return apiRequest<User>('/api/v1/auth/me', {
    method: 'GET',
    token: token ?? undefined,
    auth: true,
  })
}
