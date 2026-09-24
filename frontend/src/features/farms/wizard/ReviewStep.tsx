import { Pencil } from 'lucide-react'
import type { ReactNode } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'
import { AvailabilityBadge, ConfidenceBadge } from '@/components/data-display/status'
import { Button } from '@/components/ui/button'
import { formatDay } from '@/lib/format'
import { SoilProvenance, SoilReadings } from '../components'
import {
  areaUnitLabel,
  countReadings,
  formatCoords,
  irrigationLabel,
  seasonLabel,
  SOIL_METRICS,
  type AreaUnit,
  type IrrigationType,
  type Season,
  type SoilClassification,
  type SoilSource,
} from '../model'
import type { FarmFormValues } from '../schema'

function Section({ title, step, onEdit, children }: { title: string; step: number; onEdit: (step: number) => void; children: ReactNode }) {
  return (
    <section aria-label={title} className="border-t pt-4 first:border-t-0 first:pt-0">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold">{title}</h3>
        <Button type="button" variant="ghost" size="sm" onClick={() => onEdit(step)}>
          <Pencil aria-hidden /> Edit <span className="sr-only">{title.toLowerCase()}</span>
        </Button>
      </div>
      {children}
    </section>
  )
}

function Facts({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
      {items.map(([label, value]) => (
        <div key={label} className="min-w-0">
          <dt className="text-xs text-muted-foreground">{label}</dt>
          <dd className="mt-0.5 text-sm break-words">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

const none = <span className="text-muted-foreground italic">Not provided</span>
const orNone = (v: string) => (v.trim() ? v.trim() : none)

export function ReviewStep({ onEdit }: { onEdit: (step: number) => void }) {
  const { control } = useFormContext<FarmFormValues>()
  const v = useWatch({ control })
  const loc = v.location!
  const soil = v.soilProfile!
  const readings = Object.fromEntries(SOIL_METRICS.map((k) => [k, soil[k]?.trim() ?? ''])) as Record<(typeof SOIL_METRICS)[number], string>

  return (
    <div className="flex flex-col gap-5">
      <Section title="Location" step={0} onEdit={onEdit}>
        <Facts
          items={[
            ['Coordinates', <span className="tabular">{formatCoords(Number(loc.latitude), Number(loc.longitude))}</span>],
            ['State', orNone(loc.state ?? '')],
            ['District', orNone(loc.district ?? '')],
            ['Taluk', orNone(loc.taluk ?? '')],
            ['Address label', orNone(loc.addressLabel ?? '')],
          ]}
        />
      </Section>

      <Section title="Farm" step={1} onEdit={onEdit}>
        <Facts
          items={[
            ['Name', v.name],
            ['Area', <span className="tabular">{v.area} {areaUnitLabel[v.areaUnit as AreaUnit]?.long.toLowerCase()}</span>],
            ['Irrigation', irrigationLabel[v.irrigationType as IrrigationType]],
            ['Season', seasonLabel[v.season as Season]?.label],
            ['Current crop', orNone(v.currentCrop ?? '')],
            ['Previous crop', orNone(v.previousCrop ?? '')],
          ]}
        />
      </Section>

      <Section title="Soil" step={2} onEdit={onEdit}>
        {v.soilData === 'unavailable' ? (
          <div className="flex flex-col items-start gap-2 text-sm">
            <span className="inline-flex items-center gap-2 font-medium">
              Soil data unavailable <AvailabilityBadge status="unavailable" />
            </span>
            <p className="text-muted-foreground">This farm will be saved without a soil profile. No soil values are estimated or filled in.</p>
          </div>
        ) : (
          <>
            <p className="mb-2 text-sm font-medium">Soil data provided</p>
            <div className="mb-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-muted-foreground">
              <SoilProvenance source={soil.source as SoilSource} classification={soil.dataClassification as SoilClassification} />
              <span>{soil.measuredAt ? `Measured ${formatDay(soil.measuredAt)}` : 'Measurement date not provided'}</span>
              {soil.confidence?.trim() ? <ConfidenceBadge value={Number(soil.confidence)} /> : <span>Confidence not provided</span>}
              <span>
                {countReadings(readings)} of {SOIL_METRICS.length} values entered
              </span>
            </div>
            <SoilReadings readings={readings} />
          </>
        )}
      </Section>
    </div>
  )
}
