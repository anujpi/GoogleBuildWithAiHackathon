import { useId, type ReactNode } from 'react'
import type { UseFormRegisterReturn } from 'react-hook-form'
import { cn } from 'cn'
import { FieldError } from './Field'

type Option = { value: string; label: string; hint?: string; badge?: ReactNode }

type ChoiceGroupProps = {
  legend: string
  description?: ReactNode
  options: Option[]
  register: UseFormRegisterReturn
  error?: string
  /** Grid columns at sm+; options wrap below that. */
  columns?: 2 | 3 | 4 | 5
  className?: string
}

const cols = { 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3', 4: 'sm:grid-cols-4', 5: 'sm:grid-cols-5' }

/** Native radio group styled as selectable tiles: arrow keys, labels and form reset all work for free. */
export function ChoiceGroup({ legend, description, options, register, error, columns = 3, className }: ChoiceGroupProps) {
  const id = useId()
  return (
    <fieldset className={cn('flex min-w-0 flex-col gap-2', className)} aria-describedby={error ? `${id}-error` : undefined} aria-invalid={Boolean(error)}>
      <legend className="mb-0.5 text-sm font-medium">{legend}</legend>
      {description && <div className="-mt-1 text-xs text-muted-foreground">{description}</div>}
      <div className={cn('grid grid-cols-1 gap-2 min-[420px]:grid-cols-2', cols[columns])}>
        {options.map((o) => (
          <label
            key={o.value}
            className={cn(
              'relative flex cursor-pointer flex-col gap-0.5 rounded-md border bg-card px-3 py-2.5 text-sm transition-colors',
              'hover:border-foreground/30 has-[:checked]:border-primary has-[:checked]:bg-primary/5 has-[:checked]:ring-1 has-[:checked]:ring-primary',
              'has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring',
              error && 'border-destructive/60',
            )}
          >
            <input type="radio" value={o.value} className="sr-only" {...register} />
            <span className="flex items-center justify-between gap-2 font-medium">
              {o.label}
              {o.badge}
            </span>
            {o.hint && <span className="text-xs text-muted-foreground">{o.hint}</span>}
          </label>
        ))}
      </div>
      {error && <FieldError id={`${id}-error`}>{error}</FieldError>}
    </fieldset>
  )
}
