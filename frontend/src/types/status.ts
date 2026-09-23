// Shared product-state vocabulary. Keep these names identical across the app (CLAUDE.md §23).

export type RiskLevel = 'low' | 'moderate' | 'high' | 'critical'

/** Where a value comes from. Mock values are always 'synthetic'. */
export type DataOrigin = 'observed' | 'forecast' | 'model' | 'estimate' | 'synthetic'

export type Availability = 'healthy' | 'delayed' | 'degraded' | 'unavailable'

export type GapState = 'surplus' | 'balanced' | 'shortage'

export type Trend = 'up' | 'flat' | 'down'

/** Model confidence in [0, 1]. Supplied by the backend, never computed in the UI. */
export type Confidence = number
