import { useQuery } from '@tanstack/react-query'
import { getDashboardContext, getDashboardSummary } from './api'
import type { DashboardQuery } from './types'

export const useDashboardContext = () =>
  useQuery({ queryKey: ['dashboard', 'context'], queryFn: getDashboardContext, staleTime: Infinity })

export const useDashboardSummary = (q: DashboardQuery | null) =>
  useQuery({
    queryKey: ['dashboard', 'summary', q],
    queryFn: () => getDashboardSummary(q!),
    enabled: q !== null,
  })
