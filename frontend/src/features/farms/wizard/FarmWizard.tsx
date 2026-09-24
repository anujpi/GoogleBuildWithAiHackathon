import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, ArrowRight, Check, LoaderCircle } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { FormProvider, useForm, useWatch, type FieldPath } from 'react-hook-form'
import { Link } from 'react-router'
import { cn } from 'cn'
import { Callout } from '@/components/feedback/Callout'
import { Button } from '@/components/ui/button'
import { ApiError, describeError } from '@/lib/api/client'
import { areaUnitLabel, countReadings, formatCoords, SOIL_METRICS, soilSourceLabel, type AreaUnit, type FarmRequest, type SoilSource } from '../model'
import { farmSchema, stepOfField, toRequest, type FarmFormValues } from '../schema'
import { FarmInfoStep } from './FarmInfoStep'
import { LocationStep } from './LocationStep'
import { ReviewStep } from './ReviewStep'
import { SoilStep } from './SoilStep'

const STEPS = [
  { title: 'Location', description: 'Where the farm is. Pick it on the map or enter coordinates.', fields: ['location'] },
  { title: 'Farm information', description: 'Size, water and what is being grown.', fields: ['name', 'area', 'areaUnit', 'irrigationType', 'currentCrop', 'previousCrop', 'season'] },
  { title: 'Soil', description: 'Soil test values and where they come from, if you have them.', fields: ['soilData', 'soilProfile'] },
  { title: 'Review', description: 'Check everything before saving. Nothing has been saved yet.', fields: [] },
] as const satisfies readonly { title: string; description: string; fields: readonly FieldPath<FarmFormValues>[] }[]

const LAST = STEPS.length - 1

type FarmWizardProps = {
  defaultValues: FarmFormValues
  mode: 'create' | 'edit'
  /** Resolves when saved; rejects with ApiError on failure. */
  onSave: (body: FarmRequest) => Promise<unknown>
  cancelTo: string
}

/** One-line summary of a finished step, shown in the step rail. */
function stepSummaries(values: FarmFormValues): string[] {
  const { location: l, soilProfile: s } = values
  const lat = Number(l.latitude)
  const lon = Number(l.longitude)
  return [
    [l.district, l.state].filter((x) => x.trim()).join(', ') || (l.latitude && l.longitude && Number.isFinite(lat + lon) ? formatCoords(lat, lon) : ''),
    [values.name, values.area && `${values.area} ${areaUnitLabel[values.areaUnit as AreaUnit]?.short ?? ''}`].filter(Boolean).join(' · '),
    values.soilData === 'unavailable'
      ? 'Soil data unavailable'
      : s.source
        ? `${soilSourceLabel[s.source as SoilSource].label} · ${countReadings(s)}/${SOIL_METRICS.length} values`
        : '',
    '',
  ]
}

export function FarmWizard({ defaultValues, mode, onSave, cancelTo }: FarmWizardProps) {
  const form = useForm({ resolver: zodResolver(farmSchema), defaultValues, mode: 'onTouched' })
  const { handleSubmit, trigger, setError, formState } = form
  const [step, setStep] = useState(0)
  // Editing an existing farm starts with every step valid, so all steps are reachable.
  const [reached, setReached] = useState(mode === 'edit' ? LAST : 0)
  const [saveError, setSaveError] = useState<{ message: string; unmapped: string[] } | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const firstRender = useRef(true)
  const summaries = stepSummaries(useWatch({ control: form.control }) as FarmFormValues)

  // Move focus to the new step's heading so keyboard and screen-reader users land in the right place.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    heading.current?.focus()
  }, [step])

  const goTo = (target: number) => {
    if (target <= reached) setStep(target)
  }

  const next = async () => {
    const valid = await trigger([...STEPS[step].fields], { shouldFocus: true })
    if (!valid) return
    setStep(step + 1)
    setReached((r) => Math.max(r, step + 1))
  }

  const save = handleSubmit(
    async (parsed) => {
      setSaveError(null)
      try {
        await onSave(toRequest(parsed))
      } catch (error) {
        const unmapped: string[] = []
        let firstStep: number | null = null
        if (error instanceof ApiError && (error.status === 400 || error.status === 422)) {
          for (const { field, message } of error.details) {
            const owner = stepOfField(field)
            if (owner === null) {
              unmapped.push(field ? `${field}: ${message}` : message)
              continue
            }
            setError(field as FieldPath<FarmFormValues>, { type: 'server', message })
            firstStep = Math.min(firstStep ?? owner, owner)
          }
        }
        setSaveError({ message: describeError(error, 'this farm'), unmapped })
        if (firstStep !== null) setStep(firstStep)
      }
    },
    // Only reachable when editing jumps straight to review with data that fails current rules.
    (errors) => {
      const first = STEPS.findIndex((s) => s.fields.some((f) => f in errors))
      if (first >= 0) setStep(first)
    },
  )

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    // Enter in an earlier step advances instead of submitting.
    if (step < LAST) {
      e.preventDefault()
      void next()
      return
    }
    void save(e)
  }

  const current = STEPS[step]
  const submitting = formState.isSubmitting

  return (
    <FormProvider {...form}>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[15rem_minmax(0,1fr)] lg:gap-8">
        <nav aria-label="Onboarding steps" className="lg:sticky lg:top-20 lg:self-start">
          {/* Compact progress for small screens */}
          <div className="lg:hidden">
            <p className="text-xs text-muted-foreground">
              Step {step + 1} of {STEPS.length}
            </p>
            <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-border" aria-hidden>
              <div className="h-full bg-primary transition-[width] duration-300" style={{ width: `${((step + 1) / STEPS.length) * 100}%` }} />
            </div>
          </div>

          <ol className="hidden flex-col lg:flex">
            {STEPS.map((s, i) => {
              const done = i !== step && i < reached
              const reachable = i <= reached
              return (
                <li key={s.title} className="relative flex gap-3 pb-6 last:pb-0">
                  {i < LAST && <span aria-hidden className={cn('absolute top-7 bottom-1 left-3 w-px', i < reached ? 'bg-primary/50' : 'bg-border')} />}
                  <button
                    type="button"
                    onClick={() => goTo(i)}
                    disabled={!reachable}
                    aria-current={i === step ? 'step' : undefined}
                    className="group flex min-w-0 gap-3 rounded-sm text-left outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-default"
                  >
                    <span
                      className={cn(
                        'flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-medium tabular',
                        i === step && 'border-primary bg-primary text-primary-foreground',
                        done && 'border-primary/50 bg-primary/10 text-primary',
                        i !== step && !done && 'bg-card text-muted-foreground',
                      )}
                    >
                      {done ? <Check className="size-3.5" aria-hidden /> : i + 1}
                    </span>
                    <span className="min-w-0 pt-0.5">
                      <span className={cn('block text-sm', i === step ? 'font-semibold' : reachable ? 'font-medium group-hover:underline' : 'text-muted-foreground')}>
                        {s.title}
                        {done && <span className="sr-only"> (completed)</span>}
                      </span>
                      {done && summaries[i] && <span className="mt-0.5 block truncate text-xs text-muted-foreground">{summaries[i]}</span>}
                    </span>
                  </button>
                </li>
              )
            })}
          </ol>
        </nav>

        <form noValidate onSubmit={onSubmit} aria-busy={submitting} className="flex min-w-0 flex-col border bg-card">
          <header className="border-b px-4 py-4 md:px-6">
            <p className="hidden text-xs font-medium text-muted-foreground lg:block">
              Step {step + 1} of {STEPS.length}
            </p>
            <h2 ref={heading} tabIndex={-1} className="mt-0.5 text-lg font-semibold tracking-tight outline-none">
              {current.title}
            </h2>
            <p className="mt-0.5 text-sm text-muted-foreground">{current.description}</p>
          </header>

          <div key={step} className="flex-1 px-4 py-6 duration-200 animate-in fade-in slide-in-from-bottom-1 md:px-6">
            {step === 0 && <LocationStep />}
            {step === 1 && <FarmInfoStep />}
            {step === 2 && <SoilStep />}
            {step === 3 && <ReviewStep onEdit={goTo} />}
          </div>

          {saveError && (
            <div className="px-4 pb-4 md:px-6">
              <Callout tone="critical" role="alert" title={saveError.message}>
                {saveError.unmapped.length > 0 && (
                  <ul className="list-disc pl-4">
                    {saveError.unmapped.map((m) => (
                      <li key={m}>{m}</li>
                    ))}
                  </ul>
                )}
                {!(saveError.unmapped.length > 0) && 'Your entries are kept. Fix any highlighted fields and try again.'}
              </Callout>
            </div>
          )}

          <footer className="sticky bottom-0 flex items-center gap-2 border-t bg-card/95 px-4 py-3 backdrop-blur md:px-6">
            {step > 0 ? (
              <Button type="button" variant="outline" onClick={() => setStep(step - 1)} disabled={submitting}>
                <ArrowLeft aria-hidden /> Back
              </Button>
            ) : (
              <Button asChild variant="ghost">
                <Link to={cancelTo}>Cancel</Link>
              </Button>
            )}
            <div className="ml-auto flex items-center gap-2">
              {step < LAST ? (
                <Button type="submit">
                  Continue <ArrowRight aria-hidden />
                </Button>
              ) : (
                <Button type="submit" disabled={submitting}>
                  {submitting ? <LoaderCircle className="animate-spin" aria-hidden /> : <Check aria-hidden />}
                  {submitting ? (mode === 'create' ? 'Creating farm…' : 'Saving…') : mode === 'create' ? 'Create Farm' : 'Save changes'}
                </Button>
              )}
            </div>
          </footer>
        </form>
      </div>
    </FormProvider>
  )
}
