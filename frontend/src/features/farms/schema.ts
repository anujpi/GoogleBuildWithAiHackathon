import { z } from 'zod'
import {
  ALLOWED_CLASSIFICATIONS,
  AREA_UNITS,
  IRRIGATION_TYPES,
  SEASONS,
  SOIL_CLASSIFICATIONS,
  SOIL_METRICS,
  SOIL_SOURCES,
  soilOf,
  type Farm,
  type FarmRequest,
  type SoilProfile,
  type SoilMetric,
  type SoilSource,
} from './model'

// Inputs are kept as strings so an empty soil field stays "missing" instead of becoming 0.
// Limits mirror backend/src/main/resources/db/migration/V1__create_farm.sql.

const numberText = z
  .string()
  .trim()
  .refine((v) => v === '' || Number.isFinite(Number(v)), { message: 'Enter a number', abort: true })

const requiredNumber = (requiredMessage: string, n: z.ZodNumber) =>
  numberText.refine((v) => v !== '', { message: requiredMessage, abort: true }).transform(Number).pipe(n)

const optionalNumber = (n: z.ZodNumber) => numberText.transform((v) => (v === '' ? null : Number(v))).pipe(n.nullable())

const requiredText = (label: string, max: number) =>
  z.string().trim().min(1, `Enter the ${label}`).max(max, `Keep the ${label} under ${max} characters`)

const optionalText = (label: string, max: number) =>
  z.string().trim().max(max, `Keep the ${label} under ${max} characters`).transform((v) => v || null)

const choice = <T extends readonly [string, ...string[]]>(values: T, message: string) =>
  // An unchecked radio group reports null, so the "missing" message must cover that too.
  z.string({ error: message }).pipe(z.enum(values, { error: message }))

const localToday = () => new Date().toLocaleDateString('en-CA') // YYYY-MM-DD in the user's timezone

const nonNegative = (label: string) => z.number().min(0, `${label} cannot be negative`)

const soilValues = Object.fromEntries(
  SOIL_METRICS.map((key) => [key, optionalNumber(key === 'ph' ? z.number().min(0, 'pH is between 0 and 14').max(14, 'pH is between 0 and 14') : nonNegative('Value'))]),
) as Record<SoilMetric, ReturnType<typeof optionalNumber>>

const soilProfileSchema = z
  .object({
    ...soilValues,
    source: choice(SOIL_SOURCES, 'Choose where these soil values come from'),
    dataClassification: choice(SOIL_CLASSIFICATIONS, 'Choose how these values were obtained'),
    measuredAt: z
      .string()
      .refine((v) => v === '' || v <= localToday(), 'The measurement date cannot be in the future')
      .transform((v) => v || null),
    confidence: optionalNumber(z.number().min(0, 'Confidence is between 0 and 1').max(1, 'Confidence is between 0 and 1')),
  })
  .refine((s) => ALLOWED_CLASSIFICATIONS[s.source].includes(s.dataClassification), {
    error: (issue) => {
      const source = (issue.input as { source: SoilSource }).source
      return source === 'REGIONAL_ESTIMATE'
        ? 'A regional estimate is not a measurement on this farm. Choose Estimated or Synthetic.'
        : 'A Soil Health Card or lab report is a measurement of this farm. Choose Observed.'
    },
    path: ['dataClassification'],
  })

// Raw soil inputs. They are validated only when soil data is provided, so entries hidden behind
// "no soil data" stay in the form (switching back restores them) but are never checked or sent.
const soilInputs = z.object({
  ...(Object.fromEntries(SOIL_METRICS.map((k) => [k, z.string()])) as Record<SoilMetric, z.ZodString>),
  source: z.string().nullable(),
  dataClassification: z.string().nullable(),
  measuredAt: z.string(),
  confidence: z.string(),
})

const farmBase = z.object({
  location: z.object({
    latitude: requiredNumber('Enter a latitude', z.number().min(-90, 'Latitude must be between -90 and 90').max(90, 'Latitude must be between -90 and 90')),
    longitude: requiredNumber('Enter a longitude', z.number().min(-180, 'Longitude must be between -180 and 180').max(180, 'Longitude must be between -180 and 180')),
    state: requiredText('state', 100),
    district: requiredText('district', 100),
    taluk: optionalText('taluk', 100),
    addressLabel: optionalText('address label', 255),
  }),
  name: requiredText('farm name', 200),
  area: requiredNumber('Enter the farm area', z.number().gt(0, 'Area must be greater than 0')),
  areaUnit: choice(AREA_UNITS, 'Choose acres or hectares'),
  irrigationType: choice(IRRIGATION_TYPES, 'Choose an irrigation type'),
  currentCrop: optionalText('crop name', 100),
  previousCrop: optionalText('crop name', 100),
  season: choice(SEASONS, 'Choose a season'),
})

/** `soilData` is form-only: "unavailable" sends soilProfile: null, which the backend stores as no soil data. */
export const farmSchema = farmBase
  .extend({ soilData: z.enum(['provided', 'unavailable']), soilProfile: soilInputs })
  .transform(({ soilData, soilProfile, ...farm }, ctx) => {
    if (soilData === 'unavailable') return { ...farm, soilProfile: null }
    const soil = soilProfileSchema.safeParse(soilProfile)
    if (soil.success) return { ...farm, soilProfile: soil.data }
    for (const issue of soil.error.issues) ctx.addIssue({ code: 'custom', message: issue.message, path: ['soilProfile', ...issue.path] })
    return z.NEVER
  })

export type FarmFormValues = z.input<typeof farmSchema>

// Compile-time guarantee that a parsed form is exactly the request body the backend expects.
export const toRequest = (parsed: z.output<typeof farmSchema>): FarmRequest => parsed

const str = (v: number | string | null) => (v === null ? '' : String(v))

const emptySoil = {
  ...(Object.fromEntries(SOIL_METRICS.map((k) => [k, ''])) as Record<SoilMetric, string>),
  source: '',
  dataClassification: '',
  measuredAt: '',
  confidence: '',
}

export const emptyFarmForm: FarmFormValues = {
  location: { latitude: '', longitude: '', state: '', district: '', taluk: '', addressLabel: '' },
  name: '',
  area: '',
  areaUnit: '',
  irrigationType: '',
  currentCrop: '',
  previousCrop: '',
  season: '',
  soilData: 'provided',
  soilProfile: emptySoil,
}

export const farmToForm = (f: Farm): FarmFormValues => ({
  location: {
    latitude: str(f.location.latitude),
    longitude: str(f.location.longitude),
    state: f.location.state,
    district: f.location.district,
    taluk: str(f.location.taluk),
    addressLabel: str(f.location.addressLabel),
  },
  name: f.name,
  area: str(f.area),
  areaUnit: f.areaUnit,
  irrigationType: f.irrigationType,
  currentCrop: str(f.currentCrop),
  previousCrop: str(f.previousCrop),
  season: f.season,
  ...soilToForm(soilOf(f)),
})

function soilToForm(s: SoilProfile | null): Pick<FarmFormValues, 'soilData' | 'soilProfile'> {
  if (!s) return { soilData: 'unavailable', soilProfile: emptySoil }
  return {
    soilData: 'provided',
    soilProfile: {
      ...(Object.fromEntries(SOIL_METRICS.map((k) => [k, str(s[k])])) as Record<SoilMetric, string>),
      source: s.source,
      dataClassification: s.dataClassification,
      measuredAt: str(s.measuredAt),
      confidence: str(s.confidence),
    },
  }
}

/** Which wizard step owns a field path — used to route server validation errors to the right step. */
export function stepOfField(field: string): number | null {
  if (field.startsWith('location')) return 0
  if (field.startsWith('soilProfile') || field === 'soilData') return 2
  if (['name', 'area', 'areaUnit', 'irrigationType', 'currentCrop', 'previousCrop', 'season'].includes(field)) return 1
  return null
}
