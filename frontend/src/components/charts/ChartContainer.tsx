import type { ReactNode } from 'react'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'

type ChartContainerProps = {
  title: string
  /** e.g. "tonnes / week" — always required so no chart ships without units. */
  unit: string
  /** e.g. "Jun 2025 – Dec 2026". */
  timeContext?: string
  /** Plain-language summary for screen readers and for anyone who can't read the chart. */
  summary: string
  actions?: ReactNode
  legend?: ReactNode
  isLoading: boolean
  error: unknown
  onRetry?: () => void
  isEmpty: boolean
  loadingMessage: string
  errorMessage: string
  emptyMessage: string
  className?: string
  children: ReactNode
}

export function ChartContainer(p: ChartContainerProps) {
  const description = [p.unit, p.timeContext].filter(Boolean).join(' · ')
  let body: ReactNode
  if (p.isLoading) body = <LoadingState message={p.loadingMessage} />
  else if (p.error) body = <ErrorState message={p.errorMessage} onRetry={p.onRetry} />
  else if (p.isEmpty) body = <EmptyState message={p.emptyMessage} />
  else
    body = (
      <figure className="flex h-full flex-col gap-2 p-4">
        {p.legend}
        <div className="min-h-56 flex-1" aria-hidden>
          {p.children}
        </div>
        <figcaption className="text-xs text-muted-foreground">{p.summary}</figcaption>
      </figure>
    )

  return (
    <Panel title={p.title} description={description} actions={p.actions} className={p.className}>
      {body}
    </Panel>
  )
}
