import { z } from 'zod'
import { api } from '@/lib/api/client'
import { provenanceSchema, qs, validated } from '../shared/http'
import type { Weather, WeatherQuery } from './types'

// Always the real backend, in both modes: the endpoint exists (its provider currently labels its data SYNTHETIC).

const weatherSchema = z.object({
  farmId: z.string().nullable(),
  latitude: z.number(),
  longitude: z.number(),
  provenance: provenanceSchema,
  current: z.object({ observedAt: z.string(), temperatureC: z.number(), relativeHumidityPct: z.number(), rainfallMm: z.number(), windSpeedKmh: z.number() }),
  daily: z.array(z.object({ date: z.string(), minTemperatureC: z.number(), maxTemperatureC: z.number(), rainfallMm: z.number(), rainProbabilityPct: z.number(), relativeHumidityPct: z.number() })),
})

export function getWeather(q: WeatherQuery, signal?: AbortSignal): Promise<Weather | null> {
  const path = 'farmId' in q ? `/weather/farms/${encodeURIComponent(q.farmId)}${qs({ days: q.days })}` : `/weather${qs({ latitude: q.latitude, longitude: q.longitude, days: q.days })}`
  return api<unknown>(path, { signal }).then(validated(weatherSchema, 'weather'))
}
