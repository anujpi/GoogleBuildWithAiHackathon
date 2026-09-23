import { useSearchParams } from 'react-router'
import { MetricPanel } from '@/components/data-display/MetricPanel'
import { ConfidenceBadge, DataFreshness, DataOriginBadge, RiskIndicator, TrendLabel } from '@/components/data-display/status'
import { AsyncContent, ErrorState } from '@/components/feedback/states'
import { PageHeader } from '@/components/layout/PageHeader'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatNumber } from '@/lib/format'
import { AlertsPanel, CropPreviewPanel, SourcesPanel } from './InsightPanels'
import { RegionalWorkspace } from './RegionalWorkspace'
import { SupplyDemandChart } from './SupplyDemandChart'
import { useDashboardContext, useDashboardSummary } from './hooks'
import type { DashboardQuery, DashboardSummary, Option, Signal } from './types'

function ContextSelect({ id, label, value, options, onChange }: { id: string; label: string; value: string; options: Option[]; onChange: (v: string) => void }) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-muted-foreground">
        {label}
      </label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id={id} size="sm" className="min-w-40 bg-card">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((o) => (
            <SelectItem key={o.id} value={o.id}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

function SignalCell({ signal, signed }: { signal: Signal; signed?: boolean }) {
  const value = `${signed && signal.value > 0 ? '+' : ''}${formatNumber(signal.value)}`
  return (
    <MetricPanel
      className="bg-card"
      label={signal.label}
      value={value}
      unit={signal.unit}
      status={
        <>
          {signal.risk && <RiskIndicator level={signal.risk} />}
          <TrendLabel value={signal.trend} />
        </>
      }
      footnote={
        <span className="flex flex-col gap-2">
          <span>{signal.note}</span>
          <span className="flex flex-wrap items-center gap-2">
            <ConfidenceBadge value={signal.confidence} />
            <DataOriginBadge origin={signal.origin} />
          </span>
        </span>
      }
    />
  )
}

function SignalStrip({ signals }: { signals: DashboardSummary['signals'] }) {
  return (
    // Cells grow to fill each row, so an odd count never leaves an empty slot.
    <dl aria-label="Headline signals" className="flex flex-wrap gap-px border bg-border *:min-w-0 *:flex-1 *:basis-56">
      <SignalCell signal={signals.supply} />
      <SignalCell signal={signals.demand} />
      <SignalCell signal={signals.marketPressure} signed />
      <SignalCell signal={signals.weatherRisk} />
      <SignalCell signal={signals.diseaseRisk} />
    </dl>
  )
}

export function DashboardPage() {
  const [params, setParams] = useSearchParams()
  const context = useDashboardContext()

  const opts = context.data
  const regionId = params.get('region') ?? opts?.regions[0]?.id
  const farms = opts?.farms.filter((f) => f.regionId === regionId) ?? []
  const farmId = params.get('farm') ?? farms[0]?.id
  const seasonId = params.get('season') ?? opts?.seasons[0]?.id
  const query: DashboardQuery | null = regionId && farmId && seasonId ? { regionId, farmId, seasonId } : null
  const summary = useDashboardSummary(query)

  const set = (key: string) => (value: string) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set(key, value)
        if (key === 'region') next.delete('farm')
        return next
      },
      { replace: true },
    )

  return (
    <div className="flex flex-col gap-5">
      <title>Dashboard · Agri Intelligence</title>
      <PageHeader
        title="Regional intelligence overview"
        description="Supply, demand, market and risk signals for the selected region, farm and season."
        actions={
          opts &&
          query && (
            <>
              <ContextSelect id="ctx-region" label="Region" value={query.regionId} options={opts.regions} onChange={set('region')} />
              <ContextSelect id="ctx-farm" label="Farm" value={query.farmId} options={farms} onChange={set('farm')} />
              <ContextSelect id="ctx-season" label="Season" value={query.seasonId} options={opts.seasons} onChange={set('season')} />
            </>
          )
        }
      />

      {context.error ? (
        <ErrorState message="Region and farm options are temporarily unavailable." onRetry={() => context.refetch()} />
      ) : (
        <AsyncContent query={summary} loadingMessage="Loading regional intelligence..." errorMessage="Regional intelligence is temporarily unavailable.">
          {(data) => (
            <>
              <div className="-mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                <DataFreshness updatedAt={data.generatedAt} />
                <span>
                  Farm: {data.farm.name}, {data.farm.district} · {data.farm.areaHa} ha
                </span>
              </div>
              <SignalStrip signals={data.signals} />
              {/* Main workspace on the left, a fixed-width insight rail on the right. */}
              <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
                <RegionalWorkspace districts={data.districts} farm={data.farm} />
                <AlertsPanel alerts={data.alerts} />
                <SupplyDemandChart series={data.series} />
                <SourcesPanel sources={data.sources} />
              </div>
              <CropPreviewPanel crops={data.crops} />
            </>
          )}
        </AsyncContent>
      )}
    </div>
  )
}
