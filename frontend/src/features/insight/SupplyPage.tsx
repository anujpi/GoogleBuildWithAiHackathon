import { Search } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Field } from '@/components/forms/Field'
import { Callout } from '@/components/feedback/Callout'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { describeError } from '@/lib/api/client'
import { formatNumber, formatPercent, humanize } from '@/lib/format'
import { useReferenceScope, useSupply } from './api'
import { ProvenanceLine, Value } from './components'
import type { ReferenceScope, SupplyEstimate, SupplyQuery } from './types'

const uniq = <T,>(xs: T[]) => [...new Set(xs)]

function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: { value: string; label: string }[] }) {
  return (
    <Field label={label}>
      {(a) => (
        <NativeSelect {...a} className="w-full" value={value} onChange={(e) => onChange(e.target.value)} disabled={options.length === 0}>
          {options.map((o) => (
            <NativeSelectOption key={o.value} value={o.value}>
              {o.label}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      )}
    </Field>
  )
}

/** Every choice comes from the synced supply series, so only in-scope combinations can be requested. */
function SupplyForm({ scope, onSubmit }: { scope: ReferenceScope; onSubmit: (q: SupplyQuery) => void }) {
  const series = scope.supplySeries
  const label = (list: { id: string; label: string }[], id: string) => list.find((x) => x.id === id)?.label ?? id
  const districts = scope.districts.map((d) => ({ id: d.districtId, label: d.label }))
  const crops = scope.crops.map((c) => ({ id: c.cropId, label: c.label }))

  const [districtId, setDistrict] = useState(series[0].districtId)
  const cropIds = uniq(series.filter((s) => s.districtId === districtId).map((s) => s.cropId))
  const [cropPick, setCrop] = useState(series[0].cropId)
  const cropId = cropIds.includes(cropPick) ? cropPick : cropIds[0]
  const seasons = uniq(series.filter((s) => s.districtId === districtId && s.cropId === cropId).map((s) => s.season))
  const [seasonPick, setSeason] = useState(series[0].season)
  const season = seasons.includes(seasonPick) ? seasonPick : seasons[0]
  const years = series.find((s) => s.districtId === districtId && s.cropId === cropId && s.season === season)?.estimableYears ?? []
  const [yearPick, setYear] = useState<number | null>(null)
  const cropYear = yearPick !== null && years.includes(yearPick) ? yearPick : years[years.length - 1]

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (cropYear !== undefined) onSubmit({ districtId, cropId, season, cropYear })
  }

  return (
    <form onSubmit={submit} className="grid grid-cols-1 items-end gap-3 p-4 sm:grid-cols-2 lg:grid-cols-[repeat(4,minmax(0,1fr))_auto]">
      <Select label="District" value={districtId} onChange={setDistrict} options={uniq(series.map((s) => s.districtId)).map((id) => ({ value: id, label: label(districts, id) }))} />
      <Select label="Crop" value={cropId} onChange={setCrop} options={cropIds.map((id) => ({ value: id, label: label(crops, id) }))} />
      <Select label="Season" value={season} onChange={setSeason} options={seasons.map((s) => ({ value: s, label: humanize(s) }))} />
      <Select label="Crop year" value={String(cropYear ?? '')} onChange={(v) => setYear(Number(v))} options={[...years].reverse().map((y) => ({ value: String(y), label: String(y) }))} />
      <Button type="submit" disabled={cropYear === undefined}>
        <Search aria-hidden /> Get estimate
      </Button>
    </form>
  )
}

function Stat({ label, children, note }: { label: string; children: ReactNode; note?: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1 bg-card p-4">
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="text-lg font-semibold">{children}</dd>
      {note && <dd className="text-xs text-muted-foreground">{note}</dd>}
    </div>
  )
}

function SupplyResult({ s }: { s: SupplyEstimate }) {
  const { estimate: e, target: t } = s
  const interval = e.production.interval
  const points = s.history?.points ?? []

  return (
    <div className="flex flex-col gap-4">
      <Callout tone="caution" title="Historical production intelligence — not a current-season forecast">
        {t.cropLabel ?? t.cropId} in {t.districtLabel ?? t.districtId}, {humanize(t.season)} season, crop year {t.cropYear}. The underlying data ends in crop
        year {s.provenance.dataThrough ?? 'unknown'}.
      </Callout>

      <Panel title="Production estimate" description={`Served method: ${humanize(e.servedMethod)}`} actions={<ProvenanceLine p={s.provenance} />}>
        <dl className="flex flex-wrap gap-px bg-border *:min-w-0 *:flex-1 *:basis-48">
          <Stat
            label="Production"
            note={interval && `${formatPercent(interval.nominalCoverage)} interval ${formatNumber(interval.lower)}–${formatNumber(interval.upper)} ${e.production.unit}`}
          >
            <Value v={e.production.value} unit={e.production.unit} />
          </Stat>
          <Stat label="Yield" note={e.yield.interval && `Interval ${formatNumber(e.yield.interval.lower)}–${formatNumber(e.yield.interval.upper)} ${e.yield.unit}`}>
            <Value v={e.yield.value} unit={e.yield.unit} />
          </Stat>
          <Stat label="Area" note={`Area source: ${humanize(e.area.areaSource)}`}>
            <Value v={e.area.value} unit={e.area.unit} />
          </Stat>
          <Stat label="Baseline production" note={s.baseline ? `Method: ${s.baseline.method}` : undefined}>
            <Value v={s.baseline?.production.value} unit={s.baseline?.production.unit} />
          </Stat>
          <Stat label="Reported production" note="Observed value for this crop year, when it exists">
            <Value v={s.reported?.production?.value} unit={s.reported?.production?.unit} />
          </Stat>
        </dl>
      </Panel>

      <div className="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_24rem]">
        <Panel
          title="Production history"
          description={s.history ? `Production in ${s.history.units.production}, by crop year` : undefined}
          actions={<ProvenanceLine p={s.historyProvenance} />}
        >
          {points.length === 0 ? (
            <EmptyState message="No production history was returned." />
          ) : (
            <>
              <figure className="h-64 p-4" aria-label={`Line chart of reported production from ${points[0].cropYear} to ${points[points.length - 1].cropYear}`}>
                <ResponsiveContainer>
                  <LineChart data={points} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                    <CartesianGrid stroke="var(--border)" vertical={false} />
                    <XAxis dataKey="cropYear" tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" />
                    <YAxis tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" width={64} tickFormatter={(v: number) => formatNumber(v)} />
                    <Tooltip formatter={(v) => formatNumber(Number(v))} />
                    <Line type="monotone" dataKey="production" name="Production" stroke="var(--primary)" strokeWidth={2} dot={{ r: 2 }} />
                  </LineChart>
                </ResponsiveContainer>
              </figure>
              <div className="max-h-72 overflow-auto border-t">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="pl-4">Crop year</TableHead>
                      <TableHead className="text-right">Area ({s.history!.units.area})</TableHead>
                      <TableHead className="text-right">Production ({s.history!.units.production})</TableHead>
                      <TableHead className="pr-4 text-right">Yield ({s.history!.units.yield})</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {[...points].reverse().map((p) => (
                      <TableRow key={p.cropYear}>
                        <TableCell className="pl-4">{p.cropYear}</TableCell>
                        <TableCell className="tabular text-right">{formatNumber(p.area)}</TableCell>
                        <TableCell className="tabular text-right">{formatNumber(p.production)}</TableCell>
                        <TableCell className="tabular pr-4 text-right">{formatNumber(p.yield)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </>
          )}
        </Panel>

        <div className="flex flex-col gap-4">
          <Panel title="Historical yield stats">
            {s.historicalYieldStats ? (
              <dl className="grid grid-cols-2 gap-3 p-4 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Years observed</dt>
                  <dd>{s.historicalYieldStats.yearsObserved}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Mean yield</dt>
                  <dd>
                    <Value v={s.historicalYieldStats.meanYield?.value} unit={s.historicalYieldStats.meanYield?.unit} />
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Yield CV</dt>
                  <dd>
                    <Value v={s.historicalYieldStats.coefficientOfVariation} />
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Downside-year share</dt>
                  <dd>
                    <Value v={s.historicalYieldStats.downsideYearShare} percent />
                  </dd>
                </div>
                {s.historicalYieldStats.downsideDefinition && <p className="col-span-2 text-xs text-muted-foreground">{s.historicalYieldStats.downsideDefinition}</p>}
              </dl>
            ) : (
              <EmptyState message="Not provided." />
            )}
          </Panel>
          <Panel title="Model evaluation">
            {s.modelEvaluation ? (
              <dl className="grid grid-cols-2 gap-3 p-4 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Test WAPE</dt>
                  <dd>{formatPercent(s.modelEvaluation.testWape)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Best baseline WAPE</dt>
                  <dd>
                    {formatPercent(s.modelEvaluation.bestBaselineTestWape)} <span className="text-xs text-muted-foreground">{s.modelEvaluation.bestBaseline}</span>
                  </dd>
                </div>
                <div className="col-span-2 text-xs text-muted-foreground">
                  Train {s.modelEvaluation.trainingPeriod} · validate {s.modelEvaluation.validationPeriod} · test {s.modelEvaluation.testPeriod}
                </div>
              </dl>
            ) : (
              <EmptyState message="Not provided." />
            )}
          </Panel>
          {s.limitations.length > 0 && (
            <Panel title="Limitations">
              <ul className="list-disc space-y-1 p-4 pl-8 text-xs text-muted-foreground">
                {s.limitations.map((l) => (
                  <li key={l}>{l}</li>
                ))}
              </ul>
            </Panel>
          )}
        </div>
      </div>
    </div>
  )
}

export function SupplyPage() {
  const scope = useReferenceScope()
  const [query, setQuery] = useState<SupplyQuery | null>(null)
  const supply = useSupply(query)

  return (
    <div className="flex flex-col gap-5">
      <title>Supply intelligence · Agri Intelligence</title>
      <PageHeader
        title="Supply intelligence"
        description="District production estimates from the ML service over DES crop-production history. Historical intelligence, not a current-season forecast."
      />
      <Panel title="Query" description={scope.data?.syncedAt ? `Reference scope synced ${new Date(scope.data.syncedAt).toLocaleString('en-IN')}` : undefined}>
        {scope.isPending ? (
          <LoadingState message="Loading the supported scope..." />
        ) : scope.error ? (
          <ErrorState message={describeError(scope.error, 'the reference scope')} onRetry={() => scope.refetch()} />
        ) : scope.data.supplySeries.length === 0 ? (
          <EmptyState message="Reference data not loaded yet.">
            <p className="max-w-md text-xs">
              The backend has not synced a supply scope from the ML service, so no district, crop and season can be queried. Supply intelligence is unavailable
              until then.
            </p>
          </EmptyState>
        ) : (
          <SupplyForm scope={scope.data} onSubmit={setQuery} />
        )}
      </Panel>

      {query &&
        (supply.isPending ? (
          <LoadingState message="Loading supply estimate..." />
        ) : supply.error ? (
          <Panel title="Supply estimate">
            <ErrorState message={describeError(supply.error, 'the supply estimate')} onRetry={() => supply.refetch()} />
          </Panel>
        ) : (
          supply.data && <SupplyResult s={supply.data} />
        ))}
    </div>
  )
}
