import { FileText } from 'lucide-react'
import { cn } from 'cn'
import { DataOriginBadge } from '@/components/data-display/status'
import type { DataOrigin } from '@/types/status'
import { classificationLabel, isBlank, SOIL_GROUPS, soilSourceLabel, type SoilClassification, type SoilMetric, type SoilSource } from './model'

const origin: Record<SoilClassification, DataOrigin> = { OBSERVED: 'observed', ESTIMATED: 'estimate', SYNTHETIC: 'synthetic' }

export function ClassificationBadge({ value }: { value: SoilClassification }) {
  return <DataOriginBadge origin={origin[value]} label={classificationLabel[value].label} />
}

/** Where soil values came from and how trustworthy they are. Regional estimates always read as estimated. */
export function SoilProvenance({ source, classification }: { source: SoilSource; classification: SoilClassification }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <ClassificationBadge value={classification} />
      {source === 'REGIONAL_ESTIMATE' && classification !== 'ESTIMATED' && <DataOriginBadge origin="estimate" label="Estimated" />}
      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
        <FileText className="size-3.5" aria-hidden />
        {soilSourceLabel[source].label}
      </span>
    </span>
  )
}

type Readings = Record<SoilMetric, number | string | null>

/** All twelve soil values grouped by kind. Missing values say so; they are never shown as 0. */
export function SoilReadings({ readings, className }: { readings: Readings; className?: string }) {
  return (
    <div className={cn('grid grid-cols-1 gap-x-8 gap-y-5 md:grid-cols-3', className)}>
      {SOIL_GROUPS.map((group) => (
        <section key={group.label} aria-label={group.label}>
          <h4 className="mb-1 text-xs font-medium text-muted-foreground">{group.label}</h4>
          <dl className="divide-y">
            {group.metrics.map((m) => {
              const v = readings[m.key]
              return (
                <div key={m.key} className="flex items-baseline justify-between gap-3 py-1.5 text-sm">
                  <dt>{m.label}</dt>
                  <dd className="tabular text-right">
                    {isBlank(v) ? (
                      <span className="text-xs text-muted-foreground italic">Not measured</span>
                    ) : (
                      <>
                        {v}
                        {m.unit && <span className="ml-1 text-xs text-muted-foreground">{m.unit}</span>}
                      </>
                    )}
                  </dd>
                </div>
              )
            })}
          </dl>
        </section>
      ))}
    </div>
  )
}
