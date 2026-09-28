# Frontend intelligence API layer

State as of 2026-09-28. This document describes the frontend only.

Backend facts below were read from `backend/src`. `/api/weather` is committed. `/api/intelligence/*` (IntelligenceController, IntelligenceProvenance, SupplyForecastResponse) is **uncommitted work in progress in the backend working tree**, so re-check it once it is merged.

## 1. Architecture

```
component → feature hook (TanStack Query) → feature api.ts → lib/api/client.ts `api()` → Spring Boot (VITE_API_BASE_URL)
```

- **One HTTP client:** `lib/api/client.ts`.
  - It attaches the bearer token and ends the session on a 401.
  - Every failure becomes an `ApiError {status, code, details}`.
  - `describeError()` is the only source of user-facing error text.
- **No direct ML calls:** the frontend never calls the ML service. FastAPI is reached only through Spring Boot.
- **Source switch:** `VITE_INTELLIGENCE_SOURCE` (read once in `features/intelligence/shared/http.ts`). It affects **supply-demand only**, the one endpoint with a frontend mock.
  - `api`: supply-demand comes from Spring Boot.
  - Anything else, including the default `mock`: supply-demand comes from the frontend mock, classified `SYNTHETIC`.
  - Every other endpoint always calls the backend. Until a prediction source is connected, the backend answers `503 PREDICTION_UNAVAILABLE`, and that message is shown as-is.
  - A failed real request is **never** replaced by mock data.
- **Response validation:** responses with an agreed shape are checked with zod (`validated()` in `shared/http.ts`).
  - A 204 or empty body returns `null`, and the UI shows its empty state.
  - A body that doesn't match returns `ApiError 502 MALFORMED_RESPONSE`, and the UI shows its error state. No chart is drawn from a bad body.

```
src/features/intelligence/
├── shared/              types.ts (DataClassification, Provenance, originOf) · http.ts (source switch, validation, qs)
├── supply-demand/       types · api · hooks · options · mock · series · SupplyDemandLegend
├── weather/             types · api · hooks
├── crop-recommendation/ types · api · hooks
└── agricultural-risk/   types · api · hooks
```

Pages stay in their own feature folders (`features/supply-demand/`, `features/dashboard/`) and import these modules. There is no market module: nothing consumes one yet, and no backend contract exists.

### Response convention

There is no `IntelligenceResponse<T>` wrapper, because the backend doesn't use one. Its responses are flat records that carry a `provenance` object:

```ts
// /api/intelligence/* — backend IntelligenceProvenance
IntelligenceProvenance = { source; dataClassification; generatedAt; modelVersion: string | null; confidence: number | null; limitations: string[] }
// /api/weather — backend WeatherResponse.Provenance
Provenance = { source; dataClassification; retrievedAt; confidence: number | null }
```

`modelVersion` and `confidence` are `null` when the source provides none. The frontend never fills them in. `limitations` is shown in the provenance panel.

## 2. Classification → UI

- `DataClassification` mirrors the backend enum: `OBSERVED`, `FORECAST`, `MODEL_PREDICTION`, `REGIONAL_ESTIMATE`, `SYNTHETIC`.
- `originOf()` is the only mapping onto UI badges: Observed, Forecast, Model prediction, Estimate, Synthetic.
- Badges, the "Prototype data" notice and chart captions all derive from the classification in the response. No component hard-codes one.
- **Exception, the top-bar "Synthetic data" flag:** it comes from `app/navigation.ts`.
  - Dashboard: always on, because its summary has no backend endpoint.
  - Supply & Demand: on only when `VITE_INTELLIGENCE_SOURCE` is not `api`.

## 3. Endpoints

All intelligence forecast endpoints take `regionId`, `cropId` (lower-case slugs) and `horizonMonths` (1–12, default 3).

| Function | Path (relative to `/api`) | Backend status | Response body | Validated | Mock |
|---|---|---|---|---|---|
| `getWeather` | `GET /weather?latitude&longitude&days`, `GET /weather/farms/{farmId}?days` | Committed. The provider labels its data SYNTHETIC. | **Backend** (`WeatherResponse`) | yes | none, always real |
| `getSupplyForecast` | `GET /intelligence/supply-forecast` | In progress. Calls the ML service. | **Backend** (`SupplyForecastResponse`) | yes | none, always real |
| `getSupplyDemandForecast` | `GET /intelligence/supply-demand` | In progress. Returns 503. | **Provisional**, frontend draft | yes (draft) | yes, SYNTHETIC |
| `getDemandForecast` | `GET /intelligence/demand-forecast` | In progress. Returns 503. | **Provisional**, frontend draft | no | none, always real |
| `getCropRecommendation` | `GET /intelligence/crop-recommendations?farmId` | In progress. Returns 503. | **Provisional**, frontend draft | no | none, always real |
| `getAgriculturalRisk` | `GET /intelligence/agricultural-risk?regionId&cropId` | In progress. Returns 503. | **Provisional**, frontend draft | no | none, always real |

Paths and query parameters follow the backend controller. Where the backend has no response body yet, the frontend type is a draft and is marked PROVISIONAL in its module.

## 4. Connected pages

| Page | Data |
|---|---|
| `/supply-demand` | `useSupplyDemandForecast` |
| `/dashboard`, "Supply vs demand" chart | Same hook, so the cache is shared with `/supply-demand`. |
| `/dashboard`, everything else (signal strip, district map, alerts, data freshness, crop preview) | `features/dashboard/api.ts`, a dashboard-only mock with no endpoint. Every value is classified SYNTHETIC and badged as such. |
| `/farms`, `/farms/new`, `/farms/:id`, `/farms/:id/edit` | Real backend (`features/farms/api.ts`). |

Weather, supply-only, demand-only, crop recommendation and risk have hooks but no UI yet.

## 5. Synthetic data

- `features/intelligence/supply-demand/mock.ts`: supply-demand series in mock mode.
- `features/dashboard/api.ts`: the whole dashboard summary.
- Weather is **not** a synthetic source. The backend's mock weather provider was removed, so `/api/weather` answers `503 WEATHER_UNAVAILABLE` until a real provider exists. Show an unavailable state and never fall back to mock weather.

## 6. Error handling

| Case | Result |
|---|---|
| Network failure | "Could not reach the server…" + Retry |
| 401 on an authenticated request | Session cleared, redirect to `/login` |
| 403 / 404 / 400 / 422 / 5xx | `describeError()` message |
| Malformed body | "The server sent … data in an unexpected format." + Retry |
| 204 or empty series | Empty state |
| 503 `PREDICTION_UNAVAILABLE` | The backend's message, e.g. "Supply and demand is not available yet: no prediction source is connected." + Retry |

Retries use `shouldRetry` (set once on the QueryClient): 4xx responses are never retried.

## 7. Backend contracts still required

1. **Supply-demand response body** for `GET /api/intelligence/supply-demand`. The frontend draft is in `features/intelligence/supply-demand/types.ts`. Key points:
   - history and forecast are classified separately (`historicalClassification` and `provenance.dataClassification`)
   - units are stated in the response
   - confidence can be null

   This is the only one a page depends on. Once the backend returns the agreed body, set `VITE_INTELLIGENCE_SOURCE=api`.
2. Response bodies for demand-forecast, crop-recommendations and agricultural-risk. Drafts are in their modules. Add zod schemas once they are agreed.
3. Reference data (regions, crops, horizons). The frontend uses a static list in `supply-demand/options.ts`, and its ids must match the backend's slug rules.
4. A dashboard summary endpoint, if the dashboard's signal strip, map, alerts and sources should become real.
5. Market intelligence: no contract, no frontend module.
