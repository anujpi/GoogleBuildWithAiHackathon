import {
  ArrowLeftRight,
  Bell,
  ChartCandlestick,
  ChartScatter,
  Database,
  LayoutGrid,
  MapPinned,
  Network,
  ScanSearch,
  SlidersHorizontal,
  type LucideIcon,
} from 'lucide-react'

export type NavItem = {
  path: string
  label: string
  icon: LucideIcon
  /** One line shown on the module page until the module is built. */
  summary: string
  /** Build phase from CLAUDE.md §29. */
  phase: number
  /** The module still renders mock data, so the top bar flags it as synthetic. */
  synthetic?: boolean
}

export type NavGroup = { label: string; items: NavItem[] }

// Adding a module = adding an entry here. The sidebar and router both read this list.
export const navigation: NavGroup[] = [
  {
    label: 'Intelligence',
    items: [
      { path: '/dashboard', label: 'Dashboard', icon: LayoutGrid, phase: 0, synthetic: true, summary: 'What is happening right now across the selected region and farm.' },
      { path: '/farms', label: 'Farms', icon: MapPinned, phase: 1, summary: 'Farm onboarding, location, soil profile, irrigation and crop history.' },
      { path: '/crops', label: 'Crop Intelligence', icon: ChartScatter, phase: 4, summary: 'Compare candidate crops across suitability, yield, water, risk and market gap.' },
      { path: '/supply-demand', label: 'Supply & Demand', icon: ArrowLeftRight, phase: 7, summary: 'Historical and forecast supply against demand, with projected gaps by region.' },
      { path: '/market', label: 'Market Intelligence', icon: ChartCandlestick, phase: 7, summary: 'Mandi price trends, arrivals, seasonality and anomalous market signals.' },
    ],
  },
  {
    label: 'Analysis',
    items: [
      { path: '/scenarios', label: 'Scenario Simulator', icon: SlidersHorizontal, phase: 11, summary: 'Change rainfall, temperature, supply or demand and compare against baseline.' },
      { path: '/crop-doctor', label: 'Crop Doctor', icon: ScanSearch, phase: 12, summary: 'Diagnose crop images with weather, crop and environmental context.' },
      { path: '/coordination', label: 'Regional Coordination', icon: Network, phase: 13, summary: 'Match surplus regions with shortage regions using quantity, distance and evidence.' },
    ],
  },
  {
    label: 'Operations',
    items: [
      { path: '/alerts', label: 'Alerts', icon: Bell, phase: 8, summary: 'Supply, weather, disease, arrival, pricing and data-source alerts.' },
      { path: '/data-operations', label: 'Data Operations', icon: Database, phase: 14, summary: 'Source health, ingestion status, model versions and prediction runs.' },
    ],
  },
]
