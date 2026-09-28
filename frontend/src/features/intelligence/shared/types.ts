import type { DataOrigin } from '@/types/status'

// Shared contract vocabulary for every intelligence response.
// BACKEND = mirrors a backend type. PROVISIONAL = the backend has no response body yet; expect change.
//
// There is no IntelligenceResponse<T> wrapper: backend responses are flat records that carry a `provenance`
// field, so the frontend follows that convention (WithProvenance) instead of inventing an envelope.

export type Option = { id: string; label: string }

/** BACKEND — common.api.DataClassification. */
export type DataClassification = 'OBSERVED' | 'FORECAST' | 'MODEL_PREDICTION' | 'REGIONAL_ESTIMATE' | 'SYNTHETIC'

const origins: Record<DataClassification, DataOrigin> = {
  OBSERVED: 'observed',
  FORECAST: 'forecast',
  MODEL_PREDICTION: 'model',
  REGIONAL_ESTIMATE: 'estimate',
  SYNTHETIC: 'synthetic',
}
/** Maps the backend enum onto the UI badge vocabulary. The only place this mapping lives. */
export const originOf = (c: DataClassification): DataOrigin => origins[c]

/** BACKEND — weather.dto.WeatherResponse.Provenance. `confidence` is null when the source states none; never fill it in. */
export type Provenance = { source: string; dataClassification: DataClassification; retrievedAt: string; confidence: number | null }

/**
 * BACKEND — intelligence.dto.IntelligenceProvenance, carried by every /api/intelligence result.
 * modelVersion is null when no model produced the result; confidence is null unless the source states one.
 */
export type IntelligenceProvenance = {
  source: string
  dataClassification: DataClassification
  generatedAt: string
  modelVersion: string | null
  confidence: number | null
  limitations: string[]
}

export type WithProvenance = { provenance: IntelligenceProvenance }
