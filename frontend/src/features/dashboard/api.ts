// MOCK DATA PROVIDER — every value here is synthetic and tagged origin: 'synthetic'.
// Replace the bodies of these functions with API client calls when the backend is ready;
// hooks and components depend only on the types, not on this file's internals.

import type { CropSeries, DashboardContext, DashboardQuery, DashboardSummary, SeriesPoint } from './types'

const SOURCE = 'Prototype dataset (synthetic)'
const MONTH = 30 * 86_400_000

const hoursAgo = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString()

// Append ?mockError to the URL to see error states during development.
const shouldFail = () => new URLSearchParams(window.location.search).has('mockError')

function respond<T>(data: T, ms = 450): Promise<T> {
  return new Promise((resolve, reject) =>
    setTimeout(() => (shouldFail() ? reject(new Error('Mock failure')) : resolve(data)), ms),
  )
}

const context: DashboardContext = {
  regions: [{ id: 'mh', label: 'Maharashtra' }],
  farms: [
    { id: 'farm-niphad', label: 'Niphad plot A · 3.2 ha', regionId: 'mh', district: 'Nashik' },
    { id: 'farm-ausa', label: 'Ausa block 7 · 5.8 ha', regionId: 'mh', district: 'Latur' },
  ],
  seasons: [
    { id: 'kharif-2026', label: 'Kharif 2026' },
    { id: 'rabi-2026', label: 'Rabi 2026–27' },
  ],
}

const farms: Record<string, DashboardSummary['farm']> = {
  'farm-niphad': { id: 'farm-niphad', name: 'Niphad plot A', district: 'Nashik', lon: 74.11, lat: 20.08, areaHa: 3.2 },
  'farm-ausa': { id: 'farm-ausa', name: 'Ausa block 7', district: 'Latur', lon: 76.5, lat: 18.25, areaHa: 5.8 },
}

/** Deterministic seasonal curve so the mock looks plausible and stays stable between reloads. */
function seasonal(
  cropId: string,
  crop: string,
  s: { base: number; amp: number; peak: number },
  d: { base: number; amp: number; peak: number; growth: number },
): CropSeries {
  const now = new Date()
  const points: SeriesPoint[] = []
  for (let i = -18; i <= 6; i++) {
    const t = new Date(now.getFullYear(), now.getMonth() + i, 1)
    const m = t.getMonth()
    const wave = (peak: number) => Math.cos((2 * Math.PI * (m - peak)) / 12)
    points.push({
      period: t.toISOString(),
      supply: Math.round((s.base + s.amp * wave(s.peak)) * 10) / 10,
      demand: Math.round((d.base + d.amp * wave(d.peak) + d.growth * (i + 18)) * 10) / 10,
      kind: i <= 0 ? 'historical' : 'forecast',
    })
  }
  return { cropId, crop, unit: 'thousand tonnes / month', points, origin: 'synthetic', source: SOURCE, updatedAt: hoursAgo(5) }
}

export function getDashboardContext() {
  return respond(context, 200)
}

export function getDashboardSummary(q: DashboardQuery): Promise<DashboardSummary> {
  const farm = farms[q.farmId] ?? farms['farm-niphad']
  const signal = { origin: 'synthetic' as const, source: SOURCE }

  return respond({
    generatedAt: new Date().toISOString(),
    farm,
    signals: {
      supply: { ...signal, updatedAt: hoursAgo(5), label: 'Onion supply outlook', value: 1.42, unit: 'Mt next 90 days', trend: 'up', confidence: 0.71, risk: 'high', note: 'Late-kharif arrivals above 5-year median' },
      demand: { ...signal, updatedAt: hoursAgo(5), label: 'Onion demand outlook', value: 1.18, unit: 'Mt next 90 days', trend: 'flat', confidence: 0.64, note: 'Festival demand priced in; exports steady' },
      marketPressure: { ...signal, updatedAt: hoursAgo(2), label: 'Market pressure', value: -8.4, unit: '% modal price vs 30-day avg', trend: 'down', confidence: 0.58, risk: 'moderate', note: 'Lasalgaon arrivals rising faster than offtake' },
      weatherRisk: { ...signal, updatedAt: hoursAgo(1), label: 'Weather risk', value: 62, unit: 'mm rain, next 7 days', trend: 'up', confidence: 0.77, risk: 'moderate', note: 'Heavy spells forecast for north Maharashtra' },
      diseaseRisk: { ...signal, updatedAt: hoursAgo(9), label: 'Disease risk', value: 3, unit: 'active advisories', trend: 'up', confidence: 0.49, risk: 'high', note: 'Purple blotch conditions favourable (humid, 24–28 °C)' },
    },
    districts: [
      { id: 'nashik', name: 'Nashik', lon: 73.79, lat: 20.0, gap: 'surplus', gapKt: 214, confidence: 0.72 },
      { id: 'ahilyanagar', name: 'Ahilyanagar', lon: 74.74, lat: 19.09, gap: 'surplus', gapKt: 96, confidence: 0.66 },
      { id: 'pune', name: 'Pune', lon: 73.86, lat: 18.52, gap: 'shortage', gapKt: -88, confidence: 0.69 },
      { id: 'solapur', name: 'Solapur', lon: 75.91, lat: 17.66, gap: 'balanced', gapKt: 7, confidence: 0.55 },
      { id: 'latur', name: 'Latur', lon: 76.56, lat: 18.4, gap: 'balanced', gapKt: -4, confidence: 0.52 },
      { id: 'sambhajinagar', name: 'Chh. Sambhajinagar', lon: 75.34, lat: 19.88, gap: 'shortage', gapKt: -41, confidence: 0.61 },
      { id: 'jalgaon', name: 'Jalgaon', lon: 75.56, lat: 21.0, gap: 'balanced', gapKt: 12, confidence: 0.57 },
      { id: 'mumbai', name: 'Mumbai', lon: 72.88, lat: 19.08, gap: 'shortage', gapKt: -132, confidence: 0.74 },
      { id: 'nagpur', name: 'Nagpur', lon: 79.09, lat: 21.15, gap: 'shortage', gapKt: -37, confidence: 0.5 },
    ],
    series: [
      seasonal('onion', 'Onion', { base: 105, amp: 45, peak: 3 }, { base: 92, amp: 8, peak: 9, growth: 0.4 }),
      seasonal('soybean', 'Soybean', { base: 95, amp: 90, peak: 10 }, { base: 88, amp: 6, peak: 1, growth: 0.3 }),
      seasonal('tomato', 'Tomato', { base: 60, amp: 22, peak: 0 }, { base: 58, amp: 5, peak: 6, growth: 0.2 }),
    ],
    crops: [
      { cropId: 'onion', crop: 'Onion (rabi)', suitability: 'high', expectedYield: { value: 24, unit: 't/ha' }, waterNeed: 'medium', weatherRisk: 'moderate', supplyPressure: 'high', demandTrend: 'flat', projectedGap: 'surplus', confidence: 0.68 },
      { cropId: 'soybean', crop: 'Soybean', suitability: 'medium', expectedYield: { value: 1.9, unit: 't/ha' }, waterNeed: 'low', weatherRisk: 'moderate', supplyPressure: 'moderate', demandTrend: 'up', projectedGap: 'balanced', confidence: 0.61 },
      { cropId: 'tomato', crop: 'Tomato', suitability: 'medium', expectedYield: { value: 38, unit: 't/ha' }, waterNeed: 'high', weatherRisk: 'high', supplyPressure: 'low', demandTrend: 'up', projectedGap: 'shortage', confidence: 0.47 },
    ],
    alerts: [
      { id: 'a1', severity: 'high', category: 'supply', title: 'Projected onion oversupply', detail: 'Nashik arrivals forecast 18% above demand over the next 6 weeks.', district: 'Nashik', raisedAt: hoursAgo(3) },
      { id: 'a2', severity: 'moderate', category: 'weather', title: 'Heavy rainfall window', detail: '40–70 mm expected Thu–Sat; field drainage and harvest timing at risk.', district: 'Nashik', raisedAt: hoursAgo(6) },
      { id: 'a3', severity: 'high', category: 'disease', title: 'Purple blotch conditions', detail: 'Humidity and temperature favourable for 5+ days in onion belts.', district: 'Ahilyanagar', raisedAt: hoursAgo(9) },
      { id: 'a4', severity: 'low', category: 'data', title: 'Mandi feed delayed', detail: 'Two APMC arrival reports not yet received today.', district: 'Solapur', raisedAt: hoursAgo(14) },
    ],
    sources: [
      { id: 's1', name: 'Mandi arrivals & prices', availability: 'delayed', updatedAt: hoursAgo(14), origin: 'synthetic' },
      { id: 's2', name: 'Weather forecast', availability: 'healthy', updatedAt: hoursAgo(1), origin: 'synthetic' },
      { id: 's3', name: 'Satellite vegetation index', availability: 'healthy', updatedAt: hoursAgo(30), origin: 'synthetic' },
      { id: 's4', name: 'Soil health cards', availability: 'degraded', updatedAt: new Date(Date.now() - 4 * MONTH).toISOString(), origin: 'synthetic' },
    ],
  })
}
