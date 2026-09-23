import { useId, type ReactNode } from 'react'
import { cn } from 'cn'

type PanelProps = {
  title: string
  description?: ReactNode
  /** Right side of the header: controls, badges, freshness. */
  actions?: ReactNode
  className?: string
  bodyClassName?: string
  children: ReactNode
}

/** A flat, ruled workspace section. Meant to tile edge-to-edge, not float as a card. */
export function Panel({ title, description, actions, className, bodyClassName, children }: PanelProps) {
  const id = useId()
  return (
    <section aria-labelledby={id} className={cn('flex min-w-0 flex-col border bg-card', className)}>
      <header className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 border-b px-4 py-3">
        <div className="min-w-0">
          <h2 id={id} className="text-sm font-semibold tracking-tight">
            {title}
          </h2>
          {description && <p className="mt-0.5 text-xs text-muted-foreground">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </header>
      <div className={cn('min-h-0 flex-1', bodyClassName)}>{children}</div>
    </section>
  )
}
