import { api } from '@/lib/api/client'
import type { Advisory, Diagnosis, IntelligenceReport, Language } from './types'

export const getIntelligence = (farmId: string, crop: string | null, signal?: AbortSignal) =>
  api<IntelligenceReport>(`/intelligence/farms/${encodeURIComponent(farmId)}${crop ? `?crop=${encodeURIComponent(crop)}` : ''}`, { signal })

export function diagnoseLeaf(image: File, crop: string | null) {
  const form = new FormData()
  form.append('image', image)
  if (crop) form.append('crop', crop)
  return api<Diagnosis>('/disease/diagnose', { method: 'POST', body: form })
}

export type AdvisoryRequest = {
  farmId: string
  crop: string | null
  language: Language
  disease: { crop: string; disease: string; probability: number; modelVersion: string; needsExpertReview: boolean } | null
}

export const requestAdvisory = (body: AdvisoryRequest) => api<Advisory>('/advisories', { method: 'POST', body })
