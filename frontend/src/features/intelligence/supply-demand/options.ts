import type { Option } from '../shared/types'

// MVP selector options. Static until the backend exposes reference data.
export const forecastOptions: { regions: Option[]; crops: Option[]; periods: (Option & { horizonMonths: number })[] } = {
  regions: [
    { id: 'mh', label: 'Maharashtra' },
    { id: 'ka', label: 'Karnataka' },
  ],
  crops: [
    { id: 'onion', label: 'Onion' },
    { id: 'tomato', label: 'Tomato' },
    { id: 'soybean', label: 'Soybean' },
  ],
  periods: [
    { id: 'next-3m', label: 'Next 3 months', horizonMonths: 3 },
    { id: 'next-6m', label: 'Next 6 months', horizonMonths: 6 },
  ],
}
