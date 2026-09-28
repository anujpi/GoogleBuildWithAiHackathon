import { useQuery } from '@tanstack/react-query'
import { getCropRecommendation } from './api'

export const useCropRecommendation = (farmId: string | null) =>
  useQuery({ queryKey: ['intelligence', 'crop-recommendation', farmId], queryFn: ({ signal }) => getCropRecommendation(farmId!, signal), enabled: farmId !== null })
