import { api } from '@/lib/api/client'
import { qs } from '../shared/http'
import type { AgriculturalRisk, RiskQuery } from './types'

// BACKEND path; the response body is PROVISIONAL (the backend answers 503 PREDICTION_UNAVAILABLE for now).
// No mock on purpose. Add a zod schema here once the body is agreed.
export function getAgriculturalRisk(q: RiskQuery, signal?: AbortSignal): Promise<AgriculturalRisk> {
  return api<AgriculturalRisk>('/intelligence/agricultural-risk' + qs(q), { signal })
}
