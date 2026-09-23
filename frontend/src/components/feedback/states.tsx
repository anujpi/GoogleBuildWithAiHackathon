import { CircleSlash, LoaderCircle, RefreshCw, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'

function StateFrame({ children, role }: { children: ReactNode; role?: 'status' | 'alert' }) {
  return (
    <div role={role} className="flex min-h-32 flex-col items-center justify-center gap-2 p-6 text-center text-sm text-muted-foreground">
      {children}
    </div>
  )
}

export function LoadingState({ message }: { message: string }) {
  return (
    <StateFrame role="status">
      <LoaderCircle className="size-5 animate-spin" aria-hidden />
      <p>{message}</p>
    </StateFrame>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <StateFrame role="alert">
      <TriangleAlert className="size-5 text-warning" aria-hidden />
      <p className="text-foreground">{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RefreshCw aria-hidden /> Retry
        </Button>
      )}
    </StateFrame>
  )
}

type QueryLike<T> = { data: T | undefined; isPending: boolean; error: unknown; refetch: () => unknown }

/** Renders loading / error / empty / content for one TanStack query. */
export function AsyncContent<T>(p: {
  query: QueryLike<T>
  loadingMessage: string
  errorMessage: string
  emptyMessage?: string
  isEmpty?: (data: T) => boolean
  children: (data: T) => ReactNode
}) {
  const { data, isPending, error, refetch } = p.query
  if (isPending) return <LoadingState message={p.loadingMessage} />
  if (error || data === undefined) return <ErrorState message={p.errorMessage} onRetry={() => refetch()} />
  if (p.isEmpty?.(data)) return <EmptyState message={p.emptyMessage ?? 'Nothing to show yet.'} />
  return p.children(data)
}

export function EmptyState({ message, children }: { message: string; children?: ReactNode }) {
  return (
    <StateFrame>
      <CircleSlash className="size-5" aria-hidden />
      <p>{message}</p>
      {children}
    </StateFrame>
  )
}
