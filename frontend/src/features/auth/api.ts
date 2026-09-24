import { api } from '@/lib/api/client'

export type UserRole = 'FARMER' | (string & {})
export type User = { id: string; fullName: string; email: string; role: UserRole }

export type Credentials = { email: string; password: string }
export type Registration = { fullName: string; email: string; password: string }

/** Only accessToken is read; anything else the backend adds is ignored. */
type LoginResponse = { accessToken: string }

export const login = (body: Credentials) => api<LoginResponse>('/auth/login', { method: 'POST', body })

// No role is sent: the backend always creates a FARMER.
export const register = (body: Registration) => api<unknown>('/auth/register', { method: 'POST', body })

export const getMe = (signal?: AbortSignal) => api<User>('/auth/me', { signal })

export const roleLabel = (role: UserRole) => role.charAt(0) + role.slice(1).toLowerCase().replace(/_/g, ' ')
