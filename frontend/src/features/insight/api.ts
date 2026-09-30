import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { CropEvidence, ReferenceScope, RiskAssessment, SupplyEstimate, SupplyQuery, Weather } from './types'

// Every call goes React → Spring Boot. No mock fallback: a failed request is shown as unavailable.

const qs = (params: Record<string, string | number | undefined>) =>
  '?' + new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined).map(([k, v]) => [k, String(v)])).toString()

const farmPath = (id: string) => `/farms/${encodeURIComponent(id)}`

export const useReferenceScope = () =>
  useQuery({ queryKey: ['reference', 'scope'], queryFn: ({ signal }) => api<ReferenceScope>('/reference/scope', { signal }), staleTime: 5 * 60_000 })

export const useWeather = (point: { latitude: number; longitude: number } | null, days = 7) =>
  useQuery({
    queryKey: ['weather', point?.latitude, point?.longitude, days],
    queryFn: ({ signal }) => api<Weather>(`/weather${qs({ latitude: point!.latitude, longitude: point!.longitude, days })}`, { signal }),
    enabled: point !== null,
  })

export const useCropEvidence = (farmId: string, enabled = true) =>
  useQuery({
    queryKey: ['farms', 'crop-evidence', farmId],
    queryFn: ({ signal }) => api<CropEvidence>(`${farmPath(farmId)}/crop-evidence`, { signal }),
    enabled,
  })

export const useRisk = (farmId: string, cropId: string | null) =>
  useQuery({
    queryKey: ['farms', 'risk', farmId, cropId],
    queryFn: ({ signal }) => api<RiskAssessment>(`${farmPath(farmId)}/risk${qs({ cropId: cropId! })}`, { signal }),
    enabled: cropId !== null,
  })

export const useSupply = (q: SupplyQuery | null) =>
  useQuery({
    queryKey: ['intelligence', 'supply', q],
    queryFn: ({ signal }) => api<SupplyEstimate>(`/intelligence/supply${qs({ ...q! })}`, { signal }),
    enabled: q !== null,
  })
