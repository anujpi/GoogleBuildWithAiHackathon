import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
// MapLibre resolves its worker relative to its own module URL, which breaks once Vite pre-bundles it.
// Letting Vite bundle the worker and handing MapLibre the URL works in dev and build.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'

maplibregl.setWorkerUrl(workerUrl)

/** Free OpenStreetMap-based vector basemap; no API key. */
export const BASEMAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/positron'

export const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

export { maplibregl }
