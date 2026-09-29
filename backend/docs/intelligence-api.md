# Intelligence API contract

Every call goes Frontend → Spring Boot. The frontend never calls the ML service.
All endpoints need `Authorization: Bearer <token>` (401 `UNAUTHORIZED` without it; see `auth-api.md`).
Errors use the standard body: `{ timestamp, status, code, message, path, details[] }`.

## Status per endpoint

The canonical contract is `MASTER_SPEC.md` (§6, §8, §13). This file describes what the code does today.

| Endpoint | Status | Behaviour today |
|---|---|---|
| `GET /api/weather`, `GET /api/weather/farms/{farmId}` | NO SOURCE | 503 `WEATHER_UNAVAILABLE` after validation and the ownership check. The Open-Meteo provider and split provenance (§7) are phase P3 |
| `GET /api/intelligence/supply` | ML-BACKED (§8) | District supply estimate from ML `POST /v1/predict/supply` |
| `/api/intelligence/{supply-forecast, demand-forecast, supply-demand, crop-recommendations, agricultural-risk}` | **REMOVED** (§6.9) | 404 |
| `GET /api/reference/scope` | NOT YET (P2) | Served from synced reference tables (V4), never proxied live |

## Shared conventions

**Ids** (D3): lower-case slugs matching `[a-z0-9][a-z0-9-]{0,49}`: `districtId` like `up-agra`, `cropId` like
`potato`. The supported ones are the ML scope (`GET /v1/reference/scope`).

**Provenance** (`common/api/Provenance`, §12): `{ source, dataClassification, retrievedAt, generatedAt,
datasetVersion, modelName, modelVersion, featureVersion, dataThrough, notes[] }`. Unknown values are null.
There is no `confidence` field. Weather still uses its own single `provenance` until P3.

**Classification** (D4): `OBSERVED, FORECAST, MODEL_PREDICTION, ESTIMATED, SYNTHETIC`.

## Errors used by these endpoints (§13)

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing, blank or ill-formed parameter; `details[].field` names it |
| 401 | `UNAUTHORIZED` | No or invalid token |
| 404 | `FARM_NOT_FOUND` | Farm missing **or owned by another user** (indistinguishable on purpose) |
| 422 | `UNSUPPORTED_INPUT` | Outside the scope: unknown district or crop, no series for the season, crop year not estimable. `details[].field` names it |
| 422 | `INSUFFICIENT_DATA` | ML `INSUFFICIENT_HISTORY` / `AREA_UNAVAILABLE` |
| 502 | `ML_INVALID_RESPONSE` | ML returned bad or contract-breaking data: missing fields, classification not matching `servedMethod`, history not OBSERVED, wrong unit, negative production, value outside its interval, or an ML `VALIDATION_ERROR`/`INTERNAL_ERROR` |
| 503 | `ML_UNAVAILABLE` | ML unreachable or timed out (connect 2s, read 10s by default) |
| 503 | `ML_PREDICTION_UNAVAILABLE` | ML up but no model loaded (`MODEL_NOT_LOADED` / `ARTIFACT_LOAD_ERROR`) |
| 503 | `WEATHER_UNAVAILABLE` | Weather only, until P3 replaces it with the `UPSTREAM_*` codes |

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

## GET /api/intelligence/supply — ML-BACKED (MASTER_SPEC §8)

This is the estimated **reported production** of one district × crop × season for one crop year.
**It is not a current-season forecast.** S01 ends in crop year 2014, so the newest estimable year is 2015.

**Query parameters:**
- `districtId`, `cropId`: required
- `season`: required, one of `KHARIF, RABI, SUMMER, WHOLE_YEAR, AUTUMN, WINTER`
- `cropYear`: required, 1950–2100
- `areaHectares`: optional, > 0

**Flow:**
1. Bean validation.
2. Scope pre-check: the district, the crop and the (district, crop, season) series must exist, and
   `cropYear` must be in `firstYear+1 … lastYear+1`. Otherwise 422 `UNSUPPORTED_INPUT`, and the
   **predictor is not called**. INTERIM: the scope is read live from ML until the V4 reference sync (P2) exists.
3. `POST /v1/predict/supply {districtId, cropId, season, cropYear, areaHectares|null}`.
4. `MlClient` checks the contract rules, and the result is mapped to the body below.

**200 `SupplyEstimateResponse`** (the shape only):

```jsonc
{
  "target":   { "districtId": "up-agra", "districtLabel": "Agra", "cropId": "potato", "cropLabel": "Potato",
                "season": "RABI", "cropYear": 2015 },
  "estimate": { "production": { "value": 0, "unit": "TONNES", "interval": { "lower": 0, "upper": 0,
                  "nominalCoverage": 0.8, "empiricalCoverage": 0.0, "method": "EMPIRICAL_LOG_RESIDUAL_QUANTILES_VALIDATION" } },
                "yield": { "value": 0, "unit": "TONNES_PER_HECTARE", "interval": { } },
                "area": { "value": 0, "unit": "HECTARES", "areaSource": "REQUEST|REPORTED|LAST_REPORTED" },
                "servedMethod": "MODEL|BASELINE", "historyYearsUsed": [2012, 2013, 2014] },
  "baseline": { "method": "AREA_X_MEAN_YIELD_3Y", "production": { "value": 0, "unit": "TONNES" } },
  "reported": null,                  // the S01 actuals when cropYear is a reported (backtest) year
  "history":  { "units": { "area": "HECTARES", "production": "TONNES", "yield": "TONNES_PER_HECTARE" },
                "points": [ { "cropYear": 2014, "area": 0, "production": 0, "yield": 0 } ] },
  "historicalYieldStats": { "yearsObserved": 18, "meanYield": { "value": 0, "unit": "TONNES_PER_HECTARE" },
                            "coefficientOfVariation": 0.1, "downsideYearShare": 0.1, "yearsAssessedForDownside": 17,
                            "downsideDefinition": "YIELD_BELOW_85PCT_OF_TRAILING_3Y_MEAN" },
  "modelEvaluation": { "trainingPeriod": "1998-2010", "validationPeriod": "2011-2012", "testPeriod": "2013-2014",
                       "servedMethod": "MODEL", "testWape": 0.0, "bestBaseline": "AREA_X_MEAN_YIELD_3Y",
                       "bestBaselineTestWape": 0.0, "testIntervalCoverage": 0.0 },
  "provenance": { "source": "ML_SERVICE", "dataClassification": "MODEL_PREDICTION|ESTIMATED", "retrievedAt": "…Z",
                  "generatedAt": "…Z", "datasetVersion": "s01-…", "modelName": "…", "modelVersion": "…",
                  "featureVersion": "supply-features-v2", "dataThrough": "2014", "notes": [] },
  "historyProvenance": { "source": "DES_S01_DATA_GOV_IN", "dataClassification": "OBSERVED", "datasetVersion": "s01-…",
                         "dataThrough": "2014", "retrievedAt": null, "generatedAt": null, "modelName": null,
                         "modelVersion": null, "featureVersion": null, "notes": [] },
  "limitations": [ "…ML sentences verbatim…",
                   "Data ends in crop year 2014; this is not a current-season forecast.",
                   "The trained model did not beat the baseline; the baseline estimate is served." ]
}
```

- `dataClassification` is `MODEL_PREDICTION` when `servedMethod=MODEL` and `ESTIMATED` when `BASELINE`.
- The baseline sentence appears only when `BASELINE` is served.
- There is **no `confidence` field**: the interval is the uncertainty.

---

## Frontend (`frontend/src/features/intelligence/`) vs this contract

The final frontend contract is `MASTER_SPEC.md` §15 (phase P5). These gaps are live today:

| Topic | Frontend today | Backend today |
|---|---|---|
| Supply | `getSupplyForecast` → removed `/intelligence/supply-forecast` (`regionId`, `horizonMonths`); no page uses it | `/intelligence/supply?districtId&cropId&season&cropYear[&areaHectares]` → `SupplyEstimateResponse` |
| Demand, supply-demand, risk, crop recommendations clients | Call removed paths; the Supply & Demand page uses a SYNTHETIC mock by default | 404 (removed, §6.9). Crop evidence and risk arrive as `/api/farms/{id}/…` in P3 |
| `DataClassification` | Includes `REGIONAL_ESTIMATE` | `ESTIMATED` (D4). The frontend must rename it |
| Error codes | `describeError` has no text for the §13 codes | `UNSUPPORTED_INPUT`, `INSUFFICIENT_DATA`, `ML_UNAVAILABLE`, `ML_PREDICTION_UNAVAILABLE`, `ML_INVALID_RESPONSE` |
