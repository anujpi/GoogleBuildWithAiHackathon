import type { GapState, RiskLevel, Trend } from '@/types/status'
import type { DataClassification, IntelligenceProvenance, Option, WithProvenance } from '../shared/types'

/** BACKEND — query params of every /api/intelligence forecast endpoint. Ids are lower-case slugs; horizonMonths 1-12. */
export type ForecastQuery = { regionId: string; cropId: string; horizonMonths: number }

// PROVISIONAL from here to SupplyForecast. GET /api/intelligence/supply-demand exists but has no response body yet
// (it answers 503 PREDICTION_UNAVAILABLE). This is the frontend's draft of that body.

/** One month. `period` is the ISO date of the month's first day. */
export type SupplyDemandPoint = { period: string; supply: number; demand: number }

export type SupplyDemandRisk = {
  kind: 'shortage' | 'oversupply'
  level: RiskLevel
  summary: string
  /** Associated signals, strongest first. Not causal claims. */
  factors: string[]
}

export type SupplyDemandForecast = {
  region: Option
  crop: Option
  period: Option
  /** Unit of every supply/demand value in this response. */
  unit: string
  historical: SupplyDemandPoint[]
  forecast: SupplyDemandPoint[]
  summary: {
    /** Latest historical month. */
    currentSupply: number
    currentDemand: number
    /** Monthly average over the forecast period. */
    forecastSupply: number
    forecastDemand: number
    /** forecastSupply − forecastDemand. Positive = surplus. */
    projectedGap: number
    /** projectedGap / forecastDemand, as a fraction. */
    gapPercentage: number
    gapState: GapState
    gapTrend: Trend
  }
  risks: SupplyDemandRisk[]
  /** Describes the forecast series and summary. */
  provenance: IntelligenceProvenance
  /** History usually comes from a different source (e.g. OBSERVED mandi arrivals) than the forecast. */
  historicalClassification: DataClassification
}

/**
 * BACKEND — intelligence.dto.SupplyForecastResponse (GET /api/intelligence/supply-forecast).
 * unit and forecastPeriod are passed through from the model unchanged. modelProvenance is null when the ML service sends none.
 */
export type SupplyForecast = WithProvenance & {
  regionId: string
  cropId: string
  horizonMonths: number
  predictedSupply: number
  unit: string
  forecastPeriod: string
  modelProvenance: { datasetVersion: string | null; featureVersion: string | null; trainedAt: string | null } | null
}

/**
 * PROVISIONAL — GET /api/intelligence/demand-forecast exists but has no response body yet. Draft mirrors SupplyForecast.
 * Demand is usually a proxy (e.g. mandi arrivals), never retail sales.
 */
export type DemandForecast = WithProvenance & { regionId: string; cropId: string; horizonMonths: number; predictedDemand: number; unit: string; forecastPeriod: string }
