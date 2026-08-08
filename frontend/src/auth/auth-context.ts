import { createContext } from 'react'
import type { LoginRequest, RegisterRequest, User } from '../types/auth'

export type AuthContextValue = {
  user: User | null
  accessToken: string | null
  isAuthenticated: boolean
  isBootstrapping: boolean
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
