import { useState } from 'react'
import { CartesianGrid, ComposedChart, Line, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { ChartContainer } from '@/components/charts/ChartContainer'
import { DataOriginBadge } from '@/components/data-display/status'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { originOf } from '@/features/intelligence/shared/types'
import { useSupplyDemandForecast } from '@/features/intelligence/supply-demand/hooks'
import { forecastOptions } from '@/features/intelligence/supply-demand/options'
import { toChartRows } from '@/features/intelligence/supply-demand/series'
import { SupplyDemandLegend } from '@/features/intelligence/supply-demand/SupplyDemandLegend'
import { describeError } from '@/lib/api/client'
import { formatNumber } from '@/lib/format'

/** Dashboard widget. Reads the same supply-demand endpoint (and cache) as the Supply & Demand page. */
export function SupplyDemandChart({ regionId }: { regionId: string }) {
  const crops = forecastOptions.crops
  const [cropId, setCropId] = useState(crops[0].id)
  const query = useSupplyDemandForecast({ regionId, cropId, horizonMonths: 6 })
  const d = query.data
  const rows = d ? toChartRows(d) : []
  const firstForecast = rows.find((r) => r.kind === 'forecast')?.label
  const today = rows.findLast((r) => r.kind === 'historical')
  const range = rows.length ? `${rows[0].label} – ${rows[rows.length - 1].label}` : undefined
  const origin = d && originOf(d.provenance.dataClassification)

  return (
    <ChartContainer
      title="Supply vs demand"
      unit={d?.unit ?? 'thousand tonnes / month'}
      timeContext={range}
      summary={
        d && today
          ? `${d.crop.label}, ${d.region.label}: ${d.historical.length} months of history and ${d.forecast.length} months of forecast. Latest month (${today.label}): supply ${formatNumber(today.supply ?? 0)}, demand ${formatNumber(today.demand ?? 0)} thousand tonnes.${origin === 'synthetic' ? ' Synthetic prototype data.' : ''}`
          : ''
      }
      actions={
        <>
          <Tabs value={cropId} onValueChange={setCropId}>
            <TabsList aria-label="Crop">
              {crops.map((c) => (
                <TabsTrigger key={c.id} value={c.id} className="text-xs">
                  {c.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
          {origin && <DataOriginBadge origin={origin} />}
        </>
      }
      legend={<SupplyDemandLegend />}
      isLoading={query.isPending}
      error={query.error}
      onRetry={() => query.refetch()}
      isEmpty={rows.length === 0}
      loadingMessage="Loading supply and demand series..."
      errorMessage={describeError(query.error, 'supply and demand series')}
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
