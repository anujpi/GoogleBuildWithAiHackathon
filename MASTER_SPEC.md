# MASTER SPECIFICATION — Agricultural Intelligence Platform

**Status:** canonical. This file is the **only** architecture and product source of truth. Where it disagrees with any other document (`PROJECT_STATE.md`, the `CLAUDE.md` files, READMEs, contract docs), this file wins, and the other document must be corrected.
**Version:** 1.0 · **Date:** 2026-09-28 · **Branch audited:** `anuj-ml-integration` @ `918c5f4`, plus uncommitted work.

**Basis:** a repository audit of:
- the backend: all modules, migrations V1–V3, and the 63 passing tests;
- the frontend: routes, `lib/api/client.ts`, `features/intelligence/*`;
- the ML service: the committed code, the **uncommitted refactor of 2026-09-28** (`reference.py`, `schemas/common.py`, `schemas/supply.py`, `schemas/health.py`, `schemas/reference.py`, `models/supply.py`, `training/supply.py`), and `ML_AUDIT_2026-09-28.md`.

The ML refactor already cites "Master Specification, decision 2" and "milestone M4". This document keeps those decisions (D2, M4) unchanged.

Key words: **MUST** = required for Done; **SHOULD** = expected unless a documented reason exists; **MAY** = optional.

---

## 0. Decision register

Every decision below is final unless it is changed by editing this section.

| # | Decision |
|---|---|
| D1 | Architecture: React SPA → Spring Boot modular monolith → (PostgreSQL/PostGIS, Python FastAPI ML service, external data providers). The frontend never calls ML or providers. **ML predicts. Spring Boot decides. (LLM explains: out of scope.)** |
| D2 | **MVP scope: state Uttar Pradesh (`up`); crops potato, wheat, onion, maize; district granularity.** Everything outside it is `UNSUPPORTED_INPUT`. This replaces the frontend's mh/ka and onion/tomato/soybean scope, and the catalogue's Maharashtra scope. |
| D3 | Canonical ids: `stateId` = lower-case slug (`up`); `districtId` = `<stateId>-<slug of S01 district name>` (`up-agra`); `cropId` = lower-case slug (`potato`). Pattern `^[a-z0-9][a-z0-9-]{0,49}$`. The ML service is the authority for the id list (derived from S01); the backend syncs it (§6.3). |
| D4 | Platform `DataClassification` = **`OBSERVED`, `FORECAST`, `MODEL_PREDICTION`, `ESTIMATED`, `SYNTHETIC`**. The backend's `REGIONAL_ESTIMATE` is renamed to `ESTIMATED`. Soil's own enum values are a subset and stay. Production code never generates `SYNTHETIC` data; the value exists only for user-entered demo soil data. |
| D5 | Supply intelligence = **district × crop × season × crop-year production estimate**, served by ML from its own S01 history. The backend sends ids, never history. The request is **not** a month horizon. (Resolves the backend↔ML mismatch, §9.4.) |
| D6 | Weather provider = **Open-Meteo Forecast API** (no API key). "Current" conditions are classified `ESTIMATED` (a model analysis, not a station reading); the daily forecast is classified `FORECAST`. No station data, no synthetic weather, no mock fallback. |
| D7 | Crop decisions: ML provides production evidence only. The **backend** filters, applies rules, groups crops into tiers, orders them and composes the explanation, using the versioned rule set `crop-evidence-v1`. There is no single score and no "AI says grow X". |
| D8 | Risk: **production risk** and **market risk** are separate objects. The level is the **worst contributing factor** (no weighted black-box score). Rule set `risk-rules-v1`, with thresholds from cited sources (IMD rainfall categories, FAO EcoCrop limits) or declared product rules. |
| D9 | RBAC: FARMER and FPO own farms. AGRICULTURAL_OFFICER and FPO read regional intelligence for **admin-assigned districts**. ADMIN manages users, roles, assignments and the system. Ownership comes only from the JWT principal. |
| D10 | Market prices + anomalies = **STRETCH, milestone M4**. Demand proxy + supply-demand gap = **STRETCH, milestone M5**. Until they are built, their endpoints **do not exist** (no 503 placeholders), and market risk is reported as a factor with level `UNAVAILABLE`. |
| D11 | Every obsolete mock/synthetic production path is removed (§17): frontend dashboard mock, supply-demand mock, `VITE_INTELLIGENCE_SOURCE`, and the backend placeholder 503 intelligence endpoints. |
| D12 | Crop agronomic limits (pH, temperature) come from **FAO EcoCrop**. They are stored with a citation in `crop_requirement`; a value that can't be found is NULL, never estimated. |
| D13 | Legacy ownerless farms (from before V3) are kept. ADMIN can list them and assign them an owner. `owner_id` stays nullable at the schema level; the application never creates an ownerless farm. |
| D14 | Error body = the existing `ApiError` shape, with the final code list in §13. The ML service uses `{error:{code,message,details}}` with the codes in §9.3, and the backend translates them. |
| D15 | Every request gets a correlation id (`X-Request-Id`, generated if absent). It is logged through MDC and forwarded to ML and providers. |

---

## 1. Product definition

An agricultural decision-intelligence platform for Uttar Pradesh farmers, FPOs and agricultural officers. For a farm, it answers:
1. **What is my farm?** Location, district, soil (with provenance), irrigation, season and crop.
2. **What is the weather doing?** Current conditions and a forecast of up to 14 days from a real provider.
3. **How much does this district usually produce, and what is the estimate?** Supply estimates per district × crop × season × crop year, with an interval, a baseline, the reported history, model evaluation and data vintage.
4. **Which crops fit my farm, and why?** Structured evidence per crop (soil, weather, production reliability, market context, risk), grouped into tiers and ordered transparently.
5. **What are the production and market risks?** Kept separate, with factor-level reasons.

Officers and FPOs see the same intelligence across their assigned districts. Admins run the system.

**Honesty rules** (non-negotiable):
- Every value carries a classification and a source.
- Missing means unavailable, never zero, never invented.
- Model output is never labelled observed.
- **Data vintage is always shown.** S01 production data ends in crop year 2014 (the 2015 data is partial and excluded), so supply estimates are presented as "estimate for crop year Y from reported history through Y-1". They are never presented as a forecast of the current season.

### 1.1 Scope classification

| CORE (must ship) | STRETCH (only after CORE is Done) | OUT OF SCOPE |
|---|---|---|
| Auth (register/login/JWT/me) | M4: market prices (data.gov.in Agmarknet daily prices) + price anomalies + market risk factor | Disease detection / Crop Doctor |
| RBAC incl. district assignments | M5: demand proxy forecast (mandi arrivals) + supply-demand gap | Scenario simulator |
| Farms + location + district + soil | Paged farm list | Regional coordination / surplus-shortage matching |
| Reference scope sync (states/districts/crops/series) | NASA POWER weather features in the supply model | LLM advisory / chat |
| Weather (Open-Meteo) with split provenance | Weather history (Open-Meteo archive) | Alerts / notifications |
| Supply estimate (ML) | | Satellite/NDVI |
| Crop evidence per farm | | Refresh tokens, password reset, email verification, OAuth |
| Production risk per farm × crop; market risk factor = UNAVAILABLE until M4 | | States other than UP; mobile apps |
| Regional view (officer/FPO) | | Kafka, Redis, microservices, gateway, Kubernetes |
| Admin: users, roles, assignments, system/ML health, model/dataset metadata, audit log, legacy farm assignment | | Spring profiles beyond default + test |
| Correlation ids, provider/ML health | | |

---

## 2. Architecture

```
React SPA (Vite)  ──HTTPS/JSON, Bearer JWT──►  Spring Boot 4 modular monolith
                                                 ├─► PostgreSQL 17 + PostGIS (Flyway-owned schema)
                                                 ├─► ML service (FastAPI)   via ml/MlClient      [typed contract §9]
                                                 └─► Open-Meteo             via weather/OpenMeteoWeatherProvider
ML service ─► its own artifacts + S01 data (never writes to the app DB)
```

- **One Spring Boot application.** No microservices, Kafka, Redis, gateway or Kubernetes.
- **HTTP to the outside is isolated:**
  - only `ml.MlClient` talks HTTP to ML;
  - only classes implementing `*Provider` talk HTTP to external data sources;
  - business services never see HTTP types.
- **Caching:** only an in-process TTL cache for weather (§7.4). No Redis.

---

## 3. Backend module boundaries

Root package: `com.argiintelligence.backend`. The spelling is intentional.

| Module | Owns | Depends on |
|---|---|---|
| `common` | `ApiError`, `ApiException`, `GlobalExceptionHandler`, `DataClassification`, `RiskLevel`, `Provenance` DTO, request-id filter | — |
| `configuration` | CORS | — |
| `auth` | register/login/me, JWT (`JwtService`), `SecurityConfig`, `AuthenticatedUser`, the JSON 401/403 handler | `user`, `audit` |
| `user` | `User`, `Role`, district assignments, `UserResponse` | `reference` |
| `reference` | `ref_state`, `ref_district`, `ref_crop`, `ref_supply_series`, `ref_dataset`, `crop_requirement`; sync from ML; `/api/reference/*` | `ml` |
| `farm` | Farm, FarmLocation, SoilProfile; ownership; `farm.district_id` | `reference`, `user` |
| `weather` | `WeatherProvider`, `OpenMeteoWeatherProvider`, `WeatherService`, TTL cache | `farm` |
| `ml` | `MlClient`, typed ML DTOs, `MlProperties`, ML error translation | — |
| `intelligence` | supply estimate (`SupplyService`), crop evidence (`CropEvidenceService`), risk (`RiskService`), rule sets | `ml`, `weather`, `farm`, `reference` |
| `regional` | officer/FPO district views | `farm`, `intelligence`, `user` |
| `admin` | user/role/assignment management, system health, ML/model/dataset metadata, legacy farms | `user`, `farm`, `ml`, `weather`, `reference`, `audit` |
| `audit` | `audit_event` persistence + query | — |
| `market` | **STRETCH (M4) only.** Not created before M4. | — |

Layering follows `backend/CLAUDE.md` §7: controller → service → repository. DTOs, never entities, go in responses.

---

## 4. RBAC

### 4.1 Roles

| Role | How obtained | Capabilities |
|---|---|---|
| FARMER | Public registration (always) | CRUD own farms; own farm weather/crop evidence/risk; supply estimates; reference scope; own `/me` |
| FPO | ADMIN changes the role | Everything FARMER can do, plus a **read-only** regional view of its assigned districts |
| AGRICULTURAL_OFFICER | ADMIN changes the role | **No own farms.** Read-only regional view of assigned districts, including read-only farm detail, weather, crop evidence and risk for farms in those districts; supply estimates; reference |
| ADMIN | ADMIN changes the role; the first admin is bootstrapped by env (§14) | User/role/assignment management, system/ML/provider health, model and dataset metadata, audit log, reference sync, legacy farm assignment, a read-only regional view of all districts. **No own farms.** |

### 4.2 Endpoint authorization matrix

Legend: ✔ allowed · own = own resources only · asg = assigned districts only · ✘ = 403.

| Endpoint group | FARMER | FPO | OFFICER | ADMIN |
|---|---|---|---|---|
| `/api/auth/register`, `/login` | public | public | public | public |
| `/api/auth/me`, `/api/reference/*` | ✔ | ✔ | ✔ | ✔ |
| `POST/GET/PUT /api/farms[/{id}]`, `/api/farms/{id}/weather`, `/crop-evidence`, `/risk` | own | own | ✘ | ✘ |
| `/api/regional/farms/{id}` and its `/weather`, `/crop-evidence`, `/risk` (read-only) | ✘ | asg | asg | ✔ |
| `GET /api/weather?lat&lon` | ✔ | ✔ | ✔ | ✔ |
| `GET /api/intelligence/supply` | ✔ | ✔ | ✔ | ✔ |
| `/api/regional/**` | ✘ | asg | asg | ✔ |
| `/api/admin/**` | ✘ | ✘ | ✘ | ✔ |

### 4.3 Enforcement rules

- **Role checks:** the URL rules in `SecurityConfig` (coarse role gates), plus a service-level check for every resource access (fine-grained).
- **The principal:** `AuthenticatedUser`, reloaded from the database on every request. Disabled users get 401, and role changes apply immediately.
- **Hidden resources:** a resource the caller may not see returns **404** (`FARM_NOT_FOUND`), never 403. This applies to another farmer's farm and to a farm outside an officer's districts.
- **Wrong role:** a caller whose *role* lacks the capability entirely gets **403** `FORBIDDEN`.
- **Client input never grants access:** `ownerId`, `role`, `userId` and district claims sent by the client are ignored. For example, a `role` field in the registration body is ignored.
- **Last admin:** an ADMIN can't remove their own ADMIN role or disable themselves. The last enabled ADMIN can't be demoted or disabled (409 `CONFLICT`).

---

## 5. Database model (Flyway)

Existing: V1 (farm, farm_location, soil_profile, PostGIS), V2 (optional soil), V3 (users, farm.owner_id).
All ids are UUIDs except the reference slugs. All timestamps are `timestamptz` in UTC. Enums are varchar with CHECK constraints.

### 5.1 New migrations (in this order)

**V4__reference_data.sql**

| Table | Columns | Notes |
|---|---|---|
| `ref_state` | `state_id` varchar(50) PK, `label` varchar(100) NOT NULL | |
| `ref_district` | `district_id` varchar(50) PK, `state_id` FK → ref_state NOT NULL, `label` varchar(100) NOT NULL | |
| `ref_crop` | `crop_id` varchar(50) PK, `label` varchar(100) NOT NULL | |
| `ref_supply_series` | `district_id` FK, `crop_id` FK, `season` varchar(16) CHECK (KHARIF, RABI, SUMMER, WHOLE_YEAR, AUTUMN, WINTER), `first_year` int, `last_year` int, `years_observed` int | PK (district_id, crop_id, season) |
| `ref_dataset` | `source` varchar(100), `dataset_version` varchar(100), `data_through` varchar(10) | PK (source, dataset_version) |
| `reference_sync` | `id` uuid PK, `synced_at` timestamptz, `status` (SUCCEEDED, FAILED), `ml_model_version` null, `message` null | Sync history |

Sync is an **upsert**. A district or crop that disappears from ML is kept (farms reference it) but gets no supply series.

**V5__farm_district_and_assignments.sql**
- `farm.district_id varchar(50) NULL REFERENCES ref_district`, plus an index.
  - Nullable, because farms may lie outside the scope. They then get weather only; crop evidence and risk answer 422 `UNSUPPORTED_INPUT`.
  - Existing rows stay NULL; nothing is guessed.
- `user_district_assignment(user_id uuid FK users ON DELETE CASCADE, district_id FK ref_district, assigned_at timestamptz, assigned_by uuid FK users NULL, PK(user_id, district_id))`.
- The owner listing index `farm_owner_created_at_idx` already exists (V3).

**V6__audit_event.sql**
`audit_event(id uuid PK, occurred_at timestamptz NOT NULL, actor_user_id uuid NULL FK users, action varchar(40) NOT NULL CHECK(...), target_type varchar(30) NULL, target_id varchar(64) NULL, details jsonb NOT NULL DEFAULT '{}', request_id varchar(64) NULL)`.
- Indexes: (occurred_at DESC), (actor_user_id).
- Actions: `USER_REGISTERED`, `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `ROLE_CHANGED`, `USER_ENABLED`, `USER_DISABLED`, `DISTRICTS_ASSIGNED`, `FARM_CREATED`, `FARM_UPDATED`, `FARM_OWNER_ASSIGNED`, `REFERENCE_SYNCED`, `ADMIN_BOOTSTRAPPED`.
- `details` never contains passwords, tokens or secrets. `LOGIN_FAILED` stores the normalized email only.

**V7__crop_requirement.sql**

`crop_requirement` has these columns:

| Column | Meaning |
|---|---|
| `crop_id` | PK, FK → ref_crop (seeded with the four MVP crop ids) |
| `ph_abs_min`, `ph_opt_min`, `ph_opt_max`, `ph_abs_max` | pH limits: absolute and optimal |
| `temp_abs_min_c`, `temp_opt_min_c`, `temp_opt_max_c`, `temp_abs_max_c` | Temperature limits in °C: absolute and optimal |
| `source` | `'FAO EcoCrop'` |
| `source_url` | The per-crop EcoCrop URL |
| `retrieved_on` | The date the values were read |

All numeric columns are `numeric NULL`; a value not published by EcoCrop is NULL.

Seeding:
- V7 first **inserts the four D2 crops into `ref_crop`** (`potato`/Potato, `wheat`/Wheat, `onion`/Onion, `maize`/Maize, using `ON CONFLICT DO NOTHING`), so the foreign key holds on a clean database before any ML sync. A later sync upserts the same ids.
- V7 then inserts their `crop_requirement` rows.
- **The implementer MUST transcribe the values from FAO EcoCrop and cite the URLs; invented values are forbidden.**

**Migration numbering follows the phase order (§19):** V4, V5 and V6 ship in P2, and V7 ships in P3. Flyway rejects a lower version that arrives after a higher one has been applied, so a version number is never reused or reordered.

**The D4 classification rename needs no migration.** No table stores `REGIONAL_ESTIMATE` (soil already uses `ESTIMATED`), so the rename is a code-only change. V7 is the last migration in CORE.

### 5.2 Unchanged

- Farm/soil columns and constraints, the users table, and `owner_id` nullability (D13).
- `soil_profile` classification CHECK (OBSERVED, ESTIMATED, SYNTHETIC).
- The soil source → classification rule (`SOIL_HEALTH_CARD`/`LAB_REPORT` → OBSERVED only; `REGIONAL_ESTIMATE` → ESTIMATED or SYNTHETIC; `MANUAL`/`OTHER` → any). `SoilDataSource.REGIONAL_ESTIMATE` is a *source* value and keeps its name.

### 5.3 Farm season ↔ canonical season

Farm keeps its user-facing `Season` enum (KHARIF, RABI, ZAID, OTHER). It maps to the canonical ML season as follows: KHARIF→KHARIF, RABI→RABI, ZAID→SUMMER, OTHER→none. A farm in season OTHER can't get crop evidence or risk without an explicit `season` query parameter (422 `UNSUPPORTED_INPUT`).

---

## 6. Backend API (final)

Base `/api`. JSON uses camelCase. Timestamps are ISO-8601 UTC. Errors are covered in §13. The authorization matrix is §4.2.

### 6.1 Auth (exists; unchanged except for audit)
- `POST /auth/register {fullName, email, password(8–72)}` → 201 `UserResponse {id, fullName, email, role, enabled, assignedDistrictIds[]}`.
- `POST /auth/login {email, password}` → `{accessToken, tokenType:"Bearer", expiresIn, user: UserResponse}`. Failure → 401 `INVALID_CREDENTIALS` (the same answer for every reason).
- `GET /auth/me` → `UserResponse`.

### 6.2 Farms (exists; extended)
- `FarmRequest` gains `districtId: string | null`. It must exist in `ref_district`, otherwise 400 `VALIDATION_ERROR` on field `districtId`. Everything else is unchanged; see `backend/docs/farm-api.md`.
- `FarmResponse` gains `districtId`, `districtLabel` (null if none) and `intelligenceSupported: boolean` (district set **and** in scope).
- `POST /farms`, `GET /farms` (own, newest first, unpaged), `GET /farms/{id}`, `PUT /farms/{id}`. There is no DELETE.
- `GET /farms/{id}/weather?days=1..14` (default 7) → `WeatherResponse` (§7).
- `GET /farms/{id}/crop-evidence?season=&cropYear=` → `CropEvidenceResponse` (§10).
- `GET /farms/{id}/risk?cropId=&season=` → `RiskAssessmentResponse` (§11).
- **Moved:** `GET /api/weather/farms/{farmId}` becomes `GET /api/farms/{id}/weather`, and the old path is deleted.

### 6.3 Reference
- `GET /reference/scope` → `{ states[{stateId,label}], districts[{districtId,stateId,label}], crops[{cropId,label}], seasons[], supplySeries[{districtId,cropId,season,firstYear,lastYear,yearsObserved,estimableYears:[firstYear+1 … lastYear+1]}], datasets[{source,datasetVersion,dataThrough}], syncedAt }`.
- `estimableYears` = `firstYear+1 … lastYear+1`. These are *candidate* years for the selectors. Gaps inside a series can still make a year non-estimable; ML is the authority, and such a year returns 422 `INSUFFICIENT_DATA`.
- The data is served from the backend's synced tables, never proxied live.
- If no sync has succeeded yet, the response is a 200 with empty lists and `syncedAt: null`. The frontend shows "Reference data not loaded yet".

### 6.4 Weather
- `GET /weather?latitude=-90..90&longitude=-180..180&days=1..14` → `WeatherResponse` (§7).

### 6.5 Intelligence
- `GET /intelligence/supply?districtId&cropId&season&cropYear[&areaHectares>0]` → `SupplyEstimateResponse` (§8).

### 6.6 Regional (FPO, OFFICER: assigned districts; ADMIN: all)
- `GET /regional/districts` → the caller's districts with a farm count per district.
- `GET /regional/farms?districtId=` → a read-only `FarmSummary[] {id, name, districtId, districtLabel, area, areaUnit, currentCrop, season, soilDataAvailable, updatedAt}`.
  - Owner PII is not exposed: no email and no owner name.
  - A district outside the caller's assignments gives 404 `DISTRICT_NOT_FOUND`.
- `GET /regional/farms/{id}` → `FarmResponse` (read-only), plus `/weather`, `/crop-evidence` and `/risk` under the same path, with the same bodies as §6.2.

### 6.7 Admin (ADMIN only)
- **Users:**
  - `GET /admin/users?page&size&role&q` → `Page<UserResponse>`. `size` is ≤ 100; `q` matches email or name.
  - `PATCH /admin/users/{id} {role?, enabled?}` → `UserResponse`. Audited; last-admin rule in §4.3.
  - `PUT /admin/users/{id}/districts {districtIds[]}` → `UserResponse`. Only for FPO/OFFICER, otherwise 409 `CONFLICT`. Audited.
- **System:**
  - `GET /admin/system/health` → `{ database:{status}, mlService:{status, url, latencyMs, models[] (ML /health)}, weatherProvider:{status, provider:"OPEN_METEO", lastSuccessAt, lastError}, referenceSync:{lastSyncedAt, status}, build:{version} }`. Status is `UP`, `DOWN` or `DEGRADED`.
  - `GET /admin/ml/models` → ML `/health` `models[]`, the scope `datasets[]`, and the supply `modelEvaluation` metadata when it's available.
  - `POST /admin/reference/sync` → runs the sync and returns `reference_sync` status. Audited.
- **Audit:** `GET /admin/audit?page&size&action&actorUserId&from&to` → `Page<AuditEventResponse>`.
- **Legacy farms:**
  - `GET /admin/farms/unowned` → `FarmSummary[]`.
  - `POST /admin/farms/{id}/owner {userId}` → assigns an owner to an ownerless farm only. The target user must be FARMER or FPO, and a farm that already has an owner gives 409 `CONFLICT`. Audited.

`Page<T>` = `{items[], page, size, totalItems, totalPages}`.

### 6.8 Actuator
`GET /actuator/health` is public. It includes `db` and custom `ml` and `weather` indicators, all reported as details only. **ML or weather being DOWN makes the app DEGRADED, not DOWN** (see `management.endpoint.health.status.order` in §14).

### 6.9 Removed endpoints (D10, D11)

These are deleted and return 404:
- `/api/intelligence/supply-forecast`, `/demand-forecast`, `/supply-demand`, `/crop-recommendations`, `/agricultural-risk`
- `/api/weather/farms/{farmId}`

---

## 7. Weather architecture (CORE)

### 7.1 Provider
`OpenMeteoWeatherProvider` implements `WeatherProvider`. It calls `GET {WEATHER_BASE_URL}/v1/forecast` with:
- `latitude`, `longitude`, `timezone=Asia/Kolkata`, `forecast_days=days`;
- `current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m`;
- `daily=temperature_2m_min,temperature_2m_max,precipitation_sum,precipitation_probability_max,relative_humidity_2m_mean`.

It uses Spring `RestClient` with connect and read timeouts. It is the only production `WeatherProvider`. **A mock weather provider is forbidden in production code; test doubles live in tests only.** `WeatherService` keeps its guard that refuses `SYNTHETIC` reports.

### 7.2 Response

```jsonc
{
  "farmId": "uuid|null", "latitude": 26.9, "longitude": 80.9,
  "current": { "time": "2026-09-28T14:00:00+05:30", "temperatureC": 31.2, "relativeHumidityPct": 64,
               "precipitationMm": 0.0, "windSpeedKmh": 9.4,
               "provenance": { "source": "OPEN_METEO", "dataClassification": "ESTIMATED",
                               "retrievedAt": "...Z", "notes": ["Model analysis for the grid cell, not a station observation."] } },
  "daily": [ { "date": "2026-09-28", "minTemperatureC": 24.1, "maxTemperatureC": 33.0,
               "precipitationMm": 2.4, "precipitationProbabilityPct": 40, "relativeHumidityPct": 71 } ],
  "dailyProvenance": { "source": "OPEN_METEO", "dataClassification": "FORECAST", "retrievedAt": "...Z", "notes": [] }
}
```

- Every numeric field is nullable. A value Open-Meteo omits stays `null`; it is never zero-filled.
- **Renames from today's DTO:** `current.observedAt` → `current.time`; `rainfallMm` → `precipitationMm`; `rainProbabilityPct` → `precipitationProbabilityPct`. The single `provenance` field becomes the two above.

### 7.3 Errors

| Condition | Response |
|---|---|
| Invalid coordinates or days | 400 `VALIDATION_ERROR` |
| Connect failure or timeout | 503 `UPSTREAM_UNAVAILABLE` |
| HTTP 429 | 503 `UPSTREAM_RATE_LIMITED` |
| Other 4xx/5xx, malformed JSON, missing `current`/`daily`, array length mismatch, NaN | 502 `UPSTREAM_INVALID_RESPONSE` |

Every provider error names the provider in its message. The body never contains any weather values.

### 7.4 Caching
- **Cache key:** in-process TTL cache (`WEATHER_CACHE_TTL`, default `PT30M`) keyed on (lat and lon rounded to 2 decimals, days).
- **Provenance:** a cached answer keeps its original `retrievedAt`.
- **Failures:** they are never cached.

---

## 8. Supply intelligence (CORE)

### 8.1 Flow
1. Validate the parameters: ids match the pattern, `season` is the canonical enum, `cropYear` is 1950–2100, `areaHectares` > 0 when given.
2. Check the input against the synced scope. The district and crop must exist, and the (district, crop, season) series must exist. `cropYear` must be in `estimableYears`. Anything else gives 422 `UNSUPPORTED_INPUT`; the details name the field.
3. Build `MlSupplyRequest {districtId, cropId, season, cropYear, areaHectares|null}` and call `MlClient.predictSupply` (§9).
4. Validate the ML response (§9.5), then apply the business rules:
   - `servedMethod=MODEL` ⇒ classification `MODEL_PREDICTION`; `servedMethod=BASELINE` ⇒ `ESTIMATED`. A mismatch gives 502 `ML_INVALID_RESPONSE`.
   - Production must be ≥ 0, the interval must satisfy lower ≤ value ≤ upper, and the units must match exactly.
5. Map the response to `SupplyEstimateResponse`, adding labels from the reference tables and the backend's `retrievedAt`.

### 8.2 `SupplyEstimateResponse`

```jsonc
{
  "target": { "districtId": "up-agra", "districtLabel": "Agra", "cropId": "potato", "cropLabel": "Potato",
              "season": "RABI", "cropYear": 2014 },
  "estimate": {
    "production": { "value": 1834000, "unit": "TONNES",
                    "interval": { "lower": 1.52e6, "upper": 2.1e6, "nominalCoverage": 0.8,
                                  "empiricalCoverage": 0.78, "method": "EMPIRICAL_LOG_RESIDUAL_QUANTILES_VALIDATION" } | null },
    "yield": { "value": 27.1, "unit": "TONNES_PER_HECTARE", "interval": { ... } | null },
    "area": { "value": 67600, "unit": "HECTARES", "areaSource": "REQUEST|REPORTED|LAST_REPORTED" },
    "servedMethod": "MODEL|BASELINE",
    "historyYearsUsed": [2011, 2012, 2013]
  },
  "baseline": { "method": "AREA_X_MEAN_YIELD_3Y", "production": { "value": 1790000, "unit": "TONNES" } },
  "reported": { "area": {...}, "production": {...}, "yield": {...} } | null,
  "history": { "units": { "area": "HECTARES", "production": "TONNES", "yield": "TONNES_PER_HECTARE" },
               "points": [ { "cropYear": 2011, "area": 0, "production": 0, "yield": 0 } ] },
  "historicalYieldStats": { "yearsObserved": 17, "meanYield": { "value": 0, "unit": "TONNES_PER_HECTARE" },
                            "coefficientOfVariation": 0.12, "downsideYearShare": 0.2,
                            "yearsAssessedForDownside": 15, "downsideDefinition": "..." },
  "modelEvaluation": { "trainingPeriod": "…", "validationPeriod": "…", "testPeriod": "…", "servedMethod": "MODEL",
                       "testWape": 0.14, "bestBaseline": "AREA_X_MEAN_YIELD_3Y", "bestBaselineTestWape": 0.17,
                       "testIntervalCoverage": 0.78 },
  "provenance": { "source": "ML_SERVICE", "dataClassification": "MODEL_PREDICTION|ESTIMATED",
                  "modelName": "…", "modelVersion": "…", "featureVersion": "supply-features-v2",
                  "datasetVersion": "s01-…", "dataThrough": "2014", "generatedAt": "…Z", "retrievedAt": "…Z" },
  "historyProvenance": { "source": "DES_S01_DATA_GOV_IN", "dataClassification": "OBSERVED",
                         "datasetVersion": "s01-…", "dataThrough": "2014" },
  "limitations": [ "…from ML verbatim…", "…backend additions…" ]
}
```

- **`reported`** is present only when S01 contains the target year (a backtest). The UI shows it as "reported (observed)" next to the estimate.
- **`limitations`** is ML's list verbatim, plus these backend additions:
  - always: "Data ends in crop year {dataThrough}; this is not a current-season forecast."
  - when `servedMethod=BASELINE`: "The trained model did not beat the baseline; the baseline estimate is served."
- **Null fields:** any field that ML sends as null stays null in this response.

---

## 9. ML contract (CORE) — the only valid contract

Transport: HTTP/JSON, camelCase. Unknown request fields are rejected. The backend tolerates unknown **response** fields. `X-Request-Id` is forwarded.

### 9.1 `GET /health` → 200

```json
{ "status": "UP", "service": "agri-ml", "version": "0.1.0", "environment": "dev",
  "models": [ { "capability": "SUPPLY", "status": "READY|NOT_READY", "modelVersion": null, "datasetVersion": null,
                "featureVersion": null, "reason": "MODEL_NOT_LOADED|ARTIFACT_LOAD_ERROR|null" } ] }
```

### 9.2 `GET /v1/reference/scope` → 200 `ScopeResponse`

The body is `{states, districts, crops, seasons, supplySeries, marketSeries, datasets}`, as in `schemas/reference.py`.
- It is derived from the loaded supply artifact's scope.
- `marketSeries` stays `[]` until M4.
- With no artifact loaded, the answer is 503 `MODEL_NOT_LOADED`.

### 9.3 `POST /v1/predict/supply`

Request (`SupplyRequest`):

```json
{ "districtId": "up-agra", "cropId": "potato", "season": "RABI", "cropYear": 2014, "areaHectares": null }
```

- **200:** `SupplyResponse`, exactly the model in `ml-service/src/agri_ml/schemas/supply.py`:
  - `target`, `estimate` (production and yield with an optional interval; area with `areaSource`; `servedMethod`; `historyYearsUsed`; `provenance`);
  - `baseline`, `reported|null`, `history`, `historicalYieldStats`, `modelEvaluation`, `limitations`.
- **Units:** the `Unit` enum (`TONNES`, `HECTARES`, `TONNES_PER_HECTARE`), never lower-case strings.
- **Provenance:** `{source, dataClassification, datasetVersion, modelName, modelVersion, featureVersion, dataThrough, generatedAt}`. Unknown values are null, never invented.

**ML errors** (body `{ "error": { "code", "message", "details": {} } }`):

| HTTP | code | When | Backend maps to |
|---|---|---|---|
| 422 | `VALIDATION_ERROR` | Schema/pattern/type violation | 502 `ML_INVALID_RESPONSE`. The backend validated first, so this is a contract bug; log at ERROR |
| 422 | `UNSUPPORTED_DISTRICT` / `UNSUPPORTED_CROP` / `UNSUPPORTED_SEASON` / `UNSUPPORTED_SERIES` | Id or series not in the artifact scope | 422 `UNSUPPORTED_INPUT` |
| 422 | `INSUFFICIENT_HISTORY` | Missing t-1, or no positive yield in t-1..t-3 | 422 `INSUFFICIENT_DATA` |
| 422 | `AREA_UNAVAILABLE` | No `areaHectares` sent and no reported area for the year or year-1 | 422 `INSUFFICIENT_DATA` |
| 503 | `MODEL_NOT_LOADED` / `ARTIFACT_LOAD_ERROR` | No usable artifact | 503 `ML_PREDICTION_UNAVAILABLE` |
| 500 | `INTERNAL_ERROR` | Unexpected | 502 `ML_INVALID_RESPONSE` |

Transport failures: a connect failure or timeout gives 503 `ML_UNAVAILABLE`. A non-JSON body, a missing required field, or a failed business validation (§8.1 step 4) gives 502 `ML_INVALID_RESPONSE`.

### 9.4 Resolution of the existing mismatch

These are replaced, and **none of the old shapes survive**:

| Old side | Was | Becomes |
|---|---|---|
| Backend request | `Map{regionId, cropId, horizonMonths}` | `MlSupplyRequest` above |
| Backend response DTO | `SupplyPredictionResponse{modelVersion, prediction{value,unit,period}, provenance{3 fields}}` | Typed records mirroring §9.3 |
| ML app | `app.py` built on `SupplyPredictionRequest{crop, season, cropYear, areaHectares, history[]}` and `SupplyPredictionResponse` | `app.py` rebuilt on the refactored `SupplyRequest`/`SupplyResponse`. ML loads its own S01 history from the artifact/data; the caller never sends history |
| Errors | FastAPI `{detail}` | `{error:{code,message,details}}` |
| Units | `"tonnes"` | `TONNES` |
| Classification | ML `ESTIMATED` vs backend `REGIONAL_ESTIMATE` | `ESTIMATED` everywhere (D4) |
| Status mapping | ML 503 → backend 502 | ML 503 → backend 503 `ML_PREDICTION_UNAVAILABLE` |

### 9.5 `MlClient` responsibilities
- **It owns:** the base URL, timeouts, serialization, typed request and response DTOs, required-field validation, and the upstream error translation above.
- **It exposes:** `health()`, `scope()` and `predictSupply(MlSupplyRequest)`.
- **It never:** ranks crops or applies business rules.

### 9.6 ML training and inference requirements
- **Data:** S01 (data.gov.in resource `35be999b-0208-4354-b557-f6ca9a5355de`) for UP, restricted to the four crops. It is downloaded with a registered `DATA_GOV_IN_API_KEY`, and the manifest records the URL, GODL license, access time and key type.
- **Artifact:** `artifacts/supply/<modelVersion>/` holds:
  - `model.json`;
  - `metadata.json`, containing modelName, modelVersion, featureVersion, datasetVersion, dataThrough, periods, servedMethod, interval, scope from `build_scope`, and evaluation;
  - `series.parquet`, the scoped history used at inference.
  Artifacts are git-ignored and never overwritten.
- **Acceptance rule (already coded):**
  - The model is served only if its validation WAPE is ≥ 5% better than the best baseline's and its test WAPE is not worse. Otherwise the best baseline is served.
  - The interval is calibrated for the served method.
  - The spatial (held-out-district) evaluation is reported.
- **Model card:** `docs/model-cards/supply.md` with real metrics, the baselines, the error analysis, the "final reported area" caveat and scope limits. It is required before the model is demoed.
- **Tests:** schema tests, error codes, a leakage test, and a test against the real artifact once one exists. The environment must reproduce on Windows (the lock file must not require `uvloop`).

---

## 10. Crop evidence (CORE)

`GET /farms/{id}/crop-evidence?season=&cropYear=`:
- `season` defaults to the farm's mapped season (§5.3).
- `cropYear` defaults to the latest estimable year for each series.
- The farm must have an in-scope `districtId`, otherwise 422 `UNSUPPORTED_INPUT`.

### 10.1 Evidence per candidate crop

Candidates are the in-scope crops.

| Evidence | Source | Classification |
|---|---|---|
| `seriesSupported` | `ref_supply_series` for the farm district + crop + season | — |
| `soilCompatibility` | Farm soil pH vs `crop_requirement` pH ranges: `OPTIMAL` \| `TOLERABLE` \| `OUTSIDE_ABSOLUTE_RANGE` \| `UNAVAILABLE` (no soil, no pH, or no requirement) | The soil profile's own classification |
| `weatherSuitability` | Derived from the `TEMPERATURE_STRESS` risk factor (§11.1, 7-day forecast): LOW → `WITHIN_OPTIMAL`, MODERATE → `WITHIN_ABSOLUTE`, HIGH/CRITICAL → `OUTSIDE_ABSOLUTE`, else `UNAVAILABLE`. It reflects the next 7 days, not the whole season; this is always listed as a limitation | `FORECAST` |
| `productionEvidence` | ML supply response for the farm district: yield CV, downside share, mean yield, servedMethod, dataThrough | The ML provenance classification |
| `marketContext` | `UNAVAILABLE` until M4 | — |
| `productionRisk` | The §11 level for this crop | — |

### 10.2 Tiers (`crop-evidence-v1`)
- **NOT_SUPPORTED:** there is no supply series. The crop is listed with its reason and is not ranked.
- **UNSUITABLE:** soil pH is `OUTSIDE_ABSOLUTE_RANGE`, **or** production risk is `CRITICAL`.
- **SUITABLE_WITH_CAUTION:** any evidence is `TOLERABLE`, `WITHIN_ABSOLUTE` or `OUTSIDE_ABSOLUTE` (weather), **or** production risk is `HIGH`.
- **SUITABLE:** none of the above.

Order within a tier:
1. production risk level, lower first;
2. more assessed factors (fewer `UNAVAILABLE`) first;
3. lower yield CV first (null last);
4. `cropId`.

Yields of different crops are **never** compared directly.

### 10.3 Response

```jsonc
{ "farmId": "...", "districtId": "up-agra", "season": "RABI", "rankingRule": "crop-evidence-v1",
  "candidates": [ { "rank": 1, "cropId": "wheat", "cropLabel": "Wheat", "tier": "SUITABLE",
    "evidence": { ... §10.1 each with value, level/status, provenance ... },
    "reasons": [ { "code": "SOIL_PH_OPTIMAL", "text": "Soil pH 6.8 is within wheat's optimal range 6.0–7.5 (FAO EcoCrop)." } ],
    "unavailable": [ "MARKET_CONTEXT" ], "limitations": [ ... ] } ],
  "generatedAt": "...Z" }
```

- **Reasons:** `reasons[].text` is composed deterministically from templates. There is no LLM involved.
- **Unavailable evidence:** it is listed in `unavailable`, and never shown as a value.
- **Partial failures:** an ML or weather failure doesn't fail the whole response. That evidence becomes `UNAVAILABLE`, with a limitation naming the upstream error code. The endpoint fails only if the farm or its scope is invalid.

---

## 11. Risk (CORE)

`GET /farms/{id}/risk?cropId=&season=` → `RiskAssessmentResponse { farmId, cropId, season, cropYear, ruleSet:"risk-rules-v1", productionRisk, marketRisk, generatedAt }`.
- `season` defaults to the farm's mapped season (§5.3).
- The ML-derived factors use the supply response for the farm's district, crop and season at the **latest estimable crop year** (echoed as `cropYear`).
- The same in-scope checks as §10 apply: 422 `UNSUPPORTED_INPUT` otherwise.
- Each risk is `{ level, factors[], assessedFactors, unavailableFactors[], limitations[] }`.
- Each factor is `{ code, level, value, unit, threshold, source, dataClassification, observedOrForecastFor, reason }`.
- Levels (shared vocabulary): `LOW`, `MODERATE`, `HIGH`, `CRITICAL`, `UNAVAILABLE`.
- **Level = the worst assessed factor.** If every factor is unavailable, the level is `UNAVAILABLE`.

### 11.1 Production factors

| Code | Input | Rule |
|---|---|---|
| `HEAVY_RAINFALL` | Max daily forecast precipitation within 7 days | IMD categories: < 64.5 mm LOW; 64.5–115.5 HIGH ("heavy"); 115.6–204.4 CRITICAL ("very heavy"); ≥ 204.5 CRITICAL ("extremely heavy") |
| `TEMPERATURE_STRESS` | 7-day forecast min/max vs `crop_requirement` | Within optimal LOW; outside optimal but within absolute MODERATE; outside absolute on 1–2 days HIGH; ≥ 3 days CRITICAL. UNAVAILABLE if the requirement is missing |
| `YIELD_VARIABILITY` | ML `historicalYieldStats.coefficientOfVariation` | Product rule: < 0.15 LOW; 0.15–0.30 MODERATE; > 0.30 HIGH |
| `DOWNSIDE_FREQUENCY` | ML `downsideYearShare` | Product rule: < 0.2 LOW; 0.2–0.4 MODERATE; > 0.4 HIGH |
| `SOIL_PH` | Soil pH vs `crop_requirement` | OPTIMAL LOW; TOLERABLE MODERATE; OUTSIDE_ABSOLUTE HIGH; no soil UNAVAILABLE |

### 11.2 Market factors

Market factors are `UNAVAILABLE` until M4, with the limitation "Market data is not connected (milestone M4)". In M4, the factors are `PRICE_ANOMALY` (from the M4 anomaly method) and `SUPPLY_PRESSURE` (the supply estimate vs its 3-year mean).

Every factor threshold is echoed in the response (`threshold`) with its citation or with "product rule risk-rules-v1".

---

## 12. Provenance and classification rules

`Provenance` DTO (backend, shared): `{ source, dataClassification, retrievedAt?, generatedAt?, datasetVersion?, modelName?, modelVersion?, featureVersion?, dataThrough?, notes[] }`.

Optional fields are **null when unknown, never invented or defaulted**.

| Data | source | classification |
|---|---|---|
| User-entered soil | Soil `source` enum | The soil's own value (rule in §5.2) |
| Open-Meteo current | `OPEN_METEO` | `ESTIMATED` |
| Open-Meteo daily | `OPEN_METEO` | `FORECAST` |
| S01 reported history/actuals | `DES_S01_DATA_GOV_IN` | `OBSERVED` |
| ML supply served by MODEL | `ML_SERVICE` | `MODEL_PREDICTION` |
| ML supply served by BASELINE | `ML_SERVICE` | `ESTIMATED` |
| FAO EcoCrop limits | `FAO_ECOCROP` | (reference, not a measurement; no classification) |
| Risk/evidence levels | `BACKEND_RULES` + rule-set version | Derived. Each factor carries its input's classification |
| (M4) Agmarknet prices | `AGMARKNET_DATA_GOV_IN` | `OBSERVED` |

The frontend shows the classification on every value, using the labels from `frontend/CLAUDE.md` §23. Production builds contain no mock data.

---

## 13. Error model (final)

The body is unchanged: `{ timestamp, status, code, message, path, details[{field, message}] }`. There are no stack traces. The message is user-safe.

| HTTP | code | Use |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean/param validation; `details[].field` |
| 400 | `MALFORMED_REQUEST` | Unreadable JSON, bad enum |
| 400 | `INVALID_PARAMETER` | Type mismatch (e.g. UUID) |
| 400 | `INCONSISTENT_SOIL_PROVENANCE` | Soil rule |
| 401 | `UNAUTHORIZED` | Missing, invalid or expired token; disabled user |
| 401 | `INVALID_CREDENTIALS` | Login failure |
| 403 | `FORBIDDEN` | Role lacks the capability |
| 404 | `NOT_FOUND` | Unknown route |
| 404 | `FARM_NOT_FOUND`, `USER_NOT_FOUND`, `DISTRICT_NOT_FOUND` | Missing **or not visible** to the caller |
| 405 | `METHOD_NOT_ALLOWED` | |
| 409 | `EMAIL_ALREADY_REGISTERED` | |
| 409 | `CONFLICT` | Last-admin rule, owner already set, assignment for the wrong role |
| 422 | `UNSUPPORTED_INPUT` | Outside the D2 scope, series missing, farm has no in-scope district, season OTHER |
| 422 | `INSUFFICIENT_DATA` | ML `INSUFFICIENT_HISTORY` / `AREA_UNAVAILABLE` |
| 502 | `UPSTREAM_INVALID_RESPONSE` | Weather provider returned bad data |
| 502 | `ML_INVALID_RESPONSE` | ML returned bad or contract-breaking data |
| 503 | `UPSTREAM_UNAVAILABLE` / `UPSTREAM_RATE_LIMITED` | Weather provider down or rate-limited |
| 503 | `ML_UNAVAILABLE` | ML unreachable or timed out |
| 503 | `ML_PREDICTION_UNAVAILABLE` | ML up but no model loaded |
| 500 | `INTERNAL_ERROR` | Unexpected |

**Removed codes:** `WEATHER_UNAVAILABLE` (use the `UPSTREAM_*` codes), `ML_SERVICE_UNAVAILABLE` (use `ML_UNAVAILABLE`), `ML_SERVICE_ERROR` (use `ML_INVALID_RESPONSE`), `PREDICTION_UNAVAILABLE` (the endpoints were removed).

The frontend `describeError` MUST give each 422, 502 and 503 code a specific message.

---

## 14. Configuration

**Backend** (env vars; `backend/application-local.properties` is a git-ignored local fallback loaded through `spring.config.import`):

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | none, **required** (≥ 32 bytes) | |
| `JWT_EXPIRATION` | `PT1H` | |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5433/agri` / `agri` / `agri` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | CORS methods become GET, POST, PUT, PATCH |
| `ML_SERVICE_BASE_URL` | `http://localhost:8000` | |
| `ML_SERVICE_CONNECT_TIMEOUT` / `ML_SERVICE_READ_TIMEOUT` | `2s` / `10s` | |
| `WEATHER_BASE_URL` | `https://api.open-meteo.com` | |
| `WEATHER_CONNECT_TIMEOUT` / `WEATHER_READ_TIMEOUT` | `2s` / `5s` | |
| `WEATHER_CACHE_TTL` | `PT30M` | |
| `REFERENCE_SYNC_ON_STARTUP` | `true` | Failure is logged; the app still starts |
| `BOOTSTRAP_ADMIN_EMAIL` | unset | If set **and** no ADMIN exists, the registered user with this email is promoted at startup (audited `ADMIN_BOOTSTRAPPED`). No password is ever put in config |
| `DATA_GOV_IN_API_KEY` | unset | **M4 only** |

- Health order: `management.endpoint.health.status.order=DOWN,DEGRADED,UP`.
- ML and weather health indicators report `DEGRADED`, never `DOWN`.

**ML** (`AGRI_ML_*`): `AGRI_ML_SUPPLY_MODEL_DIR`, `AGRI_ML_ENVIRONMENT`. The download script needs `DATA_GOV_IN_API_KEY`.

**Frontend:** `VITE_API_BASE_URL` only. `VITE_INTELLIGENCE_SOURCE` is **deleted**.

---

## 15. Frontend routes (final) and contract changes

| Route | Roles | Content (real endpoints only) |
|---|---|---|
| `/login`, `/register` | public | Exist |
| `/dashboard` | all | FARMER/FPO: own farms list + selected farm's weather + risk summary. OFFICER/ADMIN: their districts' farm counts. **The mock provider is deleted** |
| `/farms`, `/farms/new`, `/farms/:id`, `/farms/:id/edit` | FARMER, FPO | Wizard gains a **district select** (from `/reference/scope`, UP only, optional). Profile tabs: Overview, Weather, Crop evidence, Risk |
| `/supply` | all | Replaces `/supply-demand`. Selectors from scope (district, crop, season, estimable year). Chart: reported history (OBSERVED) + estimate with interval (MODEL_PREDICTION/ESTIMATED) + baseline + reported actual if present; evaluation, provenance and limitations panel |
| `/regional`, `/regional/farms/:id` | FPO, OFFICER, ADMIN | District list → farm list → read-only farm detail with weather/evidence/risk |
| `/admin/users`, `/admin/system`, `/admin/audit` | ADMIN | User table with role/enable/district editing; system and ML health with model/dataset metadata; audit log; reference sync button; unowned farms |

**Removed from navigation** until built: `/crops` (crop evidence lives in the farm profile), `/market` (M4), `/scenarios`, `/crop-doctor`, `/coordination`, `/alerts`, `/data-operations` (replaced by `/admin/system`). Navigation is filtered by the user's role.

**Resolved frontend↔backend mismatches:**

| Topic | Frontend today | Final |
|---|---|---|
| Intelligence paths | `/intelligence/supply`, `/demand`, `/risk`, `/supply-demand` | `/intelligence/supply`, `/farms/{id}/crop-evidence`, `/farms/{id}/risk`. Demand and supply-demand are gone |
| Params | `regionId`, `periodId=next-3m` | `districtId`, `cropId`, `season`, `cropYear` |
| Scope options | Hard-coded mh/ka, onion/tomato/soybean (`options.ts`) | Loaded from `/api/reference/scope` |
| Supply fields | `predictedSupply`, `crop`, top-level `modelVersion` | `SupplyEstimateResponse` (§8.2) |
| Provenance | `retrievedAt` only | §12 DTO (`generatedAt`, `retrievedAt`, `dataThrough`, versions, `notes`) |
| Weather | single `provenance`, `rainfallMm`, `observedAt` | §7.2 split provenance and renames |
| Weather for a farm | `/weather/farms/{id}` | `/farms/{id}/weather` |
| Unavailable handling | 501 `NOT_AVAILABLE` (mock), 503 `PREDICTION_UNAVAILABLE` | §13 codes, with a specific message for each |
| Risk vocabulary | `low`/`moderate`/`high`/`critical` | API uses upper-case plus `UNAVAILABLE`; the UI maps it to its labels |
| `DataClassification` | the frontend type in `features/intelligence/shared/types.ts` | Exactly the D4 enum |
| User | `UserResponse {id, fullName, email, role}` | adds `enabled` and `assignedDistrictIds[]` (§6.1) |
| Role awareness | none in navigation | Navigation and routes are gated by `/me.role`. **The UI gate is cosmetic only**; the backend enforces access |

---

## 16. Testing strategy

**Backend** (JUnit + Testcontainers PostGIS; no H2, no mocked database):
- **Migrations:** a clean database migrates V1→V7 and the context starts. A migration test with legacy ownerless rows proves nothing is deleted.
- **Auth:** register, login, me, invalid/expired/wrong-signature token, disabled user, audit rows written.
- **RBAC:** a matrix test covering every endpoint group × 4 roles (§4.2), plus cross-user farm access (404), officer outside their districts (404), last-admin rule (409), and ignored client-sent `role`/`ownerId`.
- **Farms/soil:** the existing 17 tests, plus `districtId` validation and `intelligenceSupported`.
- **Reference:** sync upsert (ML served by a mock HTTP server), empty-scope behaviour, estimable years.
- **Weather:** `OpenMeteoWeatherProvider` against a mock HTTP server:
  - success with nulls preserved;
  - timeout → 503; 429 → 503 rate-limited; 5xx → 502; malformed JSON → 502; array mismatch → 502;
  - split provenance; cache hit keeps `retrievedAt`; authorization.
- **ML client contract** (against a mock HTTP server; reuse the existing `MlClientTest` approach and add no new test library unless it's needed): 200 mapping of every field; each §9.3 error code mapping; timeout; connection refused; non-JSON; missing required fields; business-rule violations (servedMethod/classification mismatch, negative production, lower > upper, wrong unit).
- **Intelligence:** supply scope pre-check (never calls ML for an out-of-scope request); crop evidence tiers and order (fixture requirement and soil); partial evidence when ML or weather is down; risk factor thresholds at each boundary; market `UNAVAILABLE`.
- **Admin:** users paging/filter, role/enable changes, district assignment, system health with ML/weather up and down, audit query, legacy farm assignment.
- **Request id:** echoed in the response header and present in audit rows.

**ML** (pytest): schemas, error codes, the scope endpoint, a leakage test, acceptance-rule tests, and a real-artifact test once an artifact exists. `ruff` must be clean.

**Contract test:** `backend/src/test/resources/contracts/ml/*.json` holds golden ML responses. The same files are validated by an ML pytest against `SupplyResponse`, so both sides test the same JSON.

**Frontend** (Vitest + @testing-library/react + jsdom, added as dev dependencies; `npm test`):
- `describeError`: a specific message for every §13 code, plus `MALFORMED_RESPONSE` and network failure.
- Zod schemas: accept a golden body and reject a broken one for every response type the frontend consumes.
- Navigation and route guards per role (§4.2, §15), including the Forbidden state for a URL entered by hand.
- 401 handling: the token is cleared, the query cache is cleared, and the user is redirected to `/login`.
- Provenance rendering for each classification. A null `confidence` or interval renders nothing (no placeholder).
- `/supply`: OBSERVED history and the estimate render differently; the interval appears only when present; `servedMethod=BASELINE` is disclosed; 422 and 503 show unavailable states with no number.
- Risk: every level including `UNAVAILABLE`; production and market risk render as separate sections.
- Farm wizard: client validation, a server `VALIDATION_ERROR` mapped to fields, and submit disabled while pending.

**End to end (manual, documented):** frontend → backend → real ML with a real artifact → Open-Meteo.

---

## 17. Obsolete paths to remove

| Where | What | Why |
|---|---|---|
| backend `intelligence/controller` | `/demand-forecast`, `/supply-demand`, `/crop-recommendations`, `/agricultural-risk` (permanent 503) and `/supply-forecast` (old contract) | D5, D10, D11 |
| backend `ml/` | `Map`-based `predictSupply`, `dto/SupplyPredictionResponse`, `ML_SERVICE_*` codes | §9.4 |
| backend `intelligence/service` | Hard-coded "The model reports no uncertainty" limitation | False; the interval is now served |
| backend `weather` | `/api/weather/farms/{farmId}`, single-provenance DTO, `WEATHER_UNAVAILABLE`, "no provider" path | §6.2, §7 (`MockWeatherProvider` is already deleted) |
| backend `common` | `DataClassification.REGIONAL_ESTIMATE` | D4 |
| ML `api/app.py` | Old request/response models, `{detail}` errors, the `history[]` request field, the `loadedModels` health field | §9.4 |
| ML docs | Stale `ML_STATE.md`/`README.md`/`PROGRESS_REPORT.md`; duplicate catalogue `docs/dataset-catalogue.md` (Maharashtra, conflicts with D2); `docs/ml-contracts/README.md` "no contracts" | Replace with `docs/ml-contracts/supply.md` (= §9) |
| frontend | `features/dashboard/api.ts` mock + `types.ts` mock shapes, `features/intelligence/supply-demand/mock.ts`, `options.ts` hard-coded scope, `VITE_INTELLIGENCE_SOURCE` + `intelligenceSource`, the demand/risk/crop-recommendation clients with draft shapes, the "Synthetic data" top-bar flag, placeholder nav entries | D11, §15 |
| docs | `PROJECT_STATE.md` (update after each milestone), `backend/docs/intelligence-api.md` (rewrite to §6–§11), `frontend/docs/intelligence-api.md` | Stale |

---

## 18. Definition of Done (whole project, CORE)

1. Clean database → Flyway V1–V7 → the app starts with `JWT_SECRET` set. Health is UP; with ML or Open-Meteo down, it is DEGRADED.
2. Every CORE endpoint in §6 is implemented with the exact shapes. **No CORE endpoint returns a placeholder or permanent 503.**
3. The RBAC matrix (§4.2) is enforced and tested.
4. A real ML artifact is trained on real S01 UP data, and its model card exists. The backend → ML supply call works end to end against it.
5. Real Open-Meteo weather works, with split provenance.
6. Crop evidence and risk work for an in-scope farm, and degrade honestly (`UNAVAILABLE`) when a source is down.
7. Every intelligence value carries provenance, with the classifications in §12.
8. No mock or synthetic production path remains (§17). The frontend builds without `VITE_INTELLIGENCE_SOURCE`.
9. All backend, ML and frontend tests pass (`npm test`), ruff and ESLint are clean, and `tsc` compiles.
10. The docs match reality (`PROJECT_STATE.md`, READMEs, contract docs), and no secrets are committed.

STRETCH milestones have their own Done: the same bar, applied to their endpoints.

---

## 19. Implementation order

CORE work is split into phases **P0–P6**. The names **M4** and **M5** are kept only for the STRETCH milestones, because the ML code already calls market "milestone M4".

| Phase | Work | Depends on |
|---|---|---|
| **P0 Contract freeze** | Commit this spec. ML writes `docs/ml-contracts/supply.md` = §9 | — |
| **P1 ML service** | Rebuild `app.py` on the refactored schemas + the error model + `/v1/reference/scope` + health `models[]`; fix the failing test and lint; make Windows reproducible. **Needs the data.gov.in key (B1):** download S01 UP → train → model card | P0 |
| **P2 Backend foundation** | D4 rename; §13 error codes; request-id filter; V4 reference + sync; V5 farm district + assignments; V6 audit + auth auditing; admin bootstrap; typed `MlClient` (§9) with contract tests against golden JSON | P0 (the ML HTTP tests use a mock HTTP server, so P1 isn't needed yet) |
| **P3 Backend intelligence** | Open-Meteo provider (§7); supply (§8); V7 EcoCrop requirement with cited values (B2); crop evidence (§10); risk (§11); delete obsolete backend paths (§17) | P2 |
| **P4 Backend roles** | Regional (§6.6) and admin (§6.7) APIs; the RBAC matrix test | P2 (can run in parallel with P3) |
| **P5 Frontend** | Remove mocks; reference-driven selectors; `/supply`; farm tabs; regional; admin; role-gated navigation; `describeError` codes | P3, P4 (typed clients can be written from this spec earlier) |
| **P6 End to end** | Real artifact + backend + frontend + Open-Meteo; update docs | P1, P3, P5 |
| M4 (STRETCH) market | data.gov.in daily prices ingestion + persistence + anomalies + market risk | P6 |
| M5 (STRETCH) demand | Arrivals proxy + gap | M4 |

---

## 20. Genuine blockers

| # | Blocker | Blocks | Owner |
|---|---|---|---|
| B1 | **Real S01 data + trained artifact.** Needs a registered `DATA_GOV_IN_API_KEY` (the public sample key is rate-limited) and a training run. Without it, supply returns 503 `ML_PREDICTION_UNAVAILABLE` (an honest state, not a placeholder), and crop evidence and risk show production evidence as UNAVAILABLE | DoD 4, P6 | ML teammate / project owner (register the key) |
| B2 | **FAO EcoCrop values must be transcribed by a person** for potato, wheat, onion and maize, with URLs. This is a data task, not a design gap; until it's done, the soil and temperature factors are UNAVAILABLE | Full crop evidence/risk | Backend implementer |

Nothing else blocks implementation. Open-Meteo needs no key; its free tier is for non-commercial use, which is acceptable for the hackathon MVP and noted as a limitation.
