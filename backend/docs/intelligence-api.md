# Intelligence API contract

This covers reference scope, weather, supply, crop evidence and risk. The canonical source is `MASTER_SPEC.md` (§6–§13); this file describes what the code implements.

- **Routing:** every call goes Frontend → Spring Boot. The frontend never calls the ML service or Open-Meteo.
- **Auth:** every endpoint needs `Authorization: Bearer <token>`; without it the answer is 401 `UNAUTHORIZED` (see `auth-api.md`). The roles allowed on each endpoint are listed in §4.2 of the spec.
- **Errors:** always `{ timestamp, status, code, message, path, details[{field, message}] }`.
- **Correlation id:** every response, including every error, carries an `X-Request-Id` header. See the end of this file.

## Endpoints

| Endpoint | Roles | Source |
|---|---|---|
| `GET /api/reference/scope` | all | Synced reference tables (never proxied live) |
| `GET /api/weather?latitude&longitude&days` | all | Open-Meteo (real, no API key) |
| `GET /api/farms/{id}/weather?days` | owner (FARMER/FPO) | Open-Meteo at the farm's stored location |
| `GET /api/intelligence/supply?districtId&cropId&season&cropYear[&areaHectares]` | all | ML service |
| `GET /api/farms/{id}/crop-evidence?season&cropYear` | owner (FARMER/FPO) | Backend rules over soil, weather and ML |
| `GET /api/farms/{id}/risk?cropId&season` | owner (FARMER/FPO) | Backend rules over soil, weather and ML |
| `GET /api/regional/farms/{id}/{weather,crop-evidence,risk}` | FPO/OFFICER (assigned districts), ADMIN | Same bodies as the owner endpoints (see `admin-api.md`) |

These endpoints are **removed** and return 404:
- `/api/intelligence/{supply-forecast, demand-forecast, supply-demand, crop-recommendations, agricultural-risk}`
- `/api/weather/farms/{farmId}`

## Shared conventions

**Ids** (D3). All ids are lower-case slugs matching `[a-z0-9][a-z0-9-]{0,49}`:
- `stateId`, e.g. `up`
- `districtId`, e.g. `up-agra`
- `cropId`, e.g. `potato`

The supported set is whatever `GET /api/reference/scope` returns.

**Seasons.** The canonical seasons are `KHARIF, RABI, SUMMER, WHOLE_YEAR, AUTUMN, WINTER`. A farm's own season maps to them like this:

| Farm season | Canonical season |
|---|---|
| `KHARIF` | `KHARIF` |
| `RABI` | `RABI` |
| `ZAID` | `SUMMER` |
| `OTHER` | none; pass `season` explicitly, otherwise 422 |

**Provenance** (§12). Every provenance object has this shape:

```
{ source, dataClassification, retrievedAt, generatedAt, datasetVersion, modelName, modelVersion, featureVersion, dataThrough, notes[] }
```

Fields that don't apply are `null`, never invented. The object has no `confidence` field.

**Classification** (D4): `OBSERVED`, `FORECAST`, `MODEL_PREDICTION`, `ESTIMATED` or `SYNTHETIC`. The backend never generates `SYNTHETIC` data.

**Risk levels:** `LOW`, `MODERATE`, `HIGH`, `CRITICAL` or `UNAVAILABLE`.

## Errors used by these endpoints (§13)

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | A missing, out-of-range or ill-formed parameter. `details[].field` names it |
| 400 | `INVALID_PARAMETER` | Wrong type, e.g. a farm id that isn't a UUID |
| 401 | `UNAUTHORIZED` | No token, or an invalid token |
| 403 | `FORBIDDEN` | The role may not use the endpoint, e.g. an officer on `/api/farms/**` |
| 404 | `FARM_NOT_FOUND` | The farm doesn't exist **or isn't visible** to the caller. The two cases are indistinguishable on purpose |
| 422 | `UNSUPPORTED_INPUT` | Outside the scope. `details[].field` names the cause: an unknown district or crop, no series for the season, a crop year that can't be estimated, a farm without an in-scope district, or a farm season of `OTHER` |
| 422 | `INSUFFICIENT_DATA` | ML answered `INSUFFICIENT_HISTORY` or `AREA_UNAVAILABLE` |
| 502 | `ML_INVALID_RESPONSE` | ML returned malformed or contract-breaking data |
| 502 | `UPSTREAM_INVALID_RESPONSE` | Open-Meteo returned bad data: a non-2xx status other than 429, malformed JSON, missing blocks, misaligned arrays, NaN, or data classified `SYNTHETIC` |
| 503 | `ML_UNAVAILABLE` | ML unreachable or timed out (connect 2s, read 10s) |
| 503 | `ML_PREDICTION_UNAVAILABLE` | ML is up but has no model loaded. **This is the state until blocker B1 is resolved** |
| 503 | `UPSTREAM_UNAVAILABLE` | Open-Meteo unreachable or timed out (connect 2s, read 5s) |
| 503 | `UPSTREAM_RATE_LIMITED` | Open-Meteo answered 429 |

An error body never contains any weather or prediction values.

---

## GET /api/reference/scope

```jsonc
{ "states": [{ "stateId": "up", "label": "Uttar Pradesh" }],
  "districts": [{ "districtId": "up-agra", "stateId": "up", "label": "Agra" }],
  "crops": [{ "cropId": "potato", "label": "Potato" }],
  "seasons": ["RABI"],
  "supplySeries": [{ "districtId": "up-agra", "cropId": "potato", "season": "RABI", "firstYear": 1997,
                     "lastYear": 2014, "yearsObserved": 18, "estimableYears": [1998, 1999, "…", 2015] }],
  "datasets": [{ "source": "DES_S01_DATA_GOV_IN", "datasetVersion": "s01-…", "dataThrough": "2014" }],
  "syncedAt": "2026-09-29T…Z" }
```

**Before the first successful sync** every list is empty and `syncedAt` is `null`. The frontend shows "Reference data not loaded yet". This is also the state while the ML service has no artifact (B1).

**`estimableYears`** runs from `firstYear+1` to `lastYear+1`. These are candidate years only: gaps in the history can still produce 422 `INSUFFICIENT_DATA`.

**How the data gets here:**
- The sync runs at startup when `REFERENCE_SYNC_ON_STARTUP=true`, and on demand via `POST /api/admin/reference/sync`.
- ML is the authority for the list. States, districts and crops are only ever added or updated. Supply series and datasets are replaced on each sync.
- A failed sync changes nothing except the sync history.

---

## GET /api/weather and GET /api/farms/{id}/weather

**Parameters:**
- `latitude`: -90..90, required.
- `longitude`: -180..180, required.
- `days`: 1..14, default 7.

**Farm weather** uses the farm's stored location. Ownership is checked first, so another user's farm gets a 404 before Open-Meteo is called.

```jsonc
{
  "farmId": "uuid|null", "latitude": 26.85, "longitude": 80.95,
  "current": { "time": "2026-09-29T14:00:00+05:30", "temperatureC": 31.2, "relativeHumidityPct": 64,
               "precipitationMm": 0.0, "windSpeedKmh": null,
               "provenance": { "source": "OPEN_METEO", "dataClassification": "ESTIMATED", "retrievedAt": "…Z",
                               "notes": ["Model analysis for the grid cell, not a station observation."], … } },
  "daily": [ { "date": "2026-09-29", "minTemperatureC": 24.1, "maxTemperatureC": 33.0, "precipitationMm": 2.4,
               "precipitationProbabilityPct": 40, "relativeHumidityPct": 71 } ],
  "dailyProvenance": { "source": "OPEN_METEO", "dataClassification": "FORECAST", "retrievedAt": "…Z", "notes": [] }
}
```

- **Nulls:** every number is nullable. A value Open-Meteo omits stays `null` and is never set to zero.
- **Cache:** answers are cached for `WEATHER_CACHE_TTL` (default 30 min), keyed on coordinates rounded to 2 decimals plus `days`.
  - A cached answer keeps its original `retrievedAt`.
  - Failures are never cached.
- **No fallback:** there is none. A provider failure is an error; no mock or synthetic weather exists in production.

---

## GET /api/intelligence/supply

**What it returns:** a district × crop × season × crop-year production estimate from ML. **This is not a current-season forecast.** The underlying S01 data ends in crop year 2014.

**Parameters:**
- `districtId`, `cropId`: required ids.
- `season`: required, canonical season.
- `cropYear`: required, 1950–2100, and must be in `estimableYears`.
- `areaHectares`: optional, > 0.

**Flow:**
1. The request is checked against the synced scope. An out-of-scope request **never reaches ML**.
2. `MlClient` validates the ML response contract. The classification must match the method: `MODEL` ⇒ `MODEL_PREDICTION`, `BASELINE` ⇒ `ESTIMATED`. It also checks the units, that the values are ≥ 0, and that each value lies inside its interval.

**The 200 body:**

```
{ target, estimate { production, yield, area, servedMethod, historyYearsUsed }, baseline, reported|null, history,
  historicalYieldStats, modelEvaluation, provenance, historyProvenance, limitations[] }
```

- **`provenance`** has `source: "ML_SERVICE"` and the classification of the served method.
- **`historyProvenance`** has `source: "DES_S01_DATA_GOV_IN"` and is classified `OBSERVED`.
- **`limitations`** holds ML's list, plus these backend additions:
  - always: "Data ends in crop year {dataThrough}; this is not a current-season forecast."
  - when the baseline was served: "The trained model did not beat the baseline; the baseline estimate is served."

**Backend → ML:** `POST {ML_SERVICE_BASE_URL}/v1/predict/supply` with body `{districtId, cropId, season, cropYear, areaHectares|null}`. The contract is in `ml-service/docs/ml-contracts/supply.md`.

---

## GET /api/farms/{id}/crop-evidence

**Rule set:** `crop-evidence-v1` (§10).

**Parameters:**
- `season`: optional; defaults to the farm's mapped season.
- `cropYear`: optional; defaults to each series' latest estimable year.

The farm must have an in-scope district.

```jsonc
{ "farmId": "…", "districtId": "up-agra", "season": "RABI", "rankingRule": "crop-evidence-v1",
  "candidates": [
    { "rank": 1, "cropId": "wheat", "cropLabel": "Wheat", "tier": "SUITABLE",
      "evidence": {
        "seriesSupported": true,
        "soilCompatibility":  { "status": "OPTIMAL|TOLERABLE|OUTSIDE_ABSOLUTE_RANGE|UNAVAILABLE", "value": 6.5, "unit": "pH",
                                "basis": "FAO EcoCrop: optimal …", "provenance": { soil source + classification } },
        "weatherSuitability": { "status": "WITHIN_OPTIMAL|WITHIN_ABSOLUTE|OUTSIDE_ABSOLUTE|UNAVAILABLE", "value": 0,
                                "unit": "days", "basis": "…", "provenance": { OPEN_METEO, FORECAST } },
        "productionEvidence": { "status": "AVAILABLE|UNAVAILABLE", "cropYear": 2015, "coefficientOfVariation": 0.11,
                                "downsideYearShare": 0.18, "meanYield": {…}, "servedMethod": "MODEL",
                                "dataThrough": "2014", "provenance": { ML provenance } },
        "marketContext": { "status": "UNAVAILABLE", "basis": "Market data is not connected (milestone M4)" },
        "productionRisk": "LOW" },
      "reasons": [ { "code": "SOIL_PH_OPTIMAL", "text": "Soil pH 6.5 is within Wheat's optimal range …" } ],
      "unavailable": ["MARKET_CONTEXT"], "limitations": ["…"] },
    { "rank": null, "cropId": "onion", "tier": "NOT_SUPPORTED",
      "reasons": [{ "code": "SERIES_NOT_SUPPORTED", "text": "…" }] }
  ],
  "generatedAt": "…Z" }
```

**Tiers:**

| Tier | When |
|---|---|
| `NOT_SUPPORTED` | No series for the crop in this district and season. The crop is listed but not ranked (`rank` is null) |
| `UNSUITABLE` | Soil pH is outside the crop's absolute range, **or** production risk is CRITICAL |
| `SUITABLE_WITH_CAUTION` | Soil pH is only tolerable, **or** weather is outside the optimal range (including outside the absolute range), **or** production risk is HIGH |
| `SUITABLE` | None of the above |

**Order within a tier:**
1. production risk, lower first;
2. fewer unavailable evidence items first;
3. lower yield CV first (null last);
4. `cropId`.

Yields of different crops are never compared, and there is no single score.

**Partial failures.** If ML or Open-Meteo fails, the dependent evidence becomes `UNAVAILABLE`, and a reason or limitation names the error code. The endpoint still answers 200. It fails only if the farm or its scope is invalid.

---

## GET /api/farms/{id}/risk

**Rule set:** `risk-rules-v1` (§11).

**Parameters:**
- `cropId`: required.
- `season`: optional; defaults to the farm's mapped season.

```jsonc
{ "farmId": "…", "cropId": "wheat", "season": "RABI", "cropYear": 2015, "ruleSet": "risk-rules-v1",
  "productionRisk": { "level": "HIGH", "assessedFactors": 5, "unavailableFactors": [],
                      "factors": [ { "code": "HEAVY_RAINFALL", "level": "HIGH", "value": 70.0, "unit": "mm/day",
                                     "threshold": "IMD rainfall categories: …", "source": "OPEN_METEO",
                                     "dataClassification": "FORECAST", "observedOrForecastFor": "2026-09-29/2026-10-05",
                                     "reason": "…" } ],
                      "limitations": ["…"] },
  "marketRisk": { "level": "UNAVAILABLE", "factors": [], "assessedFactors": 0,
                  "unavailableFactors": ["PRICE_ANOMALY", "SUPPLY_PRESSURE"],
                  "limitations": ["Market data is not connected (milestone M4)"] },
  "generatedAt": "…Z" }
```

**How levels are set:**
- Each risk's level is its **worst assessed factor**. If nothing could be assessed, the level is `UNAVAILABLE`.
- `factors` lists only the assessed factors; the unavailable ones are named in `unavailableFactors`, with the reasons in `limitations`.
- `cropYear` is the latest estimable year of the series, or `null` when there is no series.

**Production factors:**

| Factor | Input | Rule |
|---|---|---|
| `HEAVY_RAINFALL` | Maximum 7-day forecast precipitation | IMD categories: < 64.5 mm LOW; 64.5–115.5 HIGH; ≥ 115.6 CRITICAL |
| `TEMPERATURE_STRESS` | 7-day forecast vs FAO EcoCrop limits | Outside the absolute range on 1–2 days HIGH, on ≥ 3 days CRITICAL; outside only the optimal range MODERATE; otherwise LOW |
| `YIELD_VARIABILITY` | ML yield CV | Product rule: < 0.15 LOW; 0.15–0.30 MODERATE; > 0.30 HIGH |
| `DOWNSIDE_FREQUENCY` | ML downside-year share | Product rule: < 0.2 LOW; 0.2–0.4 MODERATE; > 0.4 HIGH |
| `SOIL_PH` | Soil pH vs FAO EcoCrop limits | Optimal LOW; tolerable MODERATE; outside the absolute range HIGH |

**Blocker B2:** the FAO EcoCrop values are not transcribed yet (`crop_requirement` is all NULL). Until they are, `TEMPERATURE_STRESS` and `SOIL_PH` are always `UNAVAILABLE`, and so are `soilCompatibility` and `weatherSuitability` in crop evidence. No threshold is substituted.

---

## Correlation ids (D15)

- **Incoming:** a safe incoming `X-Request-Id` is kept: 1–64 characters from `A–Z a–z 0–9 . _ -`. Anything else is replaced with a generated UUID.
- **On responses:** the id comes back on every response and is exposed to the browser through CORS.
- **Downstream:** it is logged via MDC, forwarded to ML and Open-Meteo, and stored on audit rows.
- **Error body:** it is **not** a field in the error body; see "Deviations" in `PROJECT_STATE.md`.
