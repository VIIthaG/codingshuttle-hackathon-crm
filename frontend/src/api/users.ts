import { apiRequest } from './client'
import type { User } from '../types/auth'

/** ADMIN-only directory used for owner assignment. */
export function listUsers(): Promise<User[]> {
  return apiRequest<User[]>('/api/v1/users')
}
