// Types mirror the backend farm DTOs exactly (POST/PUT /farms request, farm response).
// Enum values match backend/src/main/java/.../farm/entity/*.java.

export const AREA_UNITS = ['ACRE', 'HECTARE'] as const
export const IRRIGATION_TYPES = ['RAIN_FED', 'DRIP', 'SPRINKLER', 'CANAL', 'BOREWELL', 'OTHER'] as const
export const SEASONS = ['KHARIF', 'RABI', 'ZAID', 'OTHER'] as const
export const SOIL_SOURCES = ['SOIL_HEALTH_CARD', 'LAB_REPORT', 'MANUAL', 'REGIONAL_ESTIMATE', 'OTHER'] as const
export const SOIL_CLASSIFICATIONS = ['OBSERVED', 'ESTIMATED', 'SYNTHETIC'] as const

export type AreaUnit = (typeof AREA_UNITS)[number]
export type IrrigationType = (typeof IRRIGATION_TYPES)[number]
export type Season = (typeof SEASONS)[number]
export type SoilSource = (typeof SOIL_SOURCES)[number]
export type SoilClassification = (typeof SOIL_CLASSIFICATIONS)[number]

export const SOIL_METRICS = [
  'ph', 'electricalConductivity', 'organicCarbon',
  'nitrogen', 'phosphorus', 'potassium',
  'sulphur', 'zinc', 'iron', 'manganese', 'copper', 'boron',
] as const
export type SoilMetric = (typeof SOIL_METRICS)[number]

export type FarmLocation = {
  latitude: number
  longitude: number
  state: string
  district: string
  taluk: string | null
  addressLabel: string | null
}

export type SoilProfile = Record<SoilMetric, number | null> & {
  source: SoilSource
  dataClassification: SoilClassification
  /** ISO date, YYYY-MM-DD. */
  measuredAt: string | null
  /** 0–1. */
  confidence: number | null
}

export type FarmRequest = {
  name: string
  area: number
  areaUnit: AreaUnit
  irrigationType: IrrigationType
  currentCrop: string | null
  previousCrop: string | null
  season: Season
  location: FarmLocation
  /** null or omitted = no soil data for this farm. On PUT, null removes a stored profile. */
  soilProfile: SoilProfile | null
}

export type Farm = FarmRequest & {
  id: string
  /** Backend-authoritative. false means soilProfile is null: show soil as unavailable, never as zeros. */
  soilDataAvailable: boolean
  createdAt: string
  updatedAt: string
}

/** Mirrors SoilDataSource.permits on the backend; other pairs are rejected with INCONSISTENT_SOIL_PROVENANCE. */
export const ALLOWED_CLASSIFICATIONS: Record<SoilSource, readonly SoilClassification[]> = {
  SOIL_HEALTH_CARD: ['OBSERVED'],
  LAB_REPORT: ['OBSERVED'],
  MANUAL: SOIL_CLASSIFICATIONS,
  REGIONAL_ESTIMATE: ['ESTIMATED', 'SYNTHETIC'],
  OTHER: SOIL_CLASSIFICATIONS,
}

/** The soil profile only when the backend says soil data exists. */
export const soilOf = (f: Farm) => (f.soilDataAvailable ? f.soilProfile : null)

// Display labels -------------------------------------------------------------------------

export const areaUnitLabel: Record<AreaUnit, { short: string; long: string }> = {
  ACRE: { short: 'ac', long: 'Acres' },
  HECTARE: { short: 'ha', long: 'Hectares' },
}

export const irrigationLabel: Record<IrrigationType, string> = {
  RAIN_FED: 'Rain-fed',
  DRIP: 'Drip',
  SPRINKLER: 'Sprinkler',
  CANAL: 'Canal',
  BOREWELL: 'Borewell',
  OTHER: 'Other',
}

export const seasonLabel: Record<Season, { label: string; hint: string }> = {
  KHARIF: { label: 'Kharif', hint: 'Monsoon, Jun–Oct' },
  RABI: { label: 'Rabi', hint: 'Winter, Oct–Mar' },
  ZAID: { label: 'Zaid', hint: 'Summer, Mar–Jun' },
  OTHER: { label: 'Other', hint: 'Perennial or mixed' },
}

export const soilSourceLabel: Record<SoilSource, { label: string; hint: string }> = {
  SOIL_HEALTH_CARD: { label: 'Soil Health Card', hint: 'Government-issued card for this farm' },
  LAB_REPORT: { label: 'Lab report', hint: 'Private or institutional soil test' },
  MANUAL: { label: 'Manual entry', hint: 'Values known from another record' },
  REGIONAL_ESTIMATE: { label: 'Regional estimate', hint: 'Typical values for the area, not this farm' },
  OTHER: { label: 'Other', hint: 'Any other source' },
}

export const classificationLabel: Record<SoilClassification, { label: string; hint: string }> = {
  OBSERVED: { label: 'Observed', hint: 'Measured on this farm' },
  ESTIMATED: { label: 'Estimated', hint: 'Inferred, not measured on this farm' },
  SYNTHETIC: { label: 'Synthetic', hint: 'Demo data, not real' },
}

/** Units follow the Soil Health Card convention. */
export const SOIL_GROUPS: { label: string; metrics: { key: SoilMetric; label: string; unit: string; max?: number }[] }[] = [
  {
    label: 'Reaction & carbon',
    metrics: [
      { key: 'ph', label: 'pH', unit: '', max: 14 },
      { key: 'electricalConductivity', label: 'Electrical conductivity', unit: 'dS/m' },
      { key: 'organicCarbon', label: 'Organic carbon', unit: '%' },
    ],
  },
  {
    label: 'Macronutrients',
    metrics: [
      { key: 'nitrogen', label: 'Nitrogen (N)', unit: 'kg/ha' },
      { key: 'phosphorus', label: 'Phosphorus (P)', unit: 'kg/ha' },
      { key: 'potassium', label: 'Potassium (K)', unit: 'kg/ha' },
    ],
  },
  {
    label: 'Secondary & micronutrients',
    metrics: [
      { key: 'sulphur', label: 'Sulphur (S)', unit: 'ppm' },
      { key: 'zinc', label: 'Zinc (Zn)', unit: 'ppm' },
      { key: 'iron', label: 'Iron (Fe)', unit: 'ppm' },
      { key: 'manganese', label: 'Manganese (Mn)', unit: 'ppm' },
      { key: 'copper', label: 'Copper (Cu)', unit: 'ppm' },
      { key: 'boron', label: 'Boron (B)', unit: 'ppm' },
    ],
  },
]

export const locationLabel = (l: FarmLocation) => l.addressLabel || [l.taluk, l.district, l.state].filter(Boolean).join(', ')

export const formatCoords = (lat: number, lon: number) =>
  `${Math.abs(lat).toFixed(4)}° ${lat >= 0 ? 'N' : 'S'}, ${Math.abs(lon).toFixed(4)}° ${lon >= 0 ? 'E' : 'W'}`

export const isBlank = (v: number | string | null) => v === null || v === ''

/** How many of the twelve soil values are present. Works on both form values and saved farms. */
export const countReadings = (r: Record<SoilMetric, number | string | null>) => SOIL_METRICS.filter((k) => !isBlank(r[k])).length
