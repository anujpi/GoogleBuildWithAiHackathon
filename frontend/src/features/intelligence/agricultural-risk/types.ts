import type { RiskLevel } from '@/types/status'
import type { WithProvenance } from '../shared/types'

export type RiskQuery = { regionId: string; cropId: string }

/** PROVISIONAL — GET /api/intelligence/agricultural-risk?regionId&cropId exists but has no response body yet. */
export type AgriculturalRisk = WithProvenance & {
  regionId: string
  cropId: string
  risks: { category: string; level: RiskLevel; summary: string; factors: string[] }[]
}
