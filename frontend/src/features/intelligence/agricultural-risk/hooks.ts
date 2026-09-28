import { useQuery } from '@tanstack/react-query'
import { getAgriculturalRisk } from './api'
import type { RiskQuery } from './types'

export const useAgriculturalRisk = (q: RiskQuery | null) =>
  useQuery({ queryKey: ['intelligence', 'risk', q], queryFn: ({ signal }) => getAgriculturalRisk(q!, signal), enabled: q !== null })
