import { ArrowLeft, CalendarClock, Pencil } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { AvailabilityBadge, ConfidenceBadge } from '@/components/data-display/status'
import { Callout } from '@/components/feedback/Callout'
import { ErrorState, LoadingState } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'
import { PageHeader } from '@/components/layout/PageHeader'
import { LocationPicker } from '@/components/maps/LocationPicker'
import { Button } from '@/components/ui/button'
import { ApiError, describeError } from '@/lib/api/client'
import { formatDateTime, formatDay, formatNumber } from '@/lib/format'
import { SoilProvenance, SoilReadings } from './components'
import type { SavedState } from './FarmFormPages'
import { useFarm } from './hooks'
import { areaUnitLabel, countReadings, formatCoords, irrigationLabel, locationLabel, seasonLabel, SOIL_METRICS, soilOf, type Farm, type SoilProfile } from './model'

// Modules that will attach to a farm later. Listed, not faked.
const upcoming = [
  { label: 'Weather & environment', phase: 3 },
  { label: 'Crop intelligence', phase: 4 },
  { label: 'Supply & demand', phase: 7 },
  { label: 'Market intelligence', phase: 7 },
  { label: 'Production & market risk', phase: 8 },
]

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1 bg-card p-4">
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">{children}</dd>
    </div>
  )
}

const muted = (text: string) => <span className="font-normal text-muted-foreground">{text}</span>

function SoilPanel({ soil, editTo }: { soil: SoilProfile | null; editTo: string }) {
  if (!soil) {
    return (
      <Panel title="Soil profile" description="No soil data recorded for this farm" actions={<AvailabilityBadge status="unavailable" />}>
        <div className="flex flex-col items-start gap-3 p-4 text-sm text-muted-foreground">
          <p>
            Soil data is unavailable. No values are estimated or filled in. Soil-dependent intelligence will treat this as a missing input and
            report lower confidence.
          </p>
          <Button asChild variant="outline" size="sm">
            <Link to={editTo}>
              <Pencil aria-hidden /> Add soil data
            </Link>
          </Button>
        </div>
      </Panel>
    )
  }

  return (
    <Panel
      title="Soil profile"
      description={`${countReadings(soil)} of ${SOIL_METRICS.length} values recorded`}
      actions={<SoilProvenance source={soil.source} classification={soil.dataClassification} />}
    >
      <div className="flex flex-col gap-4 p-4">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
          <span>{soil.measuredAt ? `Measured ${formatDay(soil.measuredAt)}` : 'Measurement date not recorded'}</span>
          {soil.confidence !== null ? <ConfidenceBadge value={soil.confidence} /> : <span>Confidence not recorded</span>}
        </div>
        {soil.source === 'REGIONAL_ESTIMATE' && (
          <Callout tone="caution" title="Estimated — not a measurement of this farm">
            These values describe typical soil in the surrounding area, not a laboratory test of this farm.
          </Callout>
        )}
        {soil.dataClassification === 'SYNTHETIC' && (
          <Callout tone="synthetic" title="Synthetic — demo data">
            These soil values are not real and must not be used for decisions.
          </Callout>
        )}
        <SoilReadings readings={soil} />
      </div>
    </Panel>
  )
}

function FarmProfile({ farm }: { farm: Farm }) {
  const loc = farm.location

  return (
    <>
      <dl aria-label="Farm facts" className="flex flex-wrap gap-px border bg-border *:min-w-0 *:flex-1 *:basis-40">
        <Fact label="Area">
          <span className="tabular text-lg font-semibold">{formatNumber(farm.area)}</span> {muted(areaUnitLabel[farm.areaUnit].long.toLowerCase())}
        </Fact>
        <Fact label="Current crop">{farm.currentCrop ?? muted('Not recorded')}</Fact>
        <Fact label="Previous crop">{farm.previousCrop ?? muted('Not recorded')}</Fact>
        <Fact label="Season">
          {seasonLabel[farm.season].label} {muted(`· ${seasonLabel[farm.season].hint}`)}
        </Fact>
        <Fact label="Irrigation">{irrigationLabel[farm.irrigationType]}</Fact>
      </dl>

      <div className="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <SoilPanel soil={soilOf(farm)} editTo={`/farms/${farm.id}/edit`} />

        <div className="flex flex-col gap-4">
          <Panel title="Location" description={<span className="tabular">{formatCoords(loc.latitude, loc.longitude)}</span>}>
            <LocationPicker label={`Map showing ${farm.name}`} value={{ lat: loc.latitude, lon: loc.longitude }} className="h-52" />
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 border-t p-4 text-sm">
              {(
                [
                  ['State', loc.state],
                  ['District', loc.district],
                  ['Taluk', loc.taluk],
                  ['Address label', loc.addressLabel],
                ] as const
              ).map(([label, value]) => (
                <div key={label} className="min-w-0">
                  <dt className="text-xs text-muted-foreground">{label}</dt>
                  <dd className="mt-0.5 break-words">{value ?? muted('Not recorded')}</dd>
                </div>
              ))}
            </dl>
          </Panel>

          <Panel title="Record">
            <dl className="grid grid-cols-1 gap-3 p-4 text-sm">
              <div>
                <dt className="text-xs text-muted-foreground">Created</dt>
                <dd>
                  <time dateTime={farm.createdAt}>{formatDateTime(farm.createdAt)}</time>
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Last updated</dt>
                <dd>
                  <time dateTime={farm.updatedAt}>{formatDateTime(farm.updatedAt)}</time>
                </dd>
              </div>
              <div className="min-w-0">
                <dt className="text-xs text-muted-foreground">Farm ID</dt>
                <dd className="font-mono text-xs break-all">{farm.id}</dd>
              </div>
            </dl>
          </Panel>
        </div>
      </div>

      <Panel title="Farm intelligence" description="Modules that will attach to this farm as they are built.">
        <ul className="grid grid-cols-1 divide-y sm:grid-cols-2 sm:divide-y-0 lg:grid-cols-5 lg:divide-x">
          {upcoming.map((m) => (
            <li key={m.label} className="flex flex-col gap-1 px-4 py-3">
              <span className="text-sm font-medium">{m.label}</span>
              <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                <CalendarClock className="size-3.5" aria-hidden /> Not yet available · phase {m.phase}
              </span>
            </li>
          ))}
        </ul>
      </Panel>
    </>
  )
}

export function FarmProfilePage() {
  const { id = '' } = useParams()
  const saved = (useLocation().state as SavedState | null)?.saved
  const farm = useFarm(id)
  const notFound = farm.error instanceof ApiError && farm.error.status === 404

  return (
    <div className="flex flex-col gap-5">
      <title>{`${farm.data?.name ?? 'Farm'} · Agri Intelligence`}</title>
      <Link to="/farms" className="inline-flex w-fit items-center gap-1 rounded-sm text-xs font-medium text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring">
        <ArrowLeft className="size-3.5" aria-hidden /> All farms
      </Link>

      {saved && farm.data && (
        <Callout tone="positive" role="status" title={saved === 'created' ? 'Farm created' : 'Changes saved'}>
          {farm.data.name} is saved.
        </Callout>
      )}

      {farm.isPending && <LoadingState message="Loading farm profile..." />}
      {farm.error && (
        <ErrorState message={describeError(farm.error, 'this farm')} onRetry={notFound ? undefined : () => farm.refetch()} />
      )}
      {farm.data && (
        <>
          <PageHeader
            title={farm.data.name}
            description={locationLabel(farm.data.location)}
            actions={
              <Button asChild variant="outline">
                <Link to={`/farms/${id}/edit`}>
                  <Pencil aria-hidden /> Edit farm
                </Link>
              </Button>
            }
          />
          <FarmProfile farm={farm.data} />
        </>
      )}
    </div>
  )
}
