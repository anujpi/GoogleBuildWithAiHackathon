import { CircleCheck, FlaskConical, Info, TriangleAlert, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from 'cn'

type Tone = 'info' | 'caution' | 'synthetic' | 'critical' | 'positive'

const tones: Record<Tone, { icon: LucideIcon; className: string }> = {
  info: { icon: Info, className: 'border-border bg-muted/60 [&>svg]:text-muted-foreground' },
  caution: { icon: TriangleAlert, className: 'border-caution/30 bg-caution/8 [&>svg]:text-caution' },
  synthetic: { icon: FlaskConical, className: 'border-caution/30 bg-caution/8 [&>svg]:text-caution' },
  critical: { icon: TriangleAlert, className: 'border-critical/30 bg-critical/6 [&>svg]:text-critical' },
  positive: { icon: CircleCheck, className: 'border-positive/30 bg-positive/8 [&>svg]:text-positive' },
}

/** An inline notice. Use role="alert" for errors that appear in response to an action. */
export function Callout({ tone, title, children, role, className }: { tone: Tone; title: string; children?: ReactNode; role?: 'alert' | 'status'; className?: string }) {
  const { icon: Icon, className: toneClass } = tones[tone]
  return (
    <div role={role} className={cn('flex gap-2.5 rounded-md border px-3 py-2.5 text-sm', toneClass, className)}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="min-w-0">
        <p className="font-medium">{title}</p>
        {children && <div className="mt-0.5 text-xs text-muted-foreground">{children}</div>}
      </div>
    </div>
  )
}
