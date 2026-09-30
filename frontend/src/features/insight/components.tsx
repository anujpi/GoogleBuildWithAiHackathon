import { CircleCheck, CircleSlash, OctagonAlert, TriangleAlert } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { AvailabilityBadge, Pill, ProvenanceBadge, RiskIndicator, type Meta } from '@/components/data-display/status'
import { Field } from '@/components/forms/Field'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { Farm } from '@/features/farms/model'
import { describeError } from '@/lib/api/client'
import { formatDateTime, formatDay, formatNumber, formatPercent, humanize } from '@/lib/format'
import { useCropEvidence, useReferenceScope, useRisk, useWeather } from './api'
import type { CropCandidate, EvidenceItem, Provenance, Risk, RiskLevel, Tier } from './types'

// ---- small shared pieces ------------------------------------------------------------------

const na = <span className="text-muted-foreground">Unavailable</span>

/** A nullable number with its unit. null reads "Unavailable", never 0. */
export function Value({ v, unit, percent }: { v: number | null | undefined; unit?: string | null; percent?: boolean }) {
  if (v === null || v === undefined) return na
  return (
    <span className="tabular">
      {percent ? formatPercent(v) : formatNumber(v)}
      {unit && !percent && <span className="text-xs text-muted-foreground"> {unit}</span>}
    </span>
  )
}

/** Classification + source + time + notes for one provenance object. */
export function ProvenanceLine({ p }: { p: Provenance | null | undefined }) {
  if (!p) return <span className="text-xs text-muted-foreground">Provenance not provided</span>
  const at = p.retrievedAt ?? p.generatedAt
  return (
    <span className="inline-flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground" title={p.notes?.join(' ') || undefined}>
      <ProvenanceBadge classification={p.dataClassification} />
      <span>{p.source}</span>
      {p.modelVersion && <span>model {p.modelVersion}</span>}
      {p.dataThrough && <span>data through {p.dataThrough}</span>}
      {at && <time dateTime={at}>{formatDateTime(at)}</time>}
    </span>
  )
}

export function RiskBadge({ level, prefix }: { level: RiskLevel | null; prefix?: string }) {
  if (!level || level === 'UNAVAILABLE') return <AvailabilityBadge status="unavailable" />
  return <RiskIndicator level={level.toLowerCase() as Lowercase<Exclude<RiskLevel, 'UNAVAILABLE'>>} prefix={prefix} />
}

const tiers: Record<Tier, Meta> = {
  SUITABLE: { label: 'Suitable', icon: CircleCheck, tone: 'positive' },
  SUITABLE_WITH_CAUTION: { label: 'Suitable with caution', icon: TriangleAlert, tone: 'caution' },
  UNSUITABLE: { label: 'Unsuitable', icon: OctagonAlert, tone: 'critical' },
  NOT_SUPPORTED: { label: 'Not supported', icon: CircleSlash, tone: 'neutral' },
}

export const TierBadge = ({ tier }: { tier: Tier }) => <Pill meta={tiers[tier]} />

function Notes({ title, items }: { title: string; items: string[] | null | undefined }) {
  if (!items?.length) return null
  return (
    <div className="text-xs text-muted-foreground">
      <p className="font-medium text-foreground/80">{title}</p>
      <ul className="mt-1 list-disc space-y-0.5 pl-4">
        {items.map((t) => (
          <li key={t}>{t}</li>
        ))}
      </ul>
    </div>
  )
}

/** Crop evidence and risk need a district in the synced scope; say why they are missing instead of calling. */
function NotSupported({ farm }: { farm: Farm }) {
  return (
    <EmptyState message={farm.districtId ? `${farm.districtLabel ?? farm.districtId} has no supply series in the served scope yet.` : 'This farm has no canonical district.'}>
      <p className="max-w-md text-xs">
        {farm.districtId
          ? 'Crop evidence and risk become available once the reference data for this district is synced from the ML service.'
          : 'Edit the farm and choose a district from the supported list to enable crop evidence and risk.'}
      </p>
    </EmptyState>
  )
}

// ---- weather ------------------------------------------------------------------------------

export function WeatherPanel({ latitude, longitude, className }: { latitude: number; longitude: number; className?: string }) {
  const weather = useWeather({ latitude, longitude })
  const w = weather.data

  return (
    <Panel title="Weather" description="Open-Meteo via the backend, at the farm's coordinates" className={className}>
      {weather.isPending && <LoadingState message="Loading weather..." />}
      {weather.error && <ErrorState message={describeError(weather.error, 'weather')} onRetry={() => weather.refetch()} />}
      {w && (
        <div className="flex flex-col">
          <div className="flex flex-col gap-3 border-b p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-xs font-medium text-muted-foreground">
                Current conditions {w.current.time && <>· {formatDateTime(w.current.time)}</>}
              </h3>
              <ProvenanceLine p={w.current.provenance} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
              {(
                [
                  ['Temperature', w.current.temperatureC, '°C'],
                  ['Humidity', w.current.relativeHumidityPct, '%'],
                  ['Precipitation', w.current.precipitationMm, 'mm'],
                  ['Wind', w.current.windSpeedKmh, 'km/h'],
                ] as const
              ).map(([label, v, unit]) => (
                <div key={label}>
                  <dt className="text-xs text-muted-foreground">{label}</dt>
                  <dd className="text-base font-semibold">
                    <Value v={v} unit={unit} />
                  </dd>
                </div>
              ))}
            </dl>
            {w.current.provenance.notes?.map((n) => (
              <p key={n} className="text-xs text-muted-foreground">
                {n}
              </p>
            ))}
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-3">
            <h3 className="text-xs font-medium text-muted-foreground">Daily forecast</h3>
            <ProvenanceLine p={w.dailyProvenance} />
          </div>
          {w.daily.length === 0 ? (
            <EmptyState message="No daily forecast was returned." />
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-4">Date</TableHead>
                    <TableHead className="text-right">Min / max °C</TableHead>
                    <TableHead className="text-right">Rain mm</TableHead>
                    <TableHead className="text-right">Rain chance</TableHead>
                    <TableHead className="pr-4 text-right">Humidity</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {w.daily.map((d) => (
                    <TableRow key={d.date}>
                      <TableCell className="pl-4 whitespace-nowrap">{formatDay(d.date)}</TableCell>
                      <TableCell className="text-right whitespace-nowrap">
                        <Value v={d.minTemperatureC} /> / <Value v={d.maxTemperatureC} />
                      </TableCell>
                      <TableCell className="text-right">
                        <Value v={d.precipitationMm} />
                      </TableCell>
                      <TableCell className="text-right">
                        <Value v={d.precipitationProbabilityPct} unit="%" />
                      </TableCell>
                      <TableCell className="pr-4 text-right">
                        <Value v={d.relativeHumidityPct} unit="%" />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>
      )}
    </Panel>
  )
}

// ---- crop evidence ------------------------------------------------------------------------

function EvidenceRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}

function ItemView({ item }: { item: EvidenceItem | null }) {
  if (!item || item.status === 'UNAVAILABLE')
    return (
      <span className="flex flex-col gap-0.5">
        {na}
        {item?.basis && <span className="text-xs text-muted-foreground">{item.basis}</span>}
      </span>
    )
  return (
    <span className="flex flex-col gap-0.5">
      <span>
        {humanize(item.status)}
        {item.value !== null && (
          <>
            {' · '}
            <Value v={item.value} unit={item.unit} />
          </>
        )}
      </span>
      {item.basis && <span className="text-xs text-muted-foreground">{item.basis}</span>}
      {item.provenance && <ProvenanceLine p={item.provenance} />}
    </span>
  )
}

function CandidateView({ c }: { c: CropCandidate }) {
  const e = c.evidence
  const pe = e?.productionEvidence
  return (
    <li className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-semibold">{c.cropLabel}</h3>
        <TierBadge tier={c.tier} />
        {c.rank !== null && <span className="text-xs text-muted-foreground">Order within evidence rule: {c.rank}</span>}
      </div>
      {e && c.tier !== 'NOT_SUPPORTED' && (
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
          <EvidenceRow label="Soil compatibility">
            <ItemView item={e.soilCompatibility} />
          </EvidenceRow>
          <EvidenceRow label="Weather suitability">
            <ItemView item={e.weatherSuitability} />
          </EvidenceRow>
          <EvidenceRow label="Production evidence">
            {!pe || pe.status === 'UNAVAILABLE' ? (
              na
            ) : (
              <span className="flex flex-col gap-0.5">
                <span>
                  Mean yield <Value v={pe.meanYield?.value} unit={pe.meanYield?.unit} />
                </span>
                <span className="text-xs text-muted-foreground">
                  Yield CV <Value v={pe.coefficientOfVariation} /> · downside years <Value v={pe.downsideYearShare} percent />
                </span>
                <span className="text-xs text-muted-foreground">
                  Crop year {pe.cropYear ?? '—'} · {pe.servedMethod ? humanize(pe.servedMethod) : 'method not stated'}
                </span>
                <ProvenanceLine p={pe.provenance} />
              </span>
            )}
          </EvidenceRow>
          <EvidenceRow label="Market context">
            <ItemView item={e.marketContext} />
          </EvidenceRow>
          <EvidenceRow label="Production risk">
            <RiskBadge level={e.productionRisk} />
          </EvidenceRow>
        </dl>
      )}
      {c.reasons && c.reasons.length > 0 && (
        <ul className="list-disc space-y-0.5 pl-4 text-sm">
          {c.reasons.map((r) => (
            <li key={r.code + r.text}>{r.text}</li>
          ))}
        </ul>
      )}
      <Notes title="Unavailable evidence" items={c.unavailable?.map(humanize)} />
      <Notes title="Limitations" items={c.limitations} />
    </li>
  )
}

export function CropEvidencePanel({ farm }: { farm: Farm }) {
  const evidence = useCropEvidence(farm.id, farm.intelligenceSupported)
  const d = evidence.data
  return (
    <Panel
      title="Crop evidence"
      description={d ? `${d.season} season · rule ${d.rankingRule} · evidence tiers, not a score` : 'Evidence tiers per crop, not a single score'}
      actions={d && <span className="text-xs text-muted-foreground">Generated {formatDateTime(d.generatedAt)}</span>}
    >
      {!farm.intelligenceSupported ? (
        <NotSupported farm={farm} />
      ) : evidence.isPending ? (
        <LoadingState message="Loading crop evidence..." />
      ) : evidence.error ? (
        <ErrorState message={describeError(evidence.error, 'crop evidence')} onRetry={() => evidence.refetch()} />
      ) : d && d.candidates.length === 0 ? (
        <EmptyState message="No candidate crops were returned for this farm." />
      ) : (
        d && (
          <ul className="divide-y">
            {d.candidates.map((c) => (
              <CandidateView key={c.cropId} c={c} />
            ))}
          </ul>
        )
      )}
    </Panel>
  )
}

// ---- risk ---------------------------------------------------------------------------------

function RiskBlock({ title, risk }: { title: string; risk: Risk }) {
  return (
    <section aria-label={title} className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-semibold">{title}</h3>
        <RiskBadge level={risk.level} />
        <span className="text-xs text-muted-foreground">
          {risk.assessedFactors} assessed · {risk.unavailableFactors.length} unavailable
        </span>
      </div>
      {risk.factors.length > 0 && (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Factor</TableHead>
                <TableHead>Level</TableHead>
                <TableHead className="text-right">Value</TableHead>
                <TableHead>Source</TableHead>
                <TableHead>Reason</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {risk.factors.map((f) => (
                <TableRow key={f.code}>
                  <TableCell className="font-medium whitespace-nowrap">{humanize(f.code)}</TableCell>
                  <TableCell>
                    <RiskBadge level={f.level} />
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    <Value v={f.value} unit={f.unit} />
                  </TableCell>
                  <TableCell>
                    <span className="flex flex-col items-start gap-1 text-xs">
                      {f.dataClassification && <ProvenanceBadge classification={f.dataClassification} />}
                      <span className="text-muted-foreground">{f.source}</span>
                      {f.observedOrForecastFor && <span className="text-muted-foreground">{f.observedOrForecastFor}</span>}
                    </span>
                  </TableCell>
                  <TableCell className="min-w-64 text-xs whitespace-normal">
                    {f.reason}
                    {f.threshold && <span className="block text-muted-foreground">{f.threshold}</span>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      <Notes title="Not assessed" items={risk.unavailableFactors.map(humanize)} />
      <Notes title="Limitations" items={risk.limitations} />
    </section>
  )
}

export function RiskPanel({ farm }: { farm: Farm }) {
  const scope = useReferenceScope()
  const crops = scope.data?.crops ?? []
  // Default to the farm's current crop when it names a supported crop; the user can pick any other.
  const guess = crops.find((c) => [c.cropId, c.label.toLowerCase()].includes(farm.currentCrop?.trim().toLowerCase() ?? ''))
  const [picked, setPicked] = useState<string | null>(null)
  const cropId = picked ?? guess?.cropId ?? crops[0]?.cropId ?? null
  const risk = useRisk(farm.id, farm.intelligenceSupported ? cropId : null)
  const r = risk.data

  return (
    <Panel
      title="Risk"
      description={r ? `${r.season} season · crop year ${r.cropYear ?? 'unavailable'} · rule ${r.ruleSet}` : 'Production and market risk are assessed separately'}
      actions={
        farm.intelligenceSupported &&
        crops.length > 0 && (
          <Field label="Crop" className="flex-row items-center gap-2">
            {(a) => (
              <NativeSelect {...a} size="sm" value={cropId ?? ''} onChange={(e) => setPicked(e.target.value)}>
                {crops.map((c) => (
                  <NativeSelectOption key={c.cropId} value={c.cropId}>
                    {c.label}
                  </NativeSelectOption>
                ))}
              </NativeSelect>
            )}
          </Field>
        )
      }
    >
      {!farm.intelligenceSupported ? (
        <NotSupported farm={farm} />
      ) : scope.isPending ? (
        <LoadingState message="Loading supported crops..." />
      ) : scope.error ? (
        <ErrorState message={describeError(scope.error, 'the reference scope')} onRetry={() => scope.refetch()} />
      ) : cropId === null ? (
        <EmptyState message="Reference data not loaded yet: no crops are available." />
      ) : risk.isPending ? (
        <LoadingState message="Loading risk..." />
      ) : risk.error ? (
        <ErrorState message={describeError(risk.error, 'risk')} onRetry={() => risk.refetch()} />
      ) : (
        r && (
          <div className="divide-y">
            <RiskBlock title="Production risk" risk={r.productionRisk} />
            <RiskBlock title="Market risk" risk={r.marketRisk} />
          </div>
        )
      )}
    </Panel>
  )
}
