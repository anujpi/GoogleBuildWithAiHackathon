import { z } from 'zod'
import { api } from '@/lib/api/client'
import { classificationSchema, intelligenceProvenanceSchema, intelligenceSource, optionSchema, qs, validated } from '../shared/http'
import { mockSupplyDemandForecast } from './mock'
import type { DemandForecast, ForecastQuery, SupplyDemandForecast, SupplyForecast } from './types'

// BACKEND paths (intelligence.controller.IntelligenceController).
const paths = { supplyDemand: '/intelligence/supply-demand', supply: '/intelligence/supply-forecast', demand: '/intelligence/demand-forecast' }

const point = z.object({ period: z.string(), supply: z.number(), demand: z.number() })
const supplyDemandSchema = z.object({
  region: optionSchema,
  crop: optionSchema,
  period: optionSchema,
  unit: z.string(),
  historical: z.array(point),
  forecast: z.array(point),
  summary: z.object({
    currentSupply: z.number(),
    currentDemand: z.number(),
    forecastSupply: z.number(),
    forecastDemand: z.number(),
    projectedGap: z.number(),
    gapPercentage: z.number(),
    gapState: z.enum(['surplus', 'balanced', 'shortage']),
    gapTrend: z.enum(['up', 'flat', 'down']),
  }),
  risks: z.array(z.object({ kind: z.enum(['shortage', 'oversupply']), level: z.enum(['low', 'moderate', 'high', 'critical']), summary: z.string(), factors: z.array(z.string()) })),
  provenance: intelligenceProvenanceSchema,
  historicalClassification: classificationSchema,
})

/** null = the backend answered with no content; the UI shows an empty state. */
export function getSupplyDemandForecast(q: ForecastQuery, signal?: AbortSignal): Promise<SupplyDemandForecast | null> {
  if (intelligenceSource === 'mock') return mockSupplyDemandForecast(q)
  return api<unknown>(paths.supplyDemand + qs(q), { signal }).then(validated(supplyDemandSchema, 'supply and demand'))
}

const supplyForecastSchema = z.object({
  regionId: z.string(),
  cropId: z.string(),
  horizonMonths: z.number(),
  predictedSupply: z.number(),
  unit: z.string(),
  forecastPeriod: z.string(),
  provenance: intelligenceProvenanceSchema,
  modelProvenance: z.object({ datasetVersion: z.string().nullable(), featureVersion: z.string().nullable(), trainedAt: z.string().nullable() }).nullable(),
})

// No mock for the endpoints below: they always call the backend, which answers 503 PREDICTION_UNAVAILABLE while no
// prediction source is connected.
export function getSupplyForecast(q: ForecastQuery, signal?: AbortSignal): Promise<SupplyForecast | null> {
  return api<unknown>(paths.supply + qs(q), { signal }).then(validated(supplyForecastSchema, 'supply forecast'))
}

/** Not validated: the backend has no response body for this endpoint yet. */
export function getDemandForecast(q: ForecastQuery, signal?: AbortSignal): Promise<DemandForecast> {
  return api<DemandForecast>(paths.demand + qs(q), { signal })
}
