import { useSearchParams } from 'react-router'
import { Bar, BarChart, CartesianGrid, Cell, ComposedChart, Line, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { ChartContainer } from '@/components/charts/ChartContainer'
import { MetricPanel } from '@/components/data-display/MetricPanel'
import { ConfidenceBadge, DataFreshness, DataOriginBadge, GapBadge, RiskIndicator, TrendLabel } from '@/components/data-display/status'
import { Callout } from '@/components/feedback/Callout'
import { AsyncContent } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'
import { PageHeader } from '@/components/layout/PageHeader'
import { ContextSelect } from '@/components/forms/ContextSelect'
import { formatNumber, formatPercent } from '@/lib/format'
import { originOf } from '@/features/intelligence/shared/types'
import { useSupplyDemandForecast } from '@/features/intelligence/supply-demand/hooks'
import { forecastOptions } from '@/features/intelligence/supply-demand/options'
import { month, toChartRows } from '@/features/intelligence/supply-demand/series'
import { SupplyDemandLegend } from '@/features/intelligence/supply-demand/SupplyDemandLegend'
import type { ForecastQuery, SupplyDemandForecast } from '@/features/intelligence/supply-demand/types'
import { describeError } from '@/lib/api/client'

const tick = { fontSize: 11, fill: 'var(--muted-foreground)' }
const tooltipStyle = { background: 'var(--popover)', border: '1px solid var(--border)', borderRadius: 4, fontSize: 12 }
const signed = (n: number) => `${n > 0 ? '+' : ''}${formatNumber(n)}`
const range = (points: { period: string }[]) => (points.length ? `${month.format(new Date(points[0].period))} – ${month.format(new Date(points.at(-1)!.period))}` : undefined)

const historicalOrigin = (d: SupplyDemandForecast) => originOf(d.historicalClassification)
const forecastOrigin = (d: SupplyDemandForecast) => originOf(d.provenance.dataClassification)

function SummaryStrip({ d }: { d: SupplyDemandForecast }) {
  const s = d.summary
  const confidence = d.provenance.confidence
  const unit = 'kt / month'
  return (
    <dl aria-label="Supply and demand summary" className="flex flex-wrap gap-px border bg-border *:min-w-0 *:flex-1 *:basis-44">
      <MetricPanel className="bg-card" label="Current supply" value={formatNumber(s.currentSupply)} unit={unit} footnote={<DataOriginBadge origin={historicalOrigin(d)} />} />
      <MetricPanel className="bg-card" label="Forecast supply" value={formatNumber(s.forecastSupply)} unit={unit} footnote={<span className="flex flex-wrap gap-2"><span>Avg. over {d.period.label.toLowerCase()}</span><DataOriginBadge origin={forecastOrigin(d)} /></span>} />
      <MetricPanel className="bg-card" label="Current demand" value={formatNumber(s.currentDemand)} unit={unit} footnote={<DataOriginBadge origin={historicalOrigin(d)} />} />
      <MetricPanel className="bg-card" label="Forecast demand" value={formatNumber(s.forecastDemand)} unit={unit} footnote={<span className="flex flex-wrap gap-2"><span>Avg. over {d.period.label.toLowerCase()}</span><DataOriginBadge origin={forecastOrigin(d)} /></span>} />
      <MetricPanel
        className="bg-card"
        label="Projected gap"
        value={signed(s.projectedGap)}
        unit={unit}
        status={<GapBadge state={s.gapState} />}
        footnote={confidence !== null && <ConfidenceBadge value={confidence} />}
      />
    </dl>
  )
}

function TrendChart({ d }: { d: SupplyDemandForecast }) {
  const rows = toChartRows(d)
  const firstForecast = d.forecast[0] && month.format(new Date(d.forecast[0].period))
  const today = d.historical.at(-1) && month.format(new Date(d.historical.at(-1)!.period))
  return (
    <ChartContainer
      title="Supply vs demand over time"
      unit={d.unit}
      timeContext={range([...d.historical, ...d.forecast])}
      summary={`${d.crop.label}, ${d.region.label}: ${d.historical.length} months of history (solid) and ${d.forecast.length} months of forecast (dashed, shaded). Forecast average: supply ${formatNumber(d.summary.forecastSupply)}, demand ${formatNumber(d.summary.forecastDemand)} thousand tonnes per month.`}
      actions={<DataOriginBadge origin={forecastOrigin(d)} />}
      legend={<SupplyDemandLegend />}
      isLoading={false}
      error={null}
      isEmpty={rows.length === 0}
      loadingMessage="Loading supply and demand series..."
      errorMessage="Supply and demand series are temporarily unavailable."
      emptyMessage="No supply forecast is available for this region yet."
      className="min-h-96"
    >
      <ResponsiveContainer width="100%" height="100%" minHeight={280}>
        <ComposedChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -12 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          {firstForecast && <ReferenceArea x1={firstForecast} x2={rows.at(-1)!.label} fill="var(--muted)" fillOpacity={0.8} label={{ value: 'Forecast', position: 'insideTopRight', fontSize: 11, fill: 'var(--muted-foreground)' }} />}
          {today && <ReferenceLine x={today} stroke="var(--foreground)" strokeOpacity={0.35} strokeDasharray="2 2" />}
          <XAxis dataKey="label" tick={tick} tickLine={false} axisLine={{ stroke: 'var(--border)' }} interval="preserveStartEnd" minTickGap={24} />
          <YAxis tick={tick} tickLine={false} axisLine={false} width={48} />
          <Tooltip contentStyle={tooltipStyle} formatter={(value, name) => [`${formatNumber(Number(value))} kt`, String(name)]} />
          <Line dataKey="supply" name="Supply" stroke="var(--supply)" strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line dataKey="demand" name="Demand" stroke="var(--demand)" strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line dataKey="supplyForecast" name="Supply (forecast)" stroke="var(--supply)" strokeWidth={2} strokeDasharray="5 4" dot={false} isAnimationActive={false} />
          <Line dataKey="demandForecast" name="Demand (forecast)" stroke="var(--demand)" strokeWidth={2} strokeDasharray="5 4" dot={false} isAnimationActive={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartContainer>
  )
}

function GapPanel({ d }: { d: SupplyDemandForecast }) {
  const s = d.summary
  // Last 6 historical months + the forecast, so the bars show where the gap is heading.
  const rows = [...d.historical.slice(-6).map((p) => ({ ...p, forecast: false })), ...d.forecast.map((p) => ({ ...p, forecast: true }))].map((p) => ({
    label: month.format(new Date(p.period)),
    gap: Math.round((p.supply - p.demand) * 10) / 10,
    forecast: p.forecast,
  }))
  return (
    <ChartContainer
      title="Supply-demand gap"
      unit="supply − demand, kt / month"
      timeContext={range([...d.historical.slice(-6), ...d.forecast])}
      summary={`Projected ${s.gapState} of ${formatNumber(Math.abs(s.projectedGap))} thousand tonnes per month (${formatPercent(Math.abs(s.gapPercentage))} of forecast demand). Above zero is surplus, below zero is deficit. Faded bars are forecast.`}
      legend={
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <GapBadge state={s.gapState} />
            <span className="tabular text-lg font-semibold">{signed(s.projectedGap)} kt</span>
            <span className="tabular text-sm text-muted-foreground">({signed(Math.round(s.gapPercentage * 1000) / 10)}% of demand)</span>
          </div>
          <span className="flex items-center gap-2 text-xs text-muted-foreground">
            Gap over forecast period: <TrendLabel value={s.gapTrend} />
          </span>
        </div>
      }
      isLoading={false}
      error={null}
      isEmpty={rows.length === 0}
      loadingMessage="Loading gap..."
      errorMessage="Gap data is temporarily unavailable."
      emptyMessage="No gap can be computed without a forecast."
    >
      <ResponsiveContainer width="100%" height="100%" minHeight={200}>
        <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis dataKey="label" tick={tick} tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={16} />
          <YAxis tick={tick} tickLine={false} axisLine={false} width={44} />
          <ReferenceLine y={0} stroke="var(--foreground)" strokeOpacity={0.4} />
          <Tooltip contentStyle={tooltipStyle} formatter={(value) => [`${signed(Number(value))} kt`, Number(value) >= 0 ? 'Surplus' : 'Deficit']} />
          <Bar dataKey="gap" isAnimationActive={false}>
            {rows.map((r) => (
              <Cell key={r.label} fill={r.gap >= 0 ? 'var(--supply)' : 'var(--demand)'} fillOpacity={r.forecast ? 0.45 : 0.9} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartContainer>
  )
}

/** Fixed rules over the summary numbers. Deliberately not AI-generated. */
function interpret(d: SupplyDemandForecast): string {
  const s = d.summary
  const pct = formatPercent(Math.abs(s.gapPercentage))
  const period = d.period.label.toLowerCase()
  const main =
    s.gapState === 'shortage'
      ? `Projected demand is expected to exceed supply by about ${pct} over the ${period}.`
      : s.gapState === 'surplus'
        ? `Projected supply is expected to exceed demand by about ${pct} over the ${period}.`
        : `Projected supply and demand stay within 5% of each other over the ${period}.`
  const trend = s.gapTrend === 'up' ? 'The gap moves towards surplus as the period progresses.' : s.gapTrend === 'down' ? 'The gap moves towards deficit as the period progresses.' : 'The gap is roughly stable across the period.'
  const c = d.provenance.confidence
  const conf = c === null ? ' No model confidence is available for this forecast.' : c < 0.5 ? ' Forecast confidence is low, so treat this reading as indicative only.' : ''
  return `${main} ${trend}${conf}`
}

function InsightPanel({ d }: { d: SupplyDemandForecast }) {
  return (
    <Panel title="Forecast reading" description="Rule-based interpretation of the figures above — not AI-generated" bodyClassName="flex flex-col gap-3 p-4">
      <p className="text-sm leading-relaxed">{interpret(d)}</p>
      <div className="flex flex-wrap items-center gap-2">
        {d.provenance.confidence !== null && <ConfidenceBadge value={d.provenance.confidence} />}
        <DataOriginBadge origin={forecastOrigin(d)} />
      </div>
    </Panel>
  )
}

function RiskPanel({ d }: { d: SupplyDemandForecast }) {
  const label = { shortage: 'Shortage risk', oversupply: 'Oversupply risk' } as const
  return (
    <Panel title="Risk and attention" description="Associated signals, not proven causes">
      <ul className="divide-y">
        {d.risks.map((r) => (
          <li key={r.kind} className="flex flex-col gap-2 px-4 py-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-sm font-medium">{label[r.kind]}</span>
              <RiskIndicator level={r.level} />
            </div>
            <p className="text-xs text-muted-foreground">{r.summary}</p>
            <ul className="flex flex-wrap gap-1.5" aria-label="Contributing factors">
              {r.factors.map((f) => (
                <li key={f} className="rounded-sm border bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
                  {f}
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

function ProvenancePanel({ d }: { d: SupplyDemandForecast }) {
  return (
    <Panel title="Data provenance" bodyClassName="flex flex-col gap-3 p-4 text-xs">
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5">
        <dt className="text-muted-foreground">Source</dt>
        <dd>{d.provenance.source}</dd>
        <dt className="text-muted-foreground">History</dt>
        <dd><DataOriginBadge origin={historicalOrigin(d)} /></dd>
        <dt className="text-muted-foreground">Forecast</dt>
        <dd><DataOriginBadge origin={forecastOrigin(d)} /></dd>
        <dt className="text-muted-foreground">Model</dt>
        <dd>{d.provenance.modelVersion ?? 'None'}</dd>
        <dt className="text-muted-foreground">Unit</dt>
        <dd>{d.unit}</dd>
      </dl>
      <DataFreshness updatedAt={d.provenance.generatedAt} />
      {d.provenance.limitations.length > 0 && (
        <ul className="flex list-disc flex-col gap-1 pl-4 text-muted-foreground" aria-label="Limitations">
          {d.provenance.limitations.map((l) => (
            <li key={l}>{l}</li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

export function SupplyDemandPage() {
  const [params, setParams] = useSearchParams()
  const opts = forecastOptions
  const regionId = params.get('region') ?? opts.regions[0].id
  const cropId = params.get('crop') ?? opts.crops[0].id
  const period = opts.periods.find((p) => p.id === params.get('period')) ?? opts.periods[0]
  const query: ForecastQuery = { regionId, cropId, horizonMonths: period.horizonMonths }
  const forecast = useSupplyDemandForecast(query)

  const set = (key: string) => (value: string) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set(key, value)
        return next
      },
      { replace: true },
    )

  return (
    <div className="flex flex-col gap-5">
      <title>Supply & Demand · Agri Intelligence</title>
      <PageHeader
        title="Supply & demand intelligence"
        description="Historical and forecast supply against demand for the selected region, crop and forecast period."
        actions={
          <>
            <ContextSelect id="sd-region" label="Region" value={query.regionId} options={opts.regions} onChange={set('region')} />
            <ContextSelect id="sd-crop" label="Crop" value={query.cropId} options={opts.crops} onChange={set('crop')} />
            <ContextSelect id="sd-period" label="Forecast period" value={period.id} options={opts.periods} onChange={set('period')} />
          </>
        }
      />

      <AsyncContent
        query={forecast}
        loadingMessage="Loading supply and demand intelligence..."
        errorMessage={describeError(forecast.error, 'supply and demand data')}
        isEmpty={(d) => d === null || (d.historical.length === 0 && d.forecast.length === 0)}
        emptyMessage="No supply forecast is available for this region yet."
      >
        {(d) =>
          d && (
            <>
              {(forecastOrigin(d) === 'synthetic' || historicalOrigin(d) === 'synthetic') && (
                <Callout tone="synthetic" title="Prototype data">
                  Values labelled Synthetic are generated for development, not observed data or model predictions. Do not use them for decisions.
                </Callout>
              )}
              <SummaryStrip d={d} />
              <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_24rem]">
                <TrendChart d={d} />
                <GapPanel d={d} />
              </div>
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_24rem]">
                <InsightPanel d={d} />
                <RiskPanel d={d} />
                <ProvenancePanel d={d} />
              </div>
            </>
          )
        }
      </AsyncContent>
    </div>
  )
}
