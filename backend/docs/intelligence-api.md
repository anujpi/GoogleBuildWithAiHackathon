# Intelligence API contract

Every call goes Frontend → Spring Boot. The frontend never calls the ML service.
All endpoints need `Authorization: Bearer <token>` (401 `UNAUTHORIZED` without it; see `auth-api.md`).
Errors use the standard body: `{ timestamp, status, code, message, path, details[] }`.

## Status per endpoint

| Endpoint | Status | Behaviour today |
|---|---|---|
| `GET /api/weather`, `GET /api/weather/farms/{farmId}` | NO SOURCE | 503 `WEATHER_UNAVAILABLE` after validation and the ownership check. No weather provider is configured, and the backend never generates weather. |
| `GET /api/intelligence/supply-forecast` | WAITING_FOR_ML | Calls ML `POST /v1/predict/supply`. 200 when ML answers; 503/502 otherwise. Request body to ML is provisional. |
| `GET /api/intelligence/demand-forecast` | CONTRACT_ONLY / UNAVAILABLE | 503 `PREDICTION_UNAVAILABLE` after validation. No ML endpoint agreed. |
| `GET /api/intelligence/supply-demand` | CONTRACT_ONLY / UNAVAILABLE | 503 `PREDICTION_UNAVAILABLE`. No historical production/arrival data in the backend. |
| `GET /api/intelligence/crop-recommendations` | CONTRACT_ONLY / UNAVAILABLE | Farm ownership checked, then 503 `PREDICTION_UNAVAILABLE`. |
| `GET /api/intelligence/agricultural-risk` | CONTRACT_ONLY / UNAVAILABLE | 503 `PREDICTION_UNAVAILABLE`. |

`PREDICTION_UNAVAILABLE` means "no real source is connected". The frontend must show an unavailable state, never a placeholder number.

## Shared conventions

**Ids.** `regionId`, `cropId`: lower-case slug, regex `[a-z0-9][a-z0-9-]{0,49}` (e.g. `ka`, `tomato`). The backend has no region/crop reference table yet, so any well-formed id is accepted; unknown ids are only detected by the ML service.

**`horizonMonths`**: integer 1–12, default 3. Replaces the frontend's `periodId` (`next-3m` → `3`).

**Provenance** (`IntelligenceProvenance`), on every intelligence result:

| Field | Type | Meaning |
|---|---|---|
| `source` | string | Producer, e.g. `ML_SERVICE` |
| `dataClassification` | `OBSERVED` \| `FORECAST` \| `MODEL_PREDICTION` \| `REGIONAL_ESTIMATE` \| `SYNTHETIC` | `MODEL_PREDICTION` only when a model produced the value |
| `generatedAt` | ISO-8601 UTC | When the backend produced this response |
| `modelVersion` | string \| null | null when no model was involved |
| `confidence` | number 0..1 \| null | null unless the source reports it. Never invented. |
| `limitations` | string[] | Human-readable caveats; may be empty |

Weather predates this and keeps its own `provenance { source, dataClassification, retrievedAt, confidence }`.

## Common errors (intelligence endpoints)

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing/blank/ill-formed parameter; `details[].field` names it |
| 400 | `INVALID_PARAMETER` | Wrong type, e.g. `farmId` not a UUID |
| 401 | `UNAUTHORIZED` | No/invalid token |
| 404 | `FARM_NOT_FOUND` | Farm missing **or owned by another user** (indistinguishable on purpose) |
| 502 | `ML_SERVICE_ERROR` | ML answered 4xx/5xx, or with a malformed/incomplete body |
| 503 | `ML_SERVICE_UNAVAILABLE` | ML unreachable or timed out (connect 2s, read 10s by default) |
| 503 | `PREDICTION_UNAVAILABLE` | Endpoint has no prediction source connected yet |

---

## GET /api/weather — NO SOURCE (503 today)

Query: `latitude` (-90..90, required), `longitude` (-180..180, required), `days` (1..14, default 7).
`GET /api/weather/farms/{farmId}?days=` uses the farm's stored location (404 `FARM_NOT_FOUND` for others' farms, checked before availability).

**Today:** after validation, both endpoints answer:

```json
{ "status": 503, "code": "WEATHER_UNAVAILABLE", "message": "No weather data source is configured", "path": "/api/weather", "details": [] }
```

The response carries no `current` or `daily` values: the backend never generates weather. When a real provider is connected but fails, or returns data classified `SYNTHETIC`, the answer is the same 503 with the message "Weather data is currently unavailable".

**Once a real provider exists**, the 200 body will have this shape. The values below illustrate the shape only:

```json
{
  "farmId": null,
  "latitude": 12.72, "longitude": 77.28,
  "provenance": { "source": "<PROVIDER_NAME>", "dataClassification": "OBSERVED | FORECAST",
                  "retrievedAt": "2026-09-28T10:00:00Z", "confidence": null },
  "current": { "observedAt": "...", "temperatureC": 27.3, "relativeHumidityPct": 61.0,
               "rainfallMm": 0.0, "windSpeedKmh": 11.2 },
  "daily": [ { "date": "2026-09-28", "minTemperatureC": 19.4, "maxTemperatureC": 29.8,
               "rainfallMm": 0.0, "rainProbabilityPct": 22.5, "relativeHumidityPct": 58.1 } ]
}
```
Extra error: 503 `WEATHER_UNAVAILABLE` if the provider fails.

## GET /api/intelligence/supply-forecast — WAITING_FOR_ML

Query: `regionId`, `cropId` (required), `horizonMonths` (default 3).

200 `SupplyForecastResponse`:
```json
{
  "regionId": "ka", "cropId": "tomato", "horizonMonths": 6,
  "predictedSupply": 812.4,
  "unit": "tonnes",
  "forecastPeriod": "2026-10/2027-03",
  "provenance": { "source": "ML_SERVICE", "dataClassification": "MODEL_PREDICTION",
                  "generatedAt": "...", "modelVersion": "supply-v0.1", "confidence": null,
                  "limitations": ["The model reports no uncertainty, so confidence is not available."] },
  "modelProvenance": { "datasetVersion": "ds-1", "featureVersion": "f-1", "trainedAt": "2026-09-20T10:00:00Z" }
}
```
- `unit` and `forecastPeriod` come from the model unchanged; `forecastPeriod` may be null.
- `modelProvenance` is null when the ML service sends none; `trainedAt` is the model's raw string.

Backend → ML request (provisional, until the ML contract is final):
`POST {ML_SERVICE_BASE_URL}/v1/predict/supply` body `{ "regionId": "...", "cropId": "...", "horizonMonths": 6 }`.
Required in the ML response: `modelVersion`, `prediction.value`, `prediction.unit`; otherwise 502.

## GET /api/intelligence/demand-forecast — CONTRACT_ONLY

Query: as supply-forecast. Today: 503 `PREDICTION_UNAVAILABLE`.

Planned 200 body (not served yet): same shape as supply-forecast with `predictedDemand` instead of `predictedSupply`, plus `signalType` (e.g. `MANDI_ARRIVALS`). Demand is a proxy signal, never retail sales.

## GET /api/intelligence/supply-demand — CONTRACT_ONLY

Query: as supply-forecast. Today: 503 `PREDICTION_UNAVAILABLE`.

Planned 200 body (not served yet):
```jsonc
{
  "regionId": "ka", "cropId": "onion", "horizonMonths": 3, "unit": "tonnes",
  "historical": [ { "month": "2026-06", "supply": 0, "demand": 0 } ],  // OBSERVED, own provenance
  "forecast":   [ { "month": "2026-10", "supply": 0, "demand": 0 } ],  // MODEL_PREDICTION
  "gap": { "supplyDemandGap": 0, "gapState": "SURPLUS|BALANCED|SHORTAGE" }, // gap = supply − demand
  "historicalProvenance": { ... }, "forecastProvenance": { ... }
}
```
Every array may be empty; a series is omitted rather than filled with invented values.

## GET /api/intelligence/crop-recommendations — CONTRACT_ONLY

Query: `farmId` (UUID, required). Ownership is enforced first (404 for another user's farm), then 503 `PREDICTION_UNAVAILABLE`.

Planned 200 body (not served yet): `{ farmId, candidates: [ { cropId, suitability, factors: [ { name, value, effect } ] } ], provenance }`. Facts per crop, no single winner; the decision engine owns the recommendation.

## GET /api/intelligence/agricultural-risk — CONTRACT_ONLY

Query: `regionId`, `cropId` (required). Today: 503 `PREDICTION_UNAVAILABLE`.

Planned 200 body (not served yet): `{ regionId, cropId, risks: [ { category: WEATHER|CROP|SUPPLY|MARKET|ANOMALY, level, summary, factors[] } ], provenance }`. Production and market risks stay separate.

---

## Frontend (`frontend/src/features/intelligence/`) vs this contract

| Topic | Frontend today | Backend | Action for frontend |
|---|---|---|---|
| Paths | `/intelligence/supply`, `/demand`, `/risk` | `/supply-forecast`, `/demand-forecast`, `/agricultural-risk` | Rename paths |
| Period param | `periodId=next-3m` | `horizonMonths=3` | Map option → integer |
| Supply field names | `crop`, `forecastPeriod`, `predictedSupply`, `modelVersion` top-level | `cropId`, `forecastPeriod`, `predictedSupply`, `provenance.modelVersion` | Read from provenance |
| Provenance | `retrievedAt` | `generatedAt`, plus `limitations` | Rename; weather keeps `retrievedAt` |
| Supply-demand | full body with `summary`, `risks`, `gapTrend`, `gapPercentage`, labels | 503 today; planned body smaller (no labels, no embedded risks, no trend) | Keep mock; handle 503 as unavailable |
| Crop recs | `expectedYield`, `confidence` per candidate | `factors`, no invented yield/confidence | Adjust when served |
| Unavailable | mock mode rejects with 501 `NOT_AVAILABLE` | 503 `PREDICTION_UNAVAILABLE` | Treat both as unavailable |
| Weather | shape matches; no page uses it yet | 503 `WEATHER_UNAVAILABLE` until a real provider exists | When a weather UI is built, show 503 as "unavailable". `describeError` currently gives the generic 5xx text for it. |
