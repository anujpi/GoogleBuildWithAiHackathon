// Shared plumbing for intelligence API functions. All requests go through lib/api/client (React → Spring Boot).

import { z } from 'zod'
import { ApiError } from '@/lib/api/client'

/**
 * VITE_INTELLIGENCE_SOURCE=api → supply-demand comes from Spring Boot.
 * Anything else (default "mock") → supply-demand comes from the frontend mock, classified SYNTHETIC.
 * Only supply-demand has a mock. Every other endpoint always calls the backend, which answers
 * 503 PREDICTION_UNAVAILABLE until a prediction source is connected. A failed real request is never
 * replaced by mock data.
 */
export const intelligenceSource: 'mock' | 'api' = import.meta.env.VITE_INTELLIGENCE_SOURCE === 'api' ? 'api' : 'mock'

export const qs = (params: Record<string, string | number | undefined>) =>
  '?' + new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined).map(([k, v]) => [k, String(v)])).toString()

export const classificationSchema = z.enum(['OBSERVED', 'FORECAST', 'MODEL_PREDICTION', 'REGIONAL_ESTIMATE', 'SYNTHETIC'])
export const provenanceSchema = z.object({ source: z.string(), dataClassification: classificationSchema, retrievedAt: z.string(), confidence: z.number().min(0).max(1).nullable() })
export const intelligenceProvenanceSchema = z.object({
  source: z.string(),
  dataClassification: classificationSchema,
  generatedAt: z.string(),
  modelVersion: z.string().nullable(),
  confidence: z.number().min(0).max(1).nullable(),
  limitations: z.array(z.string()),
})
export const optionSchema = z.object({ id: z.string(), label: z.string() })

/**
 * Checks a response body against its contract. 204/empty body → null (the caller shows an empty state);
 * anything that doesn't match → MALFORMED_RESPONSE, so a bad body becomes an error state, never a half-drawn chart.
 */
export function validated<T>(schema: z.ZodType<T>, what: string) {
  return (data: unknown): T | null => {
    if (data === undefined || data === null) return null
    const result = schema.safeParse(data)
    if (result.success) return result.data
    console.error(`Malformed ${what} response`, result.error.issues)
    throw new ApiError(502, 'MALFORMED_RESPONSE', `The server sent ${what} data in an unexpected format.`)
  }
}
