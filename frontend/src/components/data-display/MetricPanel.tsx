import type { ReactNode } from 'react'
import { cn } from 'cn'

type MetricPanelProps = {
  label: string
  value: ReactNode
  unit?: string
  /** Status line under the value: risk, trend, confidence. */
  status?: ReactNode
  footnote?: ReactNode
  className?: string
}

/** One headline metric. Designed to sit in a divided strip, not as a standalone card. */
export function MetricPanel({ label, value, unit, status, footnote, className }: MetricPanelProps) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-2 p-4', className)}>
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="flex flex-col gap-2">
        <span className="flex items-baseline gap-1">
          <span className="tabular text-2xl font-semibold tracking-tight">{value}</span>
          {unit && <span className="text-xs text-muted-foreground">{unit}</span>}
        </span>
        {status && <span className="flex flex-wrap items-center gap-2">{status}</span>}
        {footnote && <span className="text-xs text-muted-foreground">{footnote}</span>}
      </dd>
    </div>
  )
}
