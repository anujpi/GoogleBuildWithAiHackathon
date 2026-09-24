import { api } from '@/lib/api/client'
import type { Farm, FarmRequest } from './model'

export const listFarms = (signal?: AbortSignal) => api<Farm[]>('/farms', { signal })

export const getFarm = (id: string, signal?: AbortSignal) => api<Farm>(`/farms/${encodeURIComponent(id)}`, { signal })

export const createFarm = (body: FarmRequest) => api<Farm>('/farms', { method: 'POST', body })

export const updateFarm = (id: string, body: FarmRequest) => api<Farm>(`/farms/${encodeURIComponent(id)}`, { method: 'PUT', body })
