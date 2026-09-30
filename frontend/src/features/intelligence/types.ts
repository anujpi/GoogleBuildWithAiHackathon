// Mirrors backend intelligence/advisory/disease DTOs (backend/docs/intelligence-api.md).
// ML sections are passed through by the backend unchanged, so their field names follow the ML contracts.

export type SectionStatus = 'OK' | 'UNAVAILABLE'
export type Section<T> = { status: SectionStatus; data: T | null; errorCode: string | null; errorMessage: string | null }

export type Classification = 'OBSERVED' | 'FORECAST' | 'MODEL_PREDICTION' | 'ESTIMATED' | 'SYNTHETIC'
export type Level = 'HIGH' | 'MODERATE' | 'LOW' | 'UNKNOWN'

export type Weather = {
  status: 'OK' | 'UNAVAILABLE'
  source: string
  dataClassification: Classification | null
  fetchedAt: string
  current: { time: string; temperatureC: number | null; relativeHumidityPct: number | null; precipitationMm: number | null; windSpeedKmh: number | null } | null
  daily: { date: string; temperatureMaxC: number | null; temperatureMinC: number | null; precipitationSumMm: number | null; precipitationProbabilityMaxPct: number | null }[]
  next7Days: { totalPrecipitationMm: number; maxTemperatureC: number | null; minTemperatureC: number | null } | null
  unavailableReason: string | null
}

export type SuitabilityComponent = { name: string; score: number | null; weight: number; evidence: string }
export type Candidate = {
  crop: string
  suitabilityScore: number
  components: SuitabilityComponent[]
  evidence: string[]
  notAssessed: string[]
  history: { yearsObserved: number; firstYear: number; lastYear: number; medianYieldTPerHa: number; latestAreaHectares: number; latestProductionTonnes: number; yieldTrendPctPerYear: number | null }
}
export type Provenance = { method: string; methodVersion: string; dataSources: string[]; datasetVersion: string | null; dataClassification: Classification; generatedAt: string }
export type Suitability = { region: string; season: string; scoreType: string; candidates: Candidate[]; cropsConsidered: number; limitations: string[]; provenance: Provenance }

export type Supply = {
  state: string
  crop: string
  sourceSeasonLabel: string
  cropYear: number
  targetAreaHectares: number
  targetAreaSource: string
  forecast: { expectedProductionTonnes: number; period: string; dataClassification: Classification; predictionInterval: { lowerTonnes: number; upperTonnes: number; nominalCoverage: number; testEmpiricalCoverage: number; method: string } | null }
  baseline: { productionTonnes: number; method: string }
  history: { cropYear: number; areaHectares: number; productionTonnes: number; dataClassification: Classification }[]
  model: { modelName: string; modelVersion: string; trainingPeriod: string; evaluationPeriod: string; trainingDataSource: string | null }
  limitations: string[]
}

export type Demand = {
  crop: string
  region: string
  period: string
  forecast: number
  unit: string
  dataClassification: Classification
  interval: { lower: number; upper: number; method: string } | null
  method: string
  evidence: Record<string, unknown>
  nationalHistory: { year: number; value: number }[]
  backtests: { method: string; mapePct: number; n: number }[]
  limitations: string[]
  provenance: Provenance
}

export type Gap = {
  year: number
  supplyTonnes: number
  supplyClassification: Classification
  demandTonnes: number
  demandClassification: Classification
  gapTonnes: number
  gapPctOfDemand: number
  status: 'SURPLUS' | 'DEFICIT' | 'BALANCED'
  method: string
  caveats: string[]
}

export type Anomaly = {
  metric: string
  period: string
  status: 'NORMAL' | 'ANOMALY' | 'INSUFFICIENT_DATA'
  direction: 'HIGH' | 'LOW' | 'NONE'
  observedValue: number
  baseline: number | null
  deviationPct: number | null
  robustZ: number | null
  method: string
  unit: string | null
}

export type RiskFactor = { category: string; level: Level; reason: string; evidenceSource: string | null }
export type Decision = { status: string; headline: string; reasons: string[]; alternatives: string[]; decidedBy: string }

export type IntelligenceReport = {
  farm: { id: string; name: string; state: string; district: string; latitude: number; longitude: number; season: string; sourceSeasonLabel: string; irrigationType: string | null; currentCrop: string | null; soilPh: number | null; soilDataClassification: Classification | null; areaHectares: number }
  focusCrop: string | null
  focusCropReason: string
  weather: Weather
  suitability: Section<Suitability>
  supply: Section<Supply>
  demand: Section<Demand>
  demandOutlook: Section<Demand>
  gap: Section<Gap>
  anomalies: { metric: string; label: string; source: string; result: Section<Anomaly> }[]
  risk: { overall: Level; factors: RiskFactor[]; method: string }
  decision: Decision
  dataNotices: string[]
  generatedAt: string
}

export type DiseaseClass = { classLabel: string; crop: string; disease: string; probability: number }
export type Diagnosis = {
  ml: {
    modelName: string
    modelVersion: string
    dataClassification: Classification
    prediction: DiseaseClass
    confidence: { value: number; method: string; calibrated: boolean }
    topClasses: DiseaseClass[]
    supportedCrops: string[]
    provenance: { datasetVersion: string; trainingDataSource: string; trainingDataLicense: string; trainedAt: string }
  }
  needsExpertReview: boolean
  reviewPolicy: string
  cropWarning: string | null
  limitations: string[]
}

export type Language = 'en' | 'hi'

export type Advisory = {
  language: Language
  generatedBy: 'GEMINI' | 'TEMPLATE_FALLBACK'
  model: string
  aiGenerated: boolean
  explanation: string
  keyFactors: string[]
  recommendedActions: string[]
  uncertainty: string[]
  numbersNotFoundInEvidence: string[]
  groundingNote: string
  fallbackReason: string | null
  evidence: unknown
  generatedAt: string
}
