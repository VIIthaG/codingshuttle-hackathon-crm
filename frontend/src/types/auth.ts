export type Role = 'ADMIN' | 'SALES_REP'

/** Matches backend UserResponse */
export interface User {
  id: string
  email: string
  fullName: string
  role: Role
}

/** Matches backend LoginRequest */
export interface LoginRequest {
  email: string
  password: string
}

/** Matches backend RegisterRequest */
export interface RegisterRequest {
  email: string
  password: string
  fullName: string
}

/** Matches backend AuthResponse */
export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}
