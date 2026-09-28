import type { WithProvenance } from '../shared/types'

/**
 * PROVISIONAL — GET /api/intelligence/crop-recommendations?farmId exists but has no response body yet.
 * Evidence per candidate crop, not a winner: the backend decision engine owns the final recommendation.
 */
export type CropRecommendation = WithProvenance & {
  farmId: string
  candidates: { crop: string; suitability: number; expectedYield: number | null; unit: string | null; confidence: number | null }[]
}
