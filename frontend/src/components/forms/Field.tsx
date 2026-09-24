import { CircleAlert } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { cn } from 'cn'

export type ControlA11y = { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }

type FieldProps = {
  label: string
  /** Shown after the label, e.g. a unit. */
  suffix?: string
  hint?: ReactNode
  error?: string
  optional?: boolean
  className?: string
  children: (control: ControlA11y) => ReactNode
}

/** Label + control + hint + error, with ids wired so screen readers announce all of them. */
export function Field({ label, suffix, hint, error, optional, className, children }: FieldProps) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [errorId, hintId].filter(Boolean).join(' ') || undefined

  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
      <label htmlFor={id} className="flex items-baseline gap-1.5 text-sm font-medium">
        {label}
        {suffix && <span className="text-xs font-normal text-muted-foreground">{suffix}</span>}
        {optional && <span className="ml-auto text-xs font-normal text-muted-foreground">Optional</span>}
      </label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {error && <FieldError id={errorId}>{error}</FieldError>}
      {hint && !error && (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
    </div>
  )
}

export function FieldError({ id, children }: { id?: string; children: ReactNode }) {
  return (
    <p id={id} className="flex items-start gap-1 text-xs font-medium text-destructive">
      <CircleAlert className="mt-px size-3.5 shrink-0" aria-hidden />
      {children}
    </p>
  )
}
