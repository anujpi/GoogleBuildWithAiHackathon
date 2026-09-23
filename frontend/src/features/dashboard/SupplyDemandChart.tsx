import { useState } from 'react'
import { CartesianGrid, ComposedChart, Line, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { ChartContainer } from '@/components/charts/ChartContainer'
import { DataOriginBadge } from '@/components/data-display/status'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatNumber } from '@/lib/format'
import type { CropSeries } from './types'

const month = new Intl.DateTimeFormat('en-IN', { month: 'short', year: 'numeric' })

/** Splits each series into historical + forecast keys so forecast can be drawn dashed. The boundary point sits in both. */
function toChartRows(series: CropSeries) {
  const lastHist = series.points.findLastIndex((p) => p.kind === 'historical')
  return series.points.map((p, i) => ({
    label: month.format(new Date(p.period)),
    kind: p.kind,
    supply: i <= lastHist ? p.supply : undefined,
    demand: i <= lastHist ? p.demand : undefined,
    supplyForecast: i >= lastHist ? p.supply : undefined,
    demandForecast: i >= lastHist ? p.demand : undefined,
  }))
}

function Legend() {
  const item = (label: string, color: string, dashed?: boolean) => (
    <li className="flex items-center gap-1.5">
      <svg width="18" height="6" aria-hidden>
        <line x1="0" y1="3" x2="18" y2="3" stroke={color} strokeWidth="2" strokeDasharray={dashed ? '4 3' : undefined} />
      </svg>
      {label}
    </li>
  )
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-label="Legend">
      {item('Supply', 'var(--supply)')}
      {item('Demand', 'var(--demand)')}
      {item('Forecast (dashed)', 'var(--muted-foreground)', true)}
    </ul>
  )
}

export function SupplyDemandChart({ series }: { series: CropSeries[] }) {
  const [cropId, setCropId] = useState(series[0]?.cropId)
  const active = series.find((s) => s.cropId === cropId) ?? series[0]
  const rows = active ? toChartRows(active) : []
  const firstForecast = rows.find((r) => r.kind === 'forecast')?.label
  const today = rows.findLast((r) => r.kind === 'historical')
  const range = rows.length ? `${rows[0].label} – ${rows[rows.length - 1].label}` : undefined

  return (
    <ChartContainer
      title="Supply vs demand"
      unit={active?.unit ?? 'thousand tonnes / month'}
      timeContext={range}
      summary={
        active && today
          ? `${active.crop}, Maharashtra: ${rows.filter((r) => r.kind === 'historical').length} months of history and ${rows.filter((r) => r.kind === 'forecast').length} months of forecast. Latest month (${today.label}): supply ${formatNumber(today.supply ?? 0)}, demand ${formatNumber(today.demand ?? 0)} thousand tonnes. Synthetic prototype data.`
          : ''
      }
      actions={
        <>
          <Tabs value={cropId} onValueChange={setCropId}>
            <TabsList aria-label="Crop">
              {series.map((s) => (
                <TabsTrigger key={s.cropId} value={s.cropId} className="text-xs">
                  {s.crop}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
          {active && <DataOriginBadge origin={active.origin} />}
        </>
      }
      legend={<Legend />}
      isLoading={false}
      error={null}
      isEmpty={rows.length === 0}
      loadingMessage="Loading supply and demand series..."
      errorMessage="Supply and demand series are temporarily unavailable."
      emptyMessage="No supply forecast is available for this region yet."
    >
      <ResponsiveContainer width="100%" height="100%" minHeight={240}>
        <ComposedChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -12 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          {firstForecast && (
            <ReferenceArea x1={firstForecast} x2={rows[rows.length - 1].label} fill="var(--muted)" fillOpacity={0.8} label={{ value: 'Forecast', position: 'insideTopRight', fontSize: 11, fill: 'var(--muted-foreground)' }} />
          )}
          {today && <ReferenceLine x={today.label} stroke="var(--foreground)" strokeOpacity={0.35} strokeDasharray="2 2" />}
          <XAxis dataKey="label" tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={{ stroke: 'var(--border)' }} interval="preserveStartEnd" minTickGap={24} />
          <YAxis tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={false} width={48} />
          <Tooltip
            contentStyle={{ background: 'var(--popover)', border: '1px solid var(--border)', borderRadius: 4, fontSize: 12 }}
            formatter={(value, name) => [`${formatNumber(Number(value))} kt`, String(name)]}
          />
          <Line dataKey="supply" name="Supply" stroke="var(--supply)" strokeWidth={2} dot={false} connectNulls={false} isAnimationActive={false} />
          <Line dataKey="demand" name="Demand" stroke="var(--demand)" strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line dataKey="supplyForecast" name="Supply (forecast)" stroke="var(--supply)" strokeWidth={2} strokeDasharray="5 4" dot={false} isAnimationActive={false} />
          <Line dataKey="demandForecast" name="Demand (forecast)" stroke="var(--demand)" strokeWidth={2} strokeDasharray="5 4" dot={false} isAnimationActive={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartContainer>
  )
}
