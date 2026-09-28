import type { Availability, Confidence, GapState, RiskLevel, Trend } from '@/types/status'
import type { DataClassification, Option, Provenance } from '@/features/intelligence/shared/types'

// Shapes are written as the backend contract we expect, so the mock can be swapped without touching UI.

/** Per-signal source. Classification uses the backend enum so badges derive from data, never from the component. */
export type SignalSource = { dataClassification: DataClassification; source: string; updatedAt: string }

export type DashboardContext = {
  regions: Option[]
  farms: (Option & { regionId: string; district: string })[]
  seasons: Option[]
}

export type DashboardQuery = { regionId: string; farmId: string; seasonId: string }

export type Signal = SignalSource & {
  label: string
  value: number
  unit: string
  trend: Trend
  confidence: Confidence
  risk?: RiskLevel
  note: string
}

export type DistrictSignal = {
  id: string
  name: string
  lon: number
  lat: number
  gap: GapState
  /** Positive = surplus, negative = shortage, thousand tonnes over the season. */
  gapKt: number
  confidence: Confidence
}

export type CropPreview = {
  cropId: string
  crop: string
  suitability: 'high' | 'medium' | 'low'
  expectedYield: { value: number; unit: string }
  waterNeed: 'high' | 'medium' | 'low'
  weatherRisk: RiskLevel
  supplyPressure: RiskLevel
  demandTrend: Trend
  projectedGap: GapState
  confidence: Confidence
}

export type Alert = {
  id: string
  severity: RiskLevel
  category: 'supply' | 'weather' | 'disease' | 'market' | 'data'
  title: string
  detail: string
  district: string
  raisedAt: string
}

export type SourceStatus = { id: string; name: string; availability: Availability; updatedAt: string; dataClassification: DataClassification }

export type DashboardSummary = {
  generatedAt: string
  /** Classifies the district map and crop preview, which have no per-row source. */
  provenance: Provenance
  farm: { id: string; name: string; district: string; lon: number; lat: number; areaHa: number }
  signals: { supply: Signal; demand: Signal; marketPressure: Signal; weatherRisk: Signal; diseaseRisk: Signal }
  districts: DistrictSignal[]
  crops: CropPreview[]
  alerts: Alert[]
  sources: SourceStatus[]
}
