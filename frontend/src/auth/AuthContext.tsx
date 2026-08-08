import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import * as authApi from '../api/auth'
import { getStoredAccessToken, setStoredAccessToken } from '../api/client'
import { ApiError } from '../types/api'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '../types/auth'
import { AuthContext } from './auth-context'

const USER_KEY = 'flowcrm.user'

function readStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

function writeStoredUser(user: User | null): void {
  if (user == null) {
    localStorage.removeItem(USER_KEY)
    return
  }
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

function applySession(response: AuthResponse): void {
  setStoredAccessToken(response.accessToken)
  writeStoredUser(response.user)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readStoredUser())
  const [accessToken, setAccessToken] = useState<string | null>(() => getStoredAccessToken())
  const [isBootstrapping, setIsBootstrapping] = useState(true)

  const clearSession = useCallback(() => {
    setStoredAccessToken(null)
    writeStoredUser(null)
    setAccessToken(null)
    setUser(null)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function restore() {
      const token = getStoredAccessToken()
      if (!token) {
        if (!cancelled) {
          clearSession()
          setIsBootstrapping(false)
        }
        return
      }

      try {
        const me = await authApi.fetchMe(token)
        if (!cancelled) {
          setAccessToken(token)
          setUser(me)
          writeStoredUser(me)
        }
      } catch (err) {
        if (!cancelled) {
          if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
            clearSession()
          }
        }
      } finally {
        if (!cancelled) setIsBootstrapping(false)
      }
    }

    void restore()
    return () => {
      cancelled = true
    }
  }, [clearSession])

  const login = useCallback(async (payload: LoginRequest) => {
    const response = await authApi.login(payload)
    applySession(response)
    setAccessToken(response.accessToken)
    setUser(response.user)
  }, [])

  const register = useCallback(async (payload: RegisterRequest) => {
    const response = await authApi.register(payload)
    applySession(response)
    setAccessToken(response.accessToken)
    setUser(response.user)
  }, [])

  const logout = useCallback(() => {
    clearSession()
  }, [clearSession])

  const value = useMemo(
    () => ({
      user,
      accessToken,
      isAuthenticated: Boolean(accessToken && user),
      isBootstrapping,
      login,
      register,
      logout,
    }),
    [user, accessToken, isBootstrapping, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
