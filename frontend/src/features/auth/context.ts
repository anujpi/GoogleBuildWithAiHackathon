import { createContext, useContext } from 'react'
import type { Credentials, User } from './api'

export type Auth = {
  currentUser: User | null
  isAuthenticated: boolean
  /** True while a stored token is being checked against /auth/me. */
  isLoading: boolean
  /** Set when a stored token could not be checked for a reason other than 401 (e.g. network). */
  error: unknown
  retry: () => void
  login: (credentials: Credentials) => Promise<User>
  logout: () => void
  /** Why the last session ended, for the sign-in page to explain. Cleared on login. */
  endedReason: SessionEnd | null
}

export type SessionEnd = 'expired' | 'signed-out'

export const AuthContext = createContext<Auth | null>(null)

export function useAuth() {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside AuthProvider')
  return auth
}

/** Router state carried to /login. */
export type LoginState = { from?: string } | null
