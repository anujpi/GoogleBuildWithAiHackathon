import { ChevronDown, Layers } from 'lucide-react'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { cn } from 'cn'
import { ErrorState, LoadingState } from '@/components/feedback/states'
import type { GapState } from '@/types/status'
import { BASEMAP_STYLE_URL, maplibregl, prefersReducedMotion } from './maplibre'

export type MapTone = GapState | 'farm'
export type MapMarker = { id: string; lon: number; lat: number; label: string; tone: MapTone }
export type MapLayer = { id: string; label: string; markers: MapMarker[] }

// Shape + glyph + colour, so tone never relies on colour alone.
const tones: Record<MapTone, { label: string; glyph: string; className: string }> = {
  surplus: { label: 'Surplus', glyph: '▲', className: 'bg-warning text-white' },
  balanced: { label: 'Balanced', glyph: '●', className: 'bg-positive text-white' },
  shortage: { label: 'Shortage', glyph: '▼', className: 'bg-critical text-white' },
  farm: { label: 'Selected farm', glyph: '◆', className: 'bg-foreground text-background rounded-sm!' },
}

type MapPanelProps = {
  layers: MapLayer[]
  selectedId?: string | null
  onSelect?: (id: string) => void
  center: [number, number]
  zoom: number
  className?: string
}

// ponytail: HTML markers (keyboard-focusable buttons); move to a GeoJSON circle layer past a few hundred points.
export function MapPanel({ layers, selectedId, onSelect, center, zoom, className }: MapPanelProps) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const initialView = useRef({ center, zoom })
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [hidden, setHidden] = useState<Set<string>>(() => new Set())
  const [controlsOpen] = useState(() => window.matchMedia('(min-width: 640px)').matches)
  const fitted = useRef(false)
  const select = useEffectEvent((id: string) => onSelect?.(id))

  useEffect(() => {
    const map = new maplibregl.Map({
      container: container.current!,
      style: BASEMAP_STYLE_URL,
      ...initialView.current,
      attributionControl: { compact: true },
      cooperativeGestures: true,
    })
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
    map.on('load', () => setStatus('ready'))
    map.on('error', () => {
      if (!map.isStyleLoaded()) setStatus('error')
    })
    mapRef.current = map
    return () => map.remove()
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map || status !== 'ready') return
    const markers = layers
      .filter((layer) => !hidden.has(layer.id))
      .flatMap((layer) => layer.markers)
      .map((m) => {
        const tone = tones[m.tone]
        const el = document.createElement('button')
        el.type = 'button'
        el.setAttribute('aria-label', `${m.label} (${tone.label})`)
        el.setAttribute('aria-pressed', String(m.id === selectedId))
        el.title = m.label
        el.textContent = tone.glyph
        el.className = cn(
          'flex size-6 cursor-pointer items-center justify-center rounded-full border-2 border-white text-[10px] leading-none shadow-md outline-none',
          'focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1',
          tone.className,
          m.id === selectedId && 'z-10 size-8 ring-2 ring-foreground',
        )
        el.addEventListener('click', () => select(m.id))
        return new maplibregl.Marker({ element: el }).setLngLat([m.lon, m.lat]).addTo(map)
      })
    return () => markers.forEach((marker) => marker.remove())
  }, [layers, hidden, selectedId, status])

  // Frame all markers once, so the view suits any container size instead of a fixed zoom.
  useEffect(() => {
    const map = mapRef.current
    const points = layers.flatMap((l) => l.markers)
    if (!map || status !== 'ready' || fitted.current || points.length === 0) return
    const bounds = new maplibregl.LngLatBounds()
    points.forEach((p) => bounds.extend([p.lon, p.lat]))
    map.fitBounds(bounds, { padding: 48, maxZoom: 8, duration: 0 })
    fitted.current = true
  }, [layers, status])

  useEffect(() => {
    const map = mapRef.current
    const target = layers.flatMap((l) => l.markers).find((m) => m.id === selectedId)
    if (!map || status !== 'ready' || !target) return
    map.easeTo({ center: [target.lon, target.lat], duration: prefersReducedMotion() ? 0 : 600 })
  }, [layers, selectedId, status])

  const toggle = (id: string) =>
    setHidden((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })

  const usedTones = [...new Set(layers.filter((l) => !hidden.has(l.id)).flatMap((l) => l.markers.map((m) => m.tone)))]

  return (
    <div className={cn('relative isolate overflow-hidden bg-muted', className)}>
      {/* size-full, not absolute: maplibre's own CSS forces position: relative on this element */}
      <div ref={container} className="size-full" role="region" aria-label="Map of district supply-demand signals" />

      {status === 'loading' && (
        <div className="absolute inset-0 grid place-items-center bg-muted/80">
          <LoadingState message="Loading map workspace..." />
        </div>
      )}
      {status === 'error' && (
        <div className="absolute inset-0 grid place-items-center bg-muted">
          <ErrorState message="The basemap could not be loaded. The same data is available in the list alongside." />
        </div>
      )}

      {status === 'ready' && (
        // Layers and legend share one box so nothing collides with MapLibre's attribution bar.
        // Native <details>: collapsed by default on narrow screens so the map stays visible.
        <details
          open={controlsOpen}
          className="group absolute top-2 left-2 max-w-[calc(100%-4rem)] rounded-sm border bg-popover/95 p-2 text-xs shadow-sm"
        >
          <summary className="flex cursor-pointer list-none items-center gap-1 rounded-sm font-medium text-muted-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-details-marker]:hidden">
            <Layers className="size-3.5" aria-hidden /> Layers &amp; legend
            <ChevronDown className="size-3.5 transition-transform group-open:rotate-180" aria-hidden />
          </summary>
          <fieldset className="mt-1.5">
            <legend className="sr-only">Map layers</legend>
            {layers.map((layer) => (
              <label key={layer.id} className="flex cursor-pointer items-center gap-2 py-0.5">
                <input
                  type="checkbox"
                  className="accent-primary"
                  checked={!hidden.has(layer.id)}
                  onChange={() => toggle(layer.id)}
                />
                {layer.label}
              </label>
            ))}
          </fieldset>

          {usedTones.length > 0 && (
            <ul aria-label="Legend" className="mt-2 flex flex-wrap gap-x-3 gap-y-1 border-t pt-2">
              {usedTones.map((t) => (
                <li key={t} className="flex items-center gap-1.5">
                  <span aria-hidden className={cn('flex size-4 items-center justify-center rounded-full text-[8px]', tones[t].className)}>
                    {tones[t].glyph}
                  </span>
                  {tones[t].label}
                </li>
              ))}
            </ul>
          )}
        </details>
      )}
    </div>
  )
}
