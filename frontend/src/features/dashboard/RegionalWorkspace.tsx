import { useMemo, useState } from 'react'
import { cn } from 'cn'
import { ConfidenceBadge, DataOriginBadge, GapBadge } from '@/components/data-display/status'
import { Panel } from '@/components/layout/Panel'
import { MapPanel, type MapLayer } from '@/components/maps/MapPanel'
import { formatNumber } from '@/lib/format'
import type { DashboardSummary, DistrictSignal } from './types'

const MAHARASHTRA: [number, number] = [75.7, 19.2]

export function RegionalWorkspace({ districts, farm }: { districts: DistrictSignal[]; farm: DashboardSummary['farm'] }) {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const sorted = useMemo(() => [...districts].sort((a, b) => b.gapKt - a.gapKt), [districts])
  const layers = useMemo<MapLayer[]>(
    () => [
      {
        id: 'gap',
        label: 'District supply–demand gap',
        markers: districts.map((d) => ({ id: d.id, lon: d.lon, lat: d.lat, label: d.name, tone: d.gap })),
      },
      { id: 'farm', label: 'Selected farm', markers: [{ id: farm.id, lon: farm.lon, lat: farm.lat, label: farm.name, tone: 'farm' }] },
    ],
    [districts, farm],
  )

  return (
    <Panel
      title="Regional supply–demand workspace"
      description="Onion · projected season gap by district · thousand tonnes"
      actions={<DataOriginBadge origin="synthetic" />}
      bodyClassName="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_16rem]"
    >
      <MapPanel
        className="h-[360px] lg:h-[500px]"
        layers={layers}
        selectedId={selectedId}
        onSelect={setSelectedId}
        center={MAHARASHTRA}
        zoom={5.4}
      />
      <div className="flex max-h-[500px] flex-col border-t lg:border-t-0 lg:border-l">
        <div className="border-b px-3 py-2 text-xs font-medium text-muted-foreground">Districts by projected gap</div>
        <ul className="flex-1 overflow-y-auto">
          {sorted.map((d) => (
            <li key={d.id} className="border-b last:border-b-0">
              <button
                type="button"
                aria-pressed={d.id === selectedId}
                onClick={() => setSelectedId(d.id === selectedId ? null : d.id)}
                className={cn(
                  'flex w-full flex-col gap-1.5 px-3 py-2.5 text-left outline-none hover:bg-muted focus-visible:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset',
                  d.id === selectedId && 'bg-accent',
                )}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-medium">{d.name}</span>
                  <span className="tabular text-sm">
                    {d.gapKt > 0 ? '+' : ''}
                    {formatNumber(d.gapKt)}
                    <span className="ml-0.5 text-xs text-muted-foreground">kt</span>
                  </span>
                </span>
                <span className="flex items-center justify-between gap-2">
                  <GapBadge state={d.gap} />
                  <ConfidenceBadge value={d.confidence} />
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </Panel>
  )
}
