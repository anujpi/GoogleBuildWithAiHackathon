import { useQuery } from '@tanstack/react-query'
import { getWeather } from './api'
import type { WeatherQuery } from './types'

export const useWeather = (q: WeatherQuery | null) =>
  useQuery({ queryKey: ['intelligence', 'weather', q], queryFn: ({ signal }) => getWeather(q!, signal), enabled: q !== null })
