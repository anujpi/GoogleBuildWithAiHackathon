import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFarm, getFarm, listFarms, updateFarm } from './api'
import type { Farm, FarmRequest } from './model'

const keys = {
  list: ['farms', 'list'] as const,
  detail: (id: string) => ['farms', 'detail', id] as const,
}

export const useFarms = () => useQuery({ queryKey: keys.list, queryFn: ({ signal }) => listFarms(signal) })

export const useFarm = (id: string) => useQuery({ queryKey: keys.detail(id), queryFn: ({ signal }) => getFarm(id, signal) })

function useOnSaved() {
  const qc = useQueryClient()
  return (farm: Farm) => {
    qc.setQueryData(keys.detail(farm.id), farm)
    return qc.invalidateQueries({ queryKey: keys.list })
  }
}

export const useCreateFarm = () => useMutation({ mutationFn: createFarm, onSuccess: useOnSaved() })

export const useUpdateFarm = (id: string) =>
  useMutation({ mutationFn: (body: FarmRequest) => updateFarm(id, body), onSuccess: useOnSaved() })
