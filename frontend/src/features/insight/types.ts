// Mirrors the backend DTOs in backend/docs/intelligence-api.md. Every number the backend may omit is `| null`:
// render it as unavailable, never as 0.

import type { DataClassification } from '@/types/status'

export type { DataClassification }

export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL' | 'UNAVAILABLE'

/** common.api.Provenance (§12). Fields that don't apply are null. There is no confidence field. */
export type Provenance = {
  source: string
  dataClassification: DataClassification
  retrievedAt: string | null
  generatedAt: string | null
  datasetVersion: string | null
  modelName: string | null
  modelVersion: string | null
  featureVersion: string | null
  dataThrough: string | null
  notes: string[] | null
}

export type ReferenceScope = {
  states: { stateId: string; label: string }[]
  districts: { districtId: string; stateId: string; label: string }[]
  crops: { cropId: string; label: string }[]
  seasons: string[]
  supplySeries: { districtId: string; cropId: string; season: string; firstYear: number; lastYear: number; yearsObserved: number; estimableYears: number[] }[]
  datasets: { source: string; datasetVersion: string; dataThrough: string }[]
  syncedAt: string | null
}

export type Weather = {
  farmId: string | null
  latitude: number
  longitude: number
  current: {
    time: string | null
    temperatureC: number | null
    relativeHumidityPct: number | null
    precipitationMm: number | null
    windSpeedKmh: number | null
    provenance: Provenance
  }
  daily: {
    date: string
    minTemperatureC: number | null
    maxTemperatureC: number | null
    precipitationMm: number | null
    precipitationProbabilityPct: number | null
    relativeHumidityPct: number | null
  }[]
  dailyProvenance: Provenance
}

export type Tier = 'SUITABLE' | 'SUITABLE_WITH_CAUTION' | 'UNSUITABLE' | 'NOT_SUPPORTED'

export type EvidenceItem = { status: string; value: number | null; unit: string | null; basis: string | null; provenance: Provenance | null }

export type Quantity = { value: number; unit: string }

export type CropCandidate = {
  rank: number | null
  cropId: string
  cropLabel: string
  tier: Tier
  evidence: {
    seriesSupported: boolean
    soilCompatibility: EvidenceItem | null
    weatherSuitability: EvidenceItem | null
    productionEvidence: {
      status: string
      cropYear: number | null
      coefficientOfVariation: number | null
      downsideYearShare: number | null
      meanYield: Quantity | null
      servedMethod: string | null
      dataThrough: string | null
      provenance: Provenance | null
    } | null
    marketContext: EvidenceItem | null
    productionRisk: RiskLevel | null
  } | null
  reasons: { code: string; text: string }[] | null
  unavailable: string[] | null
  limitations: string[] | null
}

export type CropEvidence = { farmId: string; districtId: string; season: string; rankingRule: string; candidates: CropCandidate[]; generatedAt: string }

export type RiskFactor = {
  code: string
  level: RiskLevel
  value: number | null
  unit: string | null
  threshold: string | null
  source: string | null
  dataClassification: DataClassification | null
  observedOrForecastFor: string | null
  reason: string | null
}

export type Risk = { level: RiskLevel; factors: RiskFactor[]; assessedFactors: number; unavailableFactors: string[]; limitations: string[] }

export type RiskAssessment = {
  farmId: string
  cropId: string
  season: string
  cropYear: number | null
  ruleSet: string
  productionRisk: Risk
  marketRisk: Risk
  generatedAt: string
}

type Interval = { lower: number; upper: number; nominalCoverage: number; empiricalCoverage: number | null; method: string }
type QuantityWithInterval = Quantity & { interval: Interval | null }

export type SupplyEstimate = {
  target: { districtId: string; districtLabel: string | null; cropId: string; cropLabel: string | null; season: string; cropYear: number }
  estimate: {
    production: QuantityWithInterval
    yield: QuantityWithInterval
    area: { value: number; unit: string; areaSource: string }
    servedMethod: string
    historyYearsUsed: number[]
  }
  baseline: { method: string; production: Quantity } | null
  reported: { area: Quantity | null; production: Quantity | null; yield: Quantity | null } | null
  history: { units: { area: string; production: string; yield: string }; points: { cropYear: number; area: number; production: number; yield: number }[] } | null
  historicalYieldStats: {
    yearsObserved: number
    meanYield: Quantity | null
    coefficientOfVariation: number | null
    downsideYearShare: number | null
    yearsAssessedForDownside: number
    downsideDefinition: string | null
  } | null
  modelEvaluation: {
    trainingPeriod: string
    validationPeriod: string
    testPeriod: string
    servedMethod: string
    testWape: number
    bestBaseline: string
    bestBaselineTestWape: number
    testIntervalCoverage: number | null
  } | null
  provenance: Provenance
  historyProvenance: Provenance | null
  limitations: string[]
}

export type SupplyQuery = { districtId: string; cropId: string; season: string; cropYear: number }

/** Farm season → canonical season (intelligence-api.md). OTHER has no mapping. */
export const canonicalSeason = (farmSeason: string): string | null =>
  ({ KHARIF: 'KHARIF', RABI: 'RABI', ZAID: 'SUMMER' })[farmSeason] ?? null
