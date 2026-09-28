import type { SupplyDemandForecast } from './types'

export const month = new Intl.DateTimeFormat('en-IN', { month: 'short', year: 'numeric' })

/** Historical and forecast go in separate keys so forecast draws dashed. The last historical month sits in both to join the lines. */
export function toChartRows(d: SupplyDemandForecast) {
  const last = d.historical.at(-1)
  return [
    ...d.historical.map((p) => ({ label: month.format(new Date(p.period)), kind: 'historical' as const, supply: p.supply, demand: p.demand, supplyForecast: p === last ? p.supply : undefined, demandForecast: p === last ? p.demand : undefined })),
    ...d.forecast.map((p) => ({ label: month.format(new Date(p.period)), kind: 'forecast' as const, supply: undefined, demand: undefined, supplyForecast: p.supply, demandForecast: p.demand })),
  ]
}
