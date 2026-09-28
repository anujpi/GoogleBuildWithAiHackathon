import type { Provenance } from '../shared/types'

/** BACKEND — weather.dto.WeatherResponse (GET /api/weather, GET /api/weather/farms/{farmId}). Units are in field names. */
export type Weather = {
  provenance: Provenance
  /** null for coordinate lookups. */
  farmId: string | null
  latitude: number
  longitude: number
  current: { observedAt: string; temperatureC: number; relativeHumidityPct: number; rainfallMm: number; windSpeedKmh: number }
  daily: { date: string; minTemperatureC: number; maxTemperatureC: number; rainfallMm: number; rainProbabilityPct: number; relativeHumidityPct: number }[]
}

/** days: 1–14, backend default 7. */
export type WeatherQuery = { farmId: string; days?: number } | { latitude: number; longitude: number; days?: number }
