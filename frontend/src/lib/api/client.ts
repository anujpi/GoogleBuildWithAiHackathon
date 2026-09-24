// The one place the app talks HTTP. Feature api.ts files call `api()`, hooks call those, components call hooks.

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/+$/, '')

/** Mirrors the backend's ApiError.FieldViolation. `field` uses dot paths, e.g. "location.latitude". */
export type FieldViolation = { field: string; message: string }

/** Every failed request becomes one of these. status 0 = the server could not be reached. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: FieldViolation[]

  constructor(status: number, code: string, message: string, details: FieldViolation[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

// The access token lives here and nowhere else. Swap these for cookies later without touching callers.
const TOKEN_KEY = 'agri.accessToken'
let accessToken: string | null = null
try {
  accessToken = localStorage.getItem(TOKEN_KEY)
} catch {
  // Storage blocked (private mode): the session lasts until the tab closes.
}

export const getAccessToken = () => accessToken

export function setAccessToken(token: string | null) {
  accessToken = token
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // See above.
  }
}

let onUnauthorized: (() => void) | null = null
/** Called once when an authenticated request comes back 401. The auth layer ends the session. */
export const setUnauthorizedHandler = (handler: (() => void) | null) => {
  onUnauthorized = handler
}

type RequestOptions = { method?: 'GET' | 'POST' | 'PUT' | 'DELETE'; body?: unknown; signal?: AbortSignal }

export async function api<T>(path: string, { method = 'GET', body, signal }: RequestOptions = {}): Promise<T> {
  const token = accessToken
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method,
      signal,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError(0, 'NETWORK_ERROR', `Could not reach the server at ${API_BASE_URL}.`)
  }

  if (res.ok) return (res.status === 204 ? undefined : await res.json()) as T

  // A rejected token means the session is over. Only when a token was sent: a failed login is also a 401.
  if (res.status === 401 && token && token === accessToken) {
    setAccessToken(null)
    onUnauthorized?.()
  }

  // The backend always answers errors with an ApiError body; a proxy or crash might not.
  const payload = await res.json().catch(() => null)
  throw new ApiError(
    res.status,
    typeof payload?.code === 'string' ? payload.code : `HTTP_${res.status}`,
    typeof payload?.message === 'string' ? payload.message : res.statusText || 'Request failed',
    Array.isArray(payload?.details) ? payload.details : [],
  )
}

/** A user-facing sentence for any error, phrased for the thing that failed ("the farm list"). */
export function describeError(error: unknown, subject: string): string {
  if (!(error instanceof ApiError)) return `Something went wrong while loading ${subject}.`
  if (error.status === 0) return `Could not reach the server. Check that the backend is running at ${API_BASE_URL}.`
  if (error.status === 401) return 'Your session has ended. Sign in again to continue.'
  if (error.status === 403) return `You do not have access to ${subject}.`
  if (error.status === 400 || error.status === 422) return error.details.length ? 'Some details need attention before this can be saved.' : error.message
  if (error.status === 404) return `${subject[0].toUpperCase()}${subject.slice(1)} could not be found. It may have been removed.`
  if (error.status >= 500) return `The server hit an unexpected error with ${subject}. Nothing was lost on your side — try again.`
  return error.message
}

/** 4xx errors will not fix themselves, so don't retry them. */
export const shouldRetry = (failureCount: number, error: unknown) =>
  !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 1
