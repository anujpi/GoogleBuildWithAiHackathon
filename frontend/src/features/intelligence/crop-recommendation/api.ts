import { api } from '@/lib/api/client'
import { qs } from '../shared/http'
import type { CropRecommendation } from './types'

// BACKEND path; the response body is PROVISIONAL (the backend answers 503 PREDICTION_UNAVAILABLE for now).
// No mock on purpose. Add a zod schema here once the body is agreed.
export function getCropRecommendation(farmId: string, signal?: AbortSignal): Promise<CropRecommendation> {
  return api<CropRecommendation>('/intelligence/crop-recommendations' + qs({ farmId }), { signal })
}
