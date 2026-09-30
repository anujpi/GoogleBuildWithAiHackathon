import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { shouldRetry } from '@/lib/api/client'
import { diagnoseLeaf, getIntelligence, requestAdvisory } from './api'

export const useIntelligence = (farmId: string | null, crop: string | null) =>
  useQuery({
    queryKey: ['intelligence', farmId, crop],
    queryFn: ({ signal }) => getIntelligence(farmId!, crop, signal),
    enabled: farmId !== null,
    retry: shouldRetry,
    placeholderData: keepPreviousData,
  })

export const useDiagnose = () => useMutation({ mutationFn: ({ image, crop }: { image: File; crop: string | null }) => diagnoseLeaf(image, crop) })

export const useAdvisory = () => useMutation({ mutationFn: requestAdvisory })
