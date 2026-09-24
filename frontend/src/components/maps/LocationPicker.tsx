import { MapPinOff } from 'lucide-react'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { cn } from 'cn'
import { LoadingState } from '@/components/feedback/states'
import { BASEMAP_STYLE_URL, maplibregl, prefersReducedMotion } from './maplibre'

export type LatLon = { lat: number; lon: number }

type LocationPickerProps = {
  /** The point to show. null = nothing selected yet. */
  value: LatLon | null
  /** Omit for a read-only map. */
  onPick?: (point: LatLon) => void
  label: string
  className?: string
}

const INDIA: LatLon = { lat: 22.5, lon: 79 }
const round6 = (n: number) => Math.round(n * 1e6) / 1e6

/** Single-pin map. Clicking sets the point; the coordinate inputs beside it remain the accessible way to do the same. */
export function LocationPicker({ value, onPick, label, className }: LocationPickerProps) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const markerRef = useRef<maplibregl.Marker | null>(null)
  const initial = useRef(value)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const pick = useEffectEvent((p: LatLon) => onPick?.(p))
  const interactive = Boolean(onPick)

  useEffect(() => {
    const start = initial.current ?? INDIA
    const map = new maplibregl.Map({
      container: container.current!,
      style: BASEMAP_STYLE_URL,
      center: [start.lon, start.lat],
      zoom: initial.current ? 10 : 3.6,
      attributionControl: { compact: true },
      cooperativeGestures: true,
    })
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
    map.on('load', () => setStatus('ready'))
    map.on('error', () => {
      if (!map.isStyleLoaded()) setStatus('error')
    })
    if (interactive) {
      map.getCanvas().style.cursor = 'crosshair'
      map.on('click', (e) => pick({ lat: round6(e.lngLat.lat), lon: round6(e.lngLat.lng) }))
    }
    mapRef.current = map
    return () => map.remove()
  }, [interactive])

  useEffect(() => {
    const map = mapRef.current
    markerRef.current?.remove()
    markerRef.current = null
    if (!map || status !== 'ready' || !value) return
    const el = document.createElement('div')
    el.className = 'size-4 rounded-full border-[3px] border-white bg-primary shadow-md ring-2 ring-primary/40'
    markerRef.current = new maplibregl.Marker({ element: el }).setLngLat([value.lon, value.lat]).addTo(map)
    if (!map.getBounds().contains([value.lon, value.lat]))
      map.easeTo({ center: [value.lon, value.lat], zoom: Math.max(map.getZoom(), 8), duration: prefersReducedMotion() ? 0 : 500 })
  }, [value, status])

  return (
    <div className={cn('relative isolate overflow-hidden bg-muted', className)}>
      {/* size-full, not absolute: maplibre's own CSS forces position: relative on this element */}
      <div ref={container} className="size-full" role="region" aria-label={label} />
      {status === 'loading' && (
        <div className="absolute inset-0 grid place-items-center bg-muted/80">
          <LoadingState message="Loading map..." />
        </div>
      )}
      {status === 'error' && (
        <div role="status" className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-muted p-6 text-center text-sm text-muted-foreground">
          <MapPinOff className="size-5" aria-hidden />
          <p>The map could not be loaded.{interactive && ' Enter the coordinates in the fields instead.'}</p>
        </div>
      )}
      {status === 'ready' && interactive && (
        <p className="pointer-events-none absolute top-2 left-2 rounded-sm border bg-popover/95 px-2 py-1 text-xs text-muted-foreground shadow-sm">
          Click the map to place the farm
        </p>
      )}
    </div>
  )
}
