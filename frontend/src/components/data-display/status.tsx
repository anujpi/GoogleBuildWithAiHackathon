import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  CircleCheck,
  CircleDashed,
  CircleDot,
  Clock,
  Eye,
  FlaskConical,
  Minus,
  OctagonAlert,
  Sigma,
  TrendingDown,
  TrendingUp,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react'
import { cn } from 'cn'
import { formatDateTime, formatPercent, formatRelative } from '@/lib/format'
import type { Availability, Confidence, DataOrigin, GapState, RiskLevel, Trend } from '@/types/status'

type Tone = 'positive' | 'caution' | 'warning' | 'critical' | 'neutral'

const toneClass: Record<Tone, string> = {
  positive: 'text-positive bg-positive/10 border-positive/25',
  caution: 'text-caution bg-caution/12 border-caution/30',
  warning: 'text-warning bg-warning/10 border-warning/30',
  critical: 'text-critical bg-critical/10 border-critical/30',
  neutral: 'text-muted-foreground bg-muted border-border',
}

type Meta = { label: string; icon: LucideIcon; tone: Tone }

function Pill({ meta, prefix, className }: { meta: Meta; prefix?: string; className?: string }) {
  const Icon = meta.icon
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center gap-1.5 rounded-sm border px-2 text-xs font-medium whitespace-nowrap',
        toneClass[meta.tone],
        className,
      )}
    >
      <Icon className="size-3.5 shrink-0" aria-hidden />
      {prefix && <span className="text-foreground/60">{prefix}</span>}
      {meta.label}
    </span>
  )
}

const risk: Record<RiskLevel, Meta> = {
  low: { label: 'Low', icon: CircleCheck, tone: 'positive' },
  moderate: { label: 'Moderate', icon: CircleDot, tone: 'caution' },
  high: { label: 'High', icon: TriangleAlert, tone: 'warning' },
  critical: { label: 'Critical', icon: OctagonAlert, tone: 'critical' },
}

export function RiskIndicator({ level, prefix }: { level: RiskLevel; prefix?: string }) {
  return <Pill meta={risk[level]} prefix={prefix} />
}

const origin: Record<DataOrigin, Meta> = {
  observed: { label: 'Observed', icon: Eye, tone: 'neutral' },
  forecast: { label: 'Forecast', icon: TrendingUp, tone: 'neutral' },
  model: { label: 'Model prediction', icon: Sigma, tone: 'neutral' },
  estimate: { label: 'Estimate', icon: CircleDashed, tone: 'caution' },
  synthetic: { label: 'Synthetic', icon: FlaskConical, tone: 'caution' },
}

export function DataOriginBadge({ origin: o, label }: { origin: DataOrigin; label?: string }) {
  return <Pill meta={label ? { ...origin[o], label } : origin[o]} className="h-5 px-1.5 text-[11px]" />
}

const availability: Record<Availability, Meta> = {
  healthy: { label: 'Healthy', icon: CircleCheck, tone: 'positive' },
  delayed: { label: 'Delayed', icon: Clock, tone: 'caution' },
  degraded: { label: 'Degraded', icon: TriangleAlert, tone: 'warning' },
  unavailable: { label: 'Unavailable', icon: OctagonAlert, tone: 'critical' },
}

export function AvailabilityBadge({ status }: { status: Availability }) {
  return <Pill meta={availability[status]} />
}

const gap: Record<GapState, Meta> = {
  surplus: { label: 'Surplus', icon: ArrowUp, tone: 'warning' },
  balanced: { label: 'Balanced', icon: Minus, tone: 'positive' },
  shortage: { label: 'Shortage', icon: ArrowDown, tone: 'critical' },
}

export function GapBadge({ state }: { state: GapState }) {
  return <Pill meta={gap[state]} />
}

const trend: Record<Trend, { label: string; icon: LucideIcon }> = {
  up: { label: 'Rising', icon: TrendingUp },
  flat: { label: 'Stable', icon: ArrowRight },
  down: { label: 'Falling', icon: TrendingDown },
}

export function TrendLabel({ value }: { value: Trend }) {
  const { label, icon: Icon } = trend[value]
  return (
    <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
      <Icon className="size-3.5" aria-hidden />
      {label}
    </span>
  )
}

/** Confidence is shown as a band + exact value + a small meter, so it never reads as certainty. */
export function ConfidenceBadge({ value }: { value: Confidence }) {
  const band = value >= 0.75 ? 'High' : value >= 0.5 ? 'Medium' : 'Low'
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground" title="Model confidence">
      <span className="relative h-1.5 w-8 overflow-hidden rounded-full bg-border" aria-hidden>
        <span className="absolute inset-y-0 left-0 bg-foreground/60" style={{ width: formatPercent(value) }} />
      </span>
      <span>
        <span className="sr-only">Confidence: </span>
        {band} <span className="tabular text-foreground/70">{formatPercent(value)}</span>
      </span>
    </span>
  )
}

export function DataFreshness({ updatedAt }: { updatedAt: string }) {
  return (
    <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
      <Clock className="size-3.5" aria-hidden />
      <span>
        Updated{' '}
        <time dateTime={updatedAt} title={formatDateTime(updatedAt)}>
          {formatRelative(updatedAt)}
        </time>
      </span>
    </span>
  )
}
