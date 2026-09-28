// MOCK PROVIDER — development only, used only when VITE_INTELLIGENCE_SOURCE is not "api".
// Every response is classified SYNTHETIC so the UI labels it. Only supply-demand is mocked; the other
// intelligence endpoints have no mock on purpose, so no invented ML result can appear as a prediction.

import type { GapState, RiskLevel, Trend } from '@/types/status'
import type { Option } from '../shared/types'
import { forecastOptions } from './options'
import type { ForecastQuery, SupplyDemandForecast, SupplyDemandPoint, SupplyDemandRisk } from './types'

// Append ?mockError to the URL to see error states during development.
const shouldFail = () => new URLSearchParams(window.location.search).has('mockError')

function respond<T>(data: T, ms = 450): Promise<T> {
  return new Promise((resolve, reject) => setTimeout(() => (shouldFail() ? reject(new Error('Mock failure')) : resolve(data)), ms))
}

type Wave = { base: number; amp: number; peak: number; growth: number }

const curves: Record<string, { supply: Wave; demand: Wave; risks: SupplyDemandRisk[] }> = {
  onion: {
    supply: { base: 105, amp: 45, peak: 3, growth: 0.2 },
    demand: { base: 92, amp: 8, peak: 9, growth: 0.4 },
    risks: [
      { kind: 'oversupply', level: 'high', summary: 'Rabi arrivals are forecast to peak above regional offtake.', factors: ['Rabi area sown above 5-year median', 'Storage stocks carried over from last season', 'Export demand flat'] },
      { kind: 'shortage', level: 'low', summary: 'Short lean-season window before kharif arrivals.', factors: ['Late-monsoon rainfall variability', 'Storage losses in humid months'] },
    ],
  },
  tomato: {
    supply: { base: 60, amp: 22, peak: 0, growth: 0 },
    demand: { base: 62, amp: 5, peak: 6, growth: 0.25 },
    risks: [
      { kind: 'shortage', level: 'high', summary: 'Monsoon-season supply dips below urban demand.', factors: ['Heavy rainfall damage to standing crop', 'Leaf curl virus conditions', 'Rising Mumbai–Pune offtake'] },
      { kind: 'oversupply', level: 'moderate', summary: 'Winter harvest glut likely in Nashik and Pune belts.', factors: ['Synchronised transplanting dates', 'Low cold-storage suitability'] },
    ],
  },
  soybean: {
    supply: { base: 95, amp: 90, peak: 10, growth: 0.3 },
    demand: { base: 88, amp: 6, peak: 1, growth: 0.3 },
    risks: [
      { kind: 'oversupply', level: 'moderate', summary: 'Kharif harvest concentrates arrivals in Oct–Nov.', factors: ['Single-season harvest', 'Processor procurement pace'] },
      { kind: 'shortage', level: 'moderate', summary: 'Off-season supply relies on stored stock.', factors: ['Low carry-over stock', 'Crush demand from feed industry'] },
    ],
  },
}

const round = (n: number) => Math.round(n * 10) / 10
const mean = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length

/** Deterministic seasonal curve so the mock is stable between reloads. i = months from now. */
function point(i: number, c: (typeof curves)[string]): SupplyDemandPoint {
  const now = new Date()
  const t = new Date(now.getFullYear(), now.getMonth() + i, 1)
  const wave = (w: Wave) => w.base + w.amp * Math.cos((2 * Math.PI * (t.getMonth() - w.peak)) / 12) + w.growth * (i + 24)
  return { period: t.toISOString(), supply: round(wave(c.supply)), demand: round(wave(c.demand)) }
}

const find = (list: Option[], id: string) => list.find((o) => o.id === id) ?? { id, label: id }
const periodOf = (months: number): Option => forecastOptions.periods.find((p) => p.horizonMonths === months) ?? { id: months + 'm', label: 'Next ' + months + ' months' }

export function mockSupplyDemandForecast(q: ForecastQuery): Promise<SupplyDemandForecast> {
  const labels = { region: find(forecastOptions.regions, q.regionId), crop: find(forecastOptions.crops, q.cropId), period: periodOf(q.horizonMonths) }
  const c = curves[q.cropId]
  // Only Maharashtra has mock series; other regions demonstrate the empty state.
  const hasData = q.regionId === 'mh' && c

  const horizon = q.horizonMonths
  const historical = hasData ? Array.from({ length: 24 }, (_, k) => point(k - 23, c)) : []
  const forecast = hasData ? Array.from({ length: horizon }, (_, k) => point(k + 1, c)) : []

  const last = historical.at(-1)
  const forecastSupply = forecast.length ? round(mean(forecast.map((p) => p.supply))) : 0
  const forecastDemand = forecast.length ? round(mean(forecast.map((p) => p.demand))) : 0
  const projectedGap = round(forecastSupply - forecastDemand)
  const gapPercentage = forecastDemand ? projectedGap / forecastDemand : 0
  // ±5% of demand counts as balanced.
  const gapState: GapState = gapPercentage > 0.05 ? 'surplus' : gapPercentage < -0.05 ? 'shortage' : 'balanced'
  const gapDelta = forecast.length ? forecast.at(-1)!.supply - forecast.at(-1)!.demand - (forecast[0].supply - forecast[0].demand) : 0
  const gapTrend: Trend = gapDelta > 2 ? 'up' : gapDelta < -2 ? 'down' : 'flat'

  // Keep the mock risks consistent with the mock gap: the matching risk scales with gap size, the opposite one is low.
  const matching = gapState === 'shortage' ? 'shortage' : gapState === 'surplus' ? 'oversupply' : null
  const levelFor = (kind: SupplyDemandRisk['kind'], base: RiskLevel): RiskLevel =>
    !matching ? base : kind !== matching ? 'low' : Math.abs(gapPercentage) > 0.2 ? 'high' : 'moderate'
  const risks = hasData ? c.risks.map((r) => ({ ...r, level: levelFor(r.kind, r.level) })) : []

  return respond({
    ...labels,
    unit: 'thousand tonnes / month',
    historical,
    forecast,
    summary: {
      currentSupply: last?.supply ?? 0,
      currentDemand: last?.demand ?? 0,
      forecastSupply,
      forecastDemand,
      projectedGap,
      gapPercentage,
      gapState,
      gapTrend,
    },
    risks,
    provenance: {
      source: 'Frontend mock provider',
      dataClassification: 'SYNTHETIC',
      generatedAt: new Date().toISOString(),
      // No model exists behind the mock, so no model version or confidence is claimed.
      modelVersion: null,
      confidence: null,
      limitations: [
        'Generated seasonal curves for interface development, not observed arrivals and not a model forecast.',
        'Demand stands in for a market-offtake proxy, not retail sales.',
      ],
    },
    historicalClassification: 'SYNTHETIC',
  })
}
