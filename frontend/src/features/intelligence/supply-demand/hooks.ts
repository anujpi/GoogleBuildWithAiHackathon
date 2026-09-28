import { useQuery } from '@tanstack/react-query'
import { getDemandForecast, getSupplyDemandForecast, getSupplyForecast } from './api'
import type { ForecastQuery } from './types'

// Shared by the Supply & Demand page and the dashboard widget, so each forecast is fetched and cached once.

export const useSupplyDemandForecast = (q: ForecastQuery | null) =>
  useQuery({ queryKey: ['intelligence', 'supply-demand', q], queryFn: ({ signal }) => getSupplyDemandForecast(q!, signal), enabled: q !== null })

export const useSupplyForecast = (q: ForecastQuery | null) =>
  useQuery({ queryKey: ['intelligence', 'supply', q], queryFn: ({ signal }) => getSupplyForecast(q!, signal), enabled: q !== null })

export const useDemandForecast = (q: ForecastQuery | null) =>
  useQuery({ queryKey: ['intelligence', 'demand', q], queryFn: ({ signal }) => getDemandForecast(q!, signal), enabled: q !== null })
