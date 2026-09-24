import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { ErrorState, LoadingState } from '@/components/feedback/states'
import { Button } from '@/components/ui/button'
import { describeError, getAccessToken, setAccessToken, setUnauthorizedHandler } from '@/lib/api/client'
import { getMe, login as postLogin, type Credentials } from './api'
import { AuthContext, useAuth, type Auth, type LoginState, type SessionEnd } from './context'

const ME = ['auth', 'me'] as const

/** Root route element: owns the session for everything below it. */
export function AuthProvider() {
  const queryClient = useQueryClient()
  const [token, setToken] = useState(getAccessToken)
  const [endedReason, setEndedReason] = useState<SessionEnd | null>(null)

  const me = useQuery({ queryKey: ME, queryFn: ({ signal }) => getMe(signal), enabled: token !== null, staleTime: Infinity })

  // No navigation here: RequireAuth sees the missing session and redirects, so there is exactly one redirect.
  const endSession = useCallback(
    (reason: SessionEnd) => {
      setAccessToken(null)
      setToken(null)
      setEndedReason(reason)
      // Drop every cached response so the next user never sees this one's farms.
      queryClient.clear()
    },
    [queryClient],
  )

  useEffect(() => {
    setUnauthorizedHandler(() => endSession('expired'))
    return () => setUnauthorizedHandler(null)
  }, [endSession])

  const login = useCallback(
    async (credentials: Credentials) => {
      const { accessToken } = await postLogin(credentials)
      setAccessToken(accessToken)
      queryClient.clear()
      try {
        const user = await queryClient.fetchQuery({ queryKey: ME, queryFn: ({ signal }) => getMe(signal) })
        setToken(accessToken)
        setEndedReason(null)
        return user
      } catch (error) {
        setAccessToken(null)
        throw error
      }
    },
    [queryClient],
  )

  const value = useMemo<Auth>(
    () => ({
      currentUser: token ? (me.data ?? null) : null,
      isAuthenticated: token !== null && me.data !== undefined,
      isLoading: token !== null && me.isPending && !me.error,
      error: token ? me.error : null,
      retry: () => void me.refetch(),
      login,
      logout: () => endSession('signed-out'),
      endedReason,
    }),
    [token, me, login, endSession, endedReason],
  )

  return (
    <AuthContext value={value}>
      <Outlet />
    </AuthContext>
  )
}

function Centered({ children }: { children: ReactNode }) {
  return <div className="flex min-h-svh items-center justify-center bg-background p-4">{children}</div>
}

/** Wraps protected routes. Sends signed-out visitors to /login and remembers where they were going. */
export function RequireAuth() {
  const auth = useAuth()
  const { pathname, search } = useLocation()
  if (auth.isLoading) return <Centered><LoadingState message="Checking your session..." /></Centered>
  if (auth.error)
    return (
      <Centered>
        <div className="flex flex-col items-center">
          <ErrorState message={describeError(auth.error, 'your account')} onRetry={auth.retry} />
          <Button variant="ghost" size="sm" onClick={auth.logout}>Sign out</Button>
        </div>
      </Centered>
    )
  if (!auth.isAuthenticated) return (
      // After a deliberate sign-out, the next sign-in starts fresh at the dashboard.
      <Navigate to="/login" replace state={{ from: auth.endedReason === 'signed-out' ? undefined : pathname + search } satisfies LoginState} />
    )
  return <Outlet />
}

/** Wraps /login and /register. Signed-in users go to the app, so a successful login redirects from here. */
export function PublicOnly() {
  const auth = useAuth()
  const from = (useLocation().state as LoginState)?.from
  if (auth.isLoading) return <Centered><LoadingState message="Checking your session..." /></Centered>
  if (auth.isAuthenticated) return <Navigate to={from?.startsWith('/') && !from.startsWith('//') ? from : '/dashboard'} replace />
  return <Outlet />
}
