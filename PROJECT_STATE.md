# Agricultural Intelligence — Project State

**Audit date:** 2026-09-28. Branch `main` at `3e652f0`. The working tree has **uncommitted** changes, and this document describes the working tree, not the last commit. The main uncommitted change: `MockWeatherProvider` was deleted, so weather now returns 503. `ml-service/`, the `intelligence` package and several docs are also untracked.

This file is the handoff source of truth. It was written from the source code, migrations, tests, the local dev database and the build and test runs listed in §11. Existing docs were not taken on trust; the stale ones are listed in §12.

Status vocabulary:

| Label | Meaning |
|---|---|
| **IMPLEMENTED** | Works end to end against its real source |
| **PARTIAL** | Some of it works; the gap is stated |
| **NO SOURCE** | The endpoint and provider boundary exist, but no data source is connected, so it returns 503 |
| **CONTRACT ONLY** | The route exists, validates its input and checks authorization, then returns 503 `PREDICTION_UNAVAILABLE`. No success body is served |
| **WAITING FOR ML** | Wired to the ML service; returns data only when an ML service answers. None exists today |
| **MOCK / SYNTHETIC** | Generated data, labelled `SYNTHETIC` |
| **PLANNED** | Described in docs or CLAUDE.md only. No code |
| **KNOWN ISSUE** | A verified defect or inconsistency |
| **UNKNOWN / NEEDS VERIFICATION** | Could not be confirmed in this audit |

---

## 1. Project Overview

**Product.** An agricultural decision-intelligence platform for Indian farming. The intended flow runs from a farm through its soil, weather and history, to supply, demand and risk intelligence, and ends in an explainable recommendation.

**Problem it addresses.** Crop and market decisions are made without visibility of the regional supply-demand balance, market pressure or production risk. The platform is meant to combine those signals honestly, keeping every value's provenance, instead of producing a single opaque score.

**MVP scope (planned).** One Indian state and 2–3 crops. The ML dataset audit proposes Maharashtra, with onion, tomato and soybean; nothing has agreed to this yet. Real data is used where it exists, and anything synthetic is labelled.

**Current stage.** This is an early foundation.

| Capability | State |
|---|---|
| Accounts: register, login, current user | IMPLEMENTED |
| Farm management with location and optional soil profile | IMPLEMENTED |
| Weather | NO SOURCE. The endpoint and provider boundary exist, but every request returns 503 |
| Supply forecast | WAITING FOR ML. The backend is wired to call ML, but no ML service exists |
| Demand, supply-demand gap, crop recommendations, agricultural risk | CONTRACT ONLY (503) |
| Dashboard and Supply & Demand page | Frontend MOCK / SYNTHETIC data, labelled |
| Market, scenarios, crop doctor, regional coordination, alerts, data operations | PLANNED. Placeholder pages only |
| ML service, including any model, dataset, training or API | PLANNED. Only a dataset audit document exists |
| Decision engine, LLM explanation / advisory | PLANNED. No code |

---

## 2. System Architecture

```
Frontend (React SPA, :5173)
    │  HTTP/JSON, Bearer JWT       ← the frontend talks only to Spring Boot
    ↓
Spring Boot Backend (:8080)  ── PostgreSQL 17 + PostGIS (:5433 local)
    │  HTTP/JSON via MlClient (RestClient); the only caller of ML
    ↓
ML Service (FastAPI, expected at :8000)   ← DOES NOT EXIST. Nothing listens on :8000
```

| Layer | What it owns today |
|---|---|
| **Frontend** | Routing, UI, forms (Zod), server state (TanStack Query), a single HTTP client (`lib/api/client.ts`), provenance badges, loading/error/empty states. It also holds two labelled synthetic mock providers: the dashboard summary and the supply-demand series |
| **Backend** | Authentication and JWT, users and roles, farms, locations and soil (owner-scoped), soil provenance validation, weather orchestration (no provider), intelligence orchestration, the single ML HTTP client, the error contract, Flyway schema |
| **ML service** | Nothing. `ml-service/` contains only `docs/dataset-catalogue.md` |
| **LLM** | **None.** No LLM, Spring AI or advisory code exists anywhere |

**Architectural principle:** *ML predicts. Spring Boot decides. LLM explains.* Today only the "Spring Boot decides" part exists in code. `IntelligenceService` refuses to invent predictions, and `WeatherService` refuses `SYNTHETIC` provider data. There is no ML and no LLM.

**Boundaries enforced in code:**
- The frontend never calls ML. `ML_SERVICE_BASE_URL` is backend-only config.
- `ml/MlClient` is the only backend class that makes HTTP calls to ML. ML failures are logged and turned into client-safe 503/502 errors.
- The backend never generates weather or predictions. With no source, it returns 503.
- Frontend mock data never replaces a failed real request.

---

## 3. Repository Structure

```
AgriculturalIntelligence/
├── README.md            overview + quick start
├── PROJECT_STATE.md     this file
├── backend/             Spring Boot modular monolith
├── frontend/            React + Vite SPA
└── ml-service/          docs only (untracked)
```

| Folder | Purpose | Status | Important contents |
|---|---|---|---|
| `backend/` | Business API, auth, persistence, orchestration | Active. Builds, 63/63 tests pass | `src/main/java/com/argiintelligence/backend/{common,configuration,auth,user,farm,weather,ml,intelligence}`, `src/main/resources/db/migration/V1–V3`, `docs/{auth,farm,intelligence}-api.md`, `compose.yaml`, `CLAUDE.md`, `README.md` |
| `frontend/` | User interface | Active. Build and lint pass | `src/app` (router, navigation), `src/components` (design system), `src/features/{auth,farms,dashboard,supply-demand,intelligence}`, `src/lib/api/client.ts`, `docs/intelligence-api.md`, `CLAUDE.md`, `README.md` |
| `ml-service/` | Future Python/FastAPI ML service | **Not started.** No code, config, datasets or models | `docs/dataset-catalogue.md` |

The package name `argiintelligence` is misspelled deliberately and must be kept.

Git: branches `main`, `ml-branch` and `ankit-branch` exist. `origin/ml-branch` and `origin/ankit-branch` both sit at `879ac11`, which is one commit behind `main`, and contain no ML code.

---

## 4. Frontend Current State

**Stack** (from `package.json`):
- Core: React ^19.2.8, react-router ^8.4.0, Vite ^8.3.0, TypeScript ~6.0.2.
- Data and forms: TanStack Query ^5.103.2, react-hook-form ^7.88.0 + zod ^4.6.5.
- UI: Tailwind ^4.3.3 with shadcn/Radix, lucide-react.
- Maps and charts: maplibre-gl ^6.11.1, recharts ^3.10.1.
- Linting: ESLint ^10.10.0.
- There is **no test runner**.

**Environment** (`.env.example`):
- `VITE_API_BASE_URL`, default `http://localhost:8080/api`.
- `VITE_INTELLIGENCE_SOURCE`: `mock` (default) or `api`.

### Pages / routes (`src/app/router.tsx`, `src/app/navigation.ts`)

| Route | Guard | Content | Status |
|---|---|---|---|
| `/login`, `/register` | `PublicOnly` (signed-in users redirected) | Auth forms | IMPLEMENTED |
| `/` | `RequireAuth` | Redirects to `/dashboard` | IMPLEMENTED |
| `/dashboard` | `RequireAuth` | Mock summary + supply-vs-demand chart | MOCK / SYNTHETIC |
| `/farms`, `/farms/new`, `/farms/:id`, `/farms/:id/edit` | `RequireAuth` | List, create/edit wizard (map location, farm info, optional soil, review), profile | IMPLEMENTED (real backend) |
| `/supply-demand` | `RequireAuth` | Supply & Demand page | MOCK by default; the `api` mode gets 503 |
| `/crops`, `/market`, `/scenarios`, `/crop-doctor`, `/coordination`, `/alerts`, `/data-operations` | `RequireAuth` | `ModulePage` placeholder ("scheduled for phase N") | NOT IMPLEMENTED |
| `*` | `RequireAuth` | Not-found page | IMPLEMENTED |

The frontend has no role-based UI gating. The only place the role appears is a label in `AppShell`.

### Features

**Auth:** `AuthProvider`, route guards, login and register.
- The token is stored in `localStorage` under `agri.accessToken`.
- A 401 on a request that carried a token ends the session and clears the query cache.

**Farms:** full CRUD against the backend, except delete.
- The location is picked on a MapLibre map.
- Soil is optional and carries its provenance fields.

**Intelligence API layer** (`features/intelligence/`): typed clients and hooks for weather, supply-forecast, demand-forecast, supply-demand, crop-recommendations and agricultural-risk.
- Responses with an agreed shape are validated with Zod. A body that doesn't match becomes `502 MALFORMED_RESPONSE`.

### API integration: backend endpoints actually called by rendered UI

| Endpoint | Called from | When |
|---|---|---|
| `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me` | auth feature | always |
| `GET /api/farms`, `POST /api/farms`, `GET /api/farms/{id}`, `PUT /api/farms/{id}` | farms feature | always |
| `GET /api/intelligence/supply-demand` | `/supply-demand` page and the dashboard chart | only when `VITE_INTELLIGENCE_SOURCE=api`; the default `mock` makes no call |

The following client functions and hooks exist, but **no component uses them**:
- `getWeather` / `useWeather`
- `getSupplyForecast` / `useSupplyForecast`
- `getDemandForecast` / `useDemandForecast`
- `getCropRecommendation` / `useCropRecommendation`
- `getAgriculturalRisk` / `useAgriculturalRisk`

### Intelligence UI

| Area | Status | Notes |
|---|---|---|
| Supply & Demand | **MOCK** (default) / CONTRACT ONLY (`api` mode) | Page with summary strip, trend chart, gap panel, risks and a provenance panel. Mock data is labelled SYNTHETIC with a "Prototype data" callout. The response schema is a frontend **draft** that differs from the backend's planned body (§7) |
| Weather | **NOT IMPLEMENTED** (UI) | Client, schema and hook exist; no page. The backend returns 503 |
| Crop Recommendation | **NOT IMPLEMENTED** (UI) / CONTRACT ONLY | `/crops` is a placeholder. The client type is PROVISIONAL and unvalidated |
| Risk | **NOT IMPLEMENTED** (UI) / CONTRACT ONLY | No page. The client type is PROVISIONAL and unvalidated |
| Market | **NOT IMPLEMENTED** | `/market` is a placeholder. There is no client module and no backend contract |
| Dashboard intelligence | **MOCK / SYNTHETIC** | Signal strip, district map, alerts, data freshness and crop preview all come from `features/dashboard/api.ts`, with every value classified SYNTHETIC. The supply-vs-demand chart uses the supply-demand hook, so it is mock by default. The top bar always flags the dashboard as synthetic |

### Mock / synthetic data

- **`frontend/src/features/dashboard/api.ts`** holds the whole dashboard summary.
  - It includes invented farms ("Niphad plot A", "Ausa block 7"). These are **not** the user's real farms.
  - The rest is invented too: district gaps, alerts, source health, crop comparison, and supply/demand/market/weather/disease signals with confidences.
  - Every item is classified `SYNTHETIC`, with source "Prototype dataset (synthetic)".
- **`frontend/src/features/intelligence/supply-demand/mock.ts`** generates deterministic seasonal curves plus narrative risk factors.
  - Only region `mh` has series; other regions show the empty state.
  - Its provenance is `SYNTHETIC`, with `modelVersion: null`, `confidence: null` and stated limitations.
- **`frontend/src/features/intelligence/supply-demand/options.ts`** is a static list of regions (`mh`, `ka`), crops (`onion`, `tomato`, `soybean`) and horizons (3, 6). It is not synthetic data, but it isn't backed by any reference data.
- `?mockError` in the URL forces both mocks to fail, to demonstrate error states.

### Build / tests (verified 2026-09-28)

| Check | Result |
|---|---|
| `npm run build` (`tsc -b` + `vite build`) | **Pass.** Warning: the main JS chunk is 2,145 kB (618 kB gzip), above the 500 kB advisory limit |
| `npm run lint` | **Pass**, no findings |
| Automated tests | None exist |
| Browser verification | **Not performed in this audit** |

---

## 5. Backend Current State

### Technology stack (from `pom.xml` and `application.properties`)

- Java 21, Spring Boot parent **4.1.1**, Maven wrapper (`mvnw`).
- Starters: `webmvc`, `data-jpa`, `validation`, `actuator`, `security`, `security-oauth2-resource-server` (Nimbus JWT, HS256), `flyway` + `flyway-database-postgresql`.
- Libraries: `postgresql` driver, Lombok.
- Tests: the matching `*-test` starters, `spring-boot-testcontainers`, `testcontainers-postgresql`. The run used JUnit Platform 6.0.3.
- Transitive versions (Hibernate, Jackson) are managed by the Boot parent. The docs say Hibernate 7 and Jackson 3; this audit did not check that independently.
- **Not present:** Hibernate Spatial, Redis, Kafka, Spring AI or any LLM client, any external weather or market client.

**Configuration:**
- A single `application.properties`; there are no Spring profiles.
- `ddl-auto=validate`, `open-in-view=false`, and actuator exposes only `health`.
- Every value can be overridden by an environment variable:

| Env var | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/agri` |
| `DB_USERNAME` / `DB_PASSWORD` | local dev defaults (see `compose.yaml`) |
| `JWT_SECRET` | **required**, no default, at least 32 bytes |
| `JWT_EXPIRATION` | `PT1H` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
| `ML_SERVICE_BASE_URL` | `http://localhost:8000` |
| `ML_SERVICE_CONNECT_TIMEOUT` / `ML_SERVICE_READ_TIMEOUT` | `2s` / `10s` |

### Authentication — IMPLEMENTED

**Endpoints** (`auth/controller/AuthController`):
- `POST /api/auth/register` returns 201 `UserResponse`.
  - The role is always `FARMER`; the request has no role field.
  - 409 `EMAIL_ALREADY_REGISTERED`, including when two registrations race.
- `POST /api/auth/login` returns `{accessToken, tokenType:"Bearer", expiresIn, user}`.
  - 401 `INVALID_CREDENTIALS` for an unknown email, a wrong password or a disabled account alike.
- `GET /api/auth/me` returns `UserResponse {id, fullName, email, role}`.

**JWT** (`JwtService`, `JwtProperties`):
- HS256, and the subject is the user id. The token carries no role claim.
- Lifetime is `JWT_EXPIRATION`.
- The app refuses to start if `JWT_SECRET` is missing or shorter than 32 bytes.
- There are no refresh tokens, password reset or email verification.

**Per-request behaviour** (`JwtUserAuthenticationConverter`):
- The user is reloaded from the database on every request.
- A disabled or missing user gets 401, and role changes apply immediately.

**Passwords:** BCrypt, 8–72 characters. Emails are normalised to lower case, and the database enforces this with a CHECK.

**Other security settings:**
- Stateless: no sessions, CSRF disabled, no HTTP Basic or form login.
- 401 and 403 use the JSON `ApiError` shape, and 401 also sets `WWW-Authenticate: Bearer` (`ApiErrorSecurityHandler`).

**CORS:** applies to `/api/**`.
- Origins come from `CORS_ALLOWED_ORIGINS`.
- Methods: `GET`, `POST`, `PUT` only.

### Farm — IMPLEMENTED

**Entities:**
- `Farm`: name, area, `areaUnit` (ACRE | HECTARE), `irrigationType` (RAIN_FED | DRIP | SPRINKLER | CANAL | BOREWELL | OTHER), free-text `currentCrop` and `previousCrop`, `season` (KHARIF | RABI | ZAID | OTHER), timestamps.
- `FarmLocation`: lat/lon, state, district, taluk, addressLabel.
- `SoilProfile`: optional.

**Relationships:**
- `Farm → FarmLocation`: one-to-one, required, cascade PERSIST.
- `Farm → SoilProfile`: one-to-one, optional, `orphanRemoval`.
- `Farm → User` (owner): many-to-one, LAZY, `updatable=false`.

**APIs:**

| Endpoint | Behaviour |
|---|---|
| `POST /api/farms` | 201 plus a `Location` header |
| `GET /api/farms` | Newest first, **unpaged** |
| `GET /api/farms/{id}` | |
| `PUT /api/farms/{id}` | Full replacement; a null `soilProfile` deletes it |

There is **no DELETE**.

**Ownership:**
- The owner comes from the token; a client-supplied owner is ignored.
- Every query is scoped with `findWithDetailsByIdAndOwnerId`. Another user's farm returns 404 `FARM_NOT_FOUND`, exactly like a missing one.
- `/api/farms/**` requires role FARMER or FPO. Other roles get 403.

### Soil — IMPLEMENTED (part of the farm aggregate; there is no separate `/api/soil`)

- Twelve nullable measurements (pH, EC, OC, N, P, K, S, Zn, Fe, Mn, Cu, B).
  - All must be ≥ 0, and pH must be 0–14.
  - Missing values stay null and are never filled in.
  - Documented units: N, P and K in kg/ha, micronutrients in ppm, EC in dS/m (`backend/docs/farm-api.md` and the frontend form). The code stores bare numbers and doesn't enforce units.
- Provenance fields:
  - `source`: SOIL_HEALTH_CARD | LAB_REPORT | MANUAL | REGIONAL_ESTIMATE | OTHER.
  - `dataClassification`: OBSERVED | ESTIMATED | SYNTHETIC. This is a farm-specific enum and is **not** the platform `DataClassification`.
  - `measuredAt`: may not be in the future.
  - `confidence`: 0..1.
- `FarmResponse.soilDataAvailable` tells whether a profile exists.
- `SoilDataSource.permits` enforces which classification each source may carry:

  | Source | Allowed classifications |
  |---|---|
  | `SOIL_HEALTH_CARD`, `LAB_REPORT` | `OBSERVED` |
  | `REGIONAL_ESTIMATE` | `ESTIMATED`, `SYNTHETIC` |
  | `MANUAL`, `OTHER` | any |

  Any other pair is rejected with 400 `INCONSISTENT_SOIL_PROVENANCE`. The database doesn't enforce this rule.

### Weather — NO SOURCE (503)

- `WeatherProvider` is an interface with **no implementation**. `MockWeatherProvider` was deleted in the uncommitted working tree.
- `WeatherService` resolves the provider through `ObjectProvider` and answers **503 `WEATHER_UNAVAILABLE`** in three cases:
  - no provider bean exists, which is **every production request today** ("No weather data source is configured");
  - the provider throws ("Weather data is currently unavailable");
  - the provider returns `SYNTHETIC` data, which is refused on purpose and logged (same message as above).
- There are no weather entities or tables, and nothing is persisted.
- **Data today:** none. It is neither real nor mock.

**Endpoints:**

| Endpoint | Parameters | Notes |
|---|---|---|
| `GET /api/weather` | `latitude`, `longitude` (required); `days` 1–14, default 7 | |
| `GET /api/weather/farms/{farmId}` | `days` | Checks farm ownership first (404), then availability |

**Response** (served only in tests, through a mocked provider):

```
WeatherResponse {
  farmId, latitude, longitude,
  provenance { source, dataClassification, retrievedAt, confidence },
  current { observedAt, temperatureC, relativeHumidityPct, rainfallMm, windSpeedKmh },
  daily[] { date, minTemperatureC, maxTemperatureC, rainfallMm, rainProbabilityPct, relativeHumidityPct }
}
```

**KNOWN ISSUE:** `WeatherProvider.Report` carries one `dataClassification` for both `current` (observed) and `daily` (forecast).

### Intelligence (`intelligence/controller/IntelligenceController`, base `/api/intelligence`)

Shared rules:
- `regionId` and `cropId` must match `[a-z0-9][a-z0-9-]{0,49}`. There is no reference table, so any well-formed id is accepted.
- `horizonMonths` must be 1–12 (default 3).
- **Authorization:** any authenticated user, whatever the role (`anyRequest().authenticated()`).
- Errors:
  - 400 `VALIDATION_ERROR` / `INVALID_PARAMETER`
  - 401 `UNAUTHORIZED`
  - 503 `PREDICTION_UNAVAILABLE`, `ML_SERVICE_UNAVAILABLE`
  - 502 `ML_SERVICE_ERROR`

**`GET /supply-forecast`**
- **Status:** WAITING FOR ML.
- **Purpose:** predicted supply for a region and crop.
- **Request:** `regionId`, `cropId`, `horizonMonths`.
- **Response:** `SupplyForecastResponse {regionId, cropId, horizonMonths, predictedSupply, unit, forecastPeriod, provenance: IntelligenceProvenance, modelProvenance}`.
- **Data source and ML dependency:** calls ML `POST /v1/predict/supply` on every request.
  - With no ML service running, it returns 503 `ML_SERVICE_UNAVAILABLE`.
  - It is labelled `MODEL_PREDICTION` only when ML answers, with `confidence: null` and a limitation note.

**`GET /demand-forecast`**
- **Status:** CONTRACT ONLY.
- **Purpose:** demand (proxy) forecast.
- **Request:** same parameters as supply-forecast.
- **Response:** always 503 `PREDICTION_UNAVAILABLE`.
- **Data source and ML dependency:** none. No ML endpoint has been agreed.

**`GET /supply-demand`**
- **Status:** CONTRACT ONLY.
- **Purpose:** the supply-demand gap.
- **Request:** same parameters as supply-forecast.
- **Response:** always 503.
- **Data source and ML dependency:** none. There is no production or arrival data in the backend.

**`GET /crop-recommendations`**
- **Status:** CONTRACT ONLY.
- **Purpose:** candidate-crop evidence for a farm.
- **Request:** `farmId` (UUID).
- **Response:** 404 if the caller doesn't own the farm, otherwise 503.
- **Authorization:** any role may call it, but ownership is checked, so ADMIN and OFFICER always get 404.
- **Data source and ML dependency:** none.

**`GET /agricultural-risk`**
- **Status:** CONTRACT ONLY.
- **Purpose:** risk by category.
- **Request:** `regionId`, `cropId`.
- **Response:** always 503.
- **Data source and ML dependency:** none.

`IntelligenceProvenance` has the shape `{source, dataClassification, generatedAt, modelVersion|null, confidence|null, limitations[]}`. The success bodies of the four CONTRACT ONLY endpoints are **PLANNED**; they are described in `backend/docs/intelligence-api.md`, and none is served.

### ML integration — PARTIAL (client only)

**Components:**
- `MlClient`: a Spring `RestClient` on the JDK `HttpClient`, built in `MlClientConfig`.
- `MlServiceProperties` binds `app.ml.*`.

**The one actual call:** `predictSupply(Map)` sends `POST /v1/predict/supply` with body `{regionId, cropId, horizonMonths}`.
- The body is an untyped map, and the contract is **PROVISIONAL**.
- The expected response is `SupplyPredictionResponse {modelVersion, prediction{value, unit, period}, provenance{datasetVersion, featureVersion, trainedAt}}`.
- `modelVersion`, `prediction.value` and `prediction.unit` are required. `period` and `provenance` may be null.

**Failure behaviour:**
- Connect failure or timeout → 503 `ML_SERVICE_UNAVAILABLE`.
- ML 4xx or 5xx, unreadable JSON, or missing required fields → 502 `ML_SERVICE_ERROR`.
- There are no retries and no circuit breaker. Raw errors are logged and never returned to the client.

**Current availability:** no ML service exists, and nothing listens on port 8000 on the dev machine.

### Database

- PostgreSQL 17 with PostGIS 3.5. Locally, `backend/compose.yaml` runs `postgis/postgis:17-3.5` on host port **5433**.
- Flyway migrations are listed in §10.
- Tests use Testcontainers with the same image and the real migrations.

### Tests — verified 2026-09-28

`mvnw test` on JDK 21 with Docker: **63 run, 0 failures, 0 errors, 0 skipped.**

| Class | Tests |
|---|---|
| `FarmControllerIntegrationTest` | 17 |
| `IntelligenceControllerIntegrationTest` | 11 (mocks `MlClient`) |
| `AuthControllerIntegrationTest` | 9 |
| `MlClientTest` | 9 (mocked HTTP) |
| `WeatherControllerIntegrationTest` | 7 (no provider → 503) |
| `GlobalExceptionHandlerTest` | 5 |
| `WeatherProviderIntegrationTest` | 4 (test-double provider; the synthetic report is refused) |
| `BackendApplicationTests` | 1 |

---

## 6. ML Service Current State

**Nothing is implemented.** `ml-service/` is untracked and contains exactly one file: `docs/dataset-catalogue.md`.

| Item | State |
|---|---|
| Python version / config (`pyproject`, `requirements`) | None |
| FastAPI app | None |
| ML libraries | None declared |
| Structure | `ml-service/docs/dataset-catalogue.md` only |
| APIs | **None.** `POST /v1/predict/supply` exists only as the backend's provisional expectation |
| Datasets | **None in the repo.** The catalogue evaluates external sources only |
| Models | None |
| Training | None |
| Evaluation | None; baselines and splits are proposed on paper only |
| Inference | None |
| MLflow | Not present or configured anywhere |

**What the dataset catalogue contains (planning only, PLANNED):**
- **Candidate sources:**
  - DES APY crop production (supply)
  - ICRISAT DLD (yield and suitability)
  - CEDA Agmarknet arrivals and prices (demand **proxy**)
  - NASA POWER and IMD (weather)
  - Kaggle Crop Recommendation, treated as synthetic or low-provenance
  - NSSO HCES, PlantVillage and PlantDoc, Sentinel/MODIS
- **Proposed MVP:** Maharashtra; onion, tomato and soybean.
- **Proposed first model:** a supply baseline on DES APY at district × crop × season × year granularity, in tonnes.
- **Stated caveats:**
  - There is no true demand data, only proxies.
  - The data ends around 2017–2021, so forecasts for 2026 extrapolate over a gap of several years.
  - Kaggle and ICRISAT need accounts.
- **Stale references:**
  - It cites `ML_CLAUDE.md` and `ML_STATE.md`, which do not exist.
  - It says `ml-branch` is identical to `main`, which is no longer true: `ml-branch` is one commit behind.
- **Classification mismatch:** it uses the class `ESTIMATED`, which the platform `DataClassification` doesn't have (see §8).

---

## 7. API Contracts

All frontend → backend errors use `{timestamp, status, code, message, path, details[]}`, and no stack traces are returned.

### Frontend → Backend

**Base URL:** `VITE_API_BASE_URL`, default `http://localhost:8080/api`.

**Auth:**

| Method and path | Request | Response | Auth | Status |
|---|---|---|---|---|
| POST `/api/auth/register` | `{fullName, email, password}` | 201 `UserResponse` | public | IMPLEMENTED, used |
| POST `/api/auth/login` | `{email, password}` | `{accessToken, tokenType, expiresIn, user}`; the frontend reads only `accessToken` | public | IMPLEMENTED, used |
| GET `/api/auth/me` | — | `{id, fullName, email, role}` | token | IMPLEMENTED, used |

**Farms:**

| Method and path | Request | Response | Auth | Status |
|---|---|---|---|---|
| GET `/api/farms` | — | `FarmResponse[]` | FARMER or FPO | IMPLEMENTED, used |
| POST `/api/farms` | `FarmRequest` | 201 `FarmResponse` | FARMER or FPO | IMPLEMENTED, used |
| GET `/api/farms/{id}` | — | `FarmResponse` | FARMER or FPO | IMPLEMENTED, used |
| PUT `/api/farms/{id}` | `FarmRequest` | `FarmResponse` | FARMER or FPO | IMPLEMENTED, used |

**Weather:**

| Method and path | Request | Response | Auth | Status |
|---|---|---|---|---|
| GET `/api/weather` | `latitude`, `longitude`, `days` | `WeatherResponse`; in practice 503 | token | NO SOURCE; frontend client exists but is unused |
| GET `/api/weather/farms/{id}` | `days` | `WeatherResponse`; in practice 503 | token | Same as above |

**Intelligence:**

| Method and path | Request | Response | Auth | Status |
|---|---|---|---|---|
| GET `/api/intelligence/supply-demand` | `regionId`, `cropId`, `horizonMonths` | 503. The frontend draft body is **PROVISIONAL** | token | CONTRACT ONLY; called only in `api` mode |
| GET `/api/intelligence/supply-forecast` | same | `SupplyForecastResponse` | token | WAITING FOR ML; frontend client unused |
| GET `/api/intelligence/demand-forecast` | same | 503. The frontend draft is PROVISIONAL | token | CONTRACT ONLY; client unused |
| GET `/api/intelligence/crop-recommendations` | `farmId` | 503. The frontend draft is PROVISIONAL | token | CONTRACT ONLY; client unused |
| GET `/api/intelligence/agricultural-risk` | `regionId`, `cropId` | 503. The frontend draft is PROVISIONAL | token | CONTRACT ONLY; client unused |

**Other:** `GET /actuator/health` is public and not used by the frontend.

Paths and query parameters now match between frontend and backend: the frontend already uses `/supply-forecast`, `horizonMonths` and so on. **The response bodies do not match yet:**

| Endpoint | Frontend draft | Backend (planned or actual) |
|---|---|---|
| supply-demand | `region`/`crop`/`period` as `{id,label}`; points `{period, supply, demand}`; `summary {currentSupply, …, gapPercentage, gapState: surplus\|balanced\|shortage, gapTrend}`; `risks[]`; one `provenance` + `historicalClassification` | Planned: `regionId`, `cropId`, `horizonMonths`, `unit`; points `{month, supply, demand}`; `gap {supplyDemandGap, gapState: SURPLUS\|BALANCED\|SHORTAGE}`; separate `historicalProvenance` / `forecastProvenance`; no embedded risks, labels or trend |
| crop-recommendations | `candidates[] {crop, suitability, expectedYield, unit, confidence}` | Planned: `candidates[] {cropId, suitability, factors[{name, value, effect}]}`; no yield or confidence |
| agricultural-risk | `risks[] {category: string, level: low…critical, …}` | Planned: `category: WEATHER\|CROP\|SUPPLY\|MARKET\|ANOMALY`; the level vocabulary is not specified |
| supply-forecast | `forecastPeriod: string` (Zod, **non-null**) | Actual: `forecastPeriod` passes through from ML and **may be null**. A null value would make the frontend report `MALFORMED_RESPONSE` (KNOWN ISSUE) |

### Backend → ML (all PROVISIONAL; no ML service exists)

| Method | Path | Request | Response | Model metadata | Failure behaviour | Status |
|---|---|---|---|---|---|---|
| POST | `/v1/predict/supply` | `{regionId, cropId, horizonMonths}` (untyped map) | `{modelVersion, prediction{value, unit, period}, provenance{datasetVersion, featureVersion, trainedAt}}` | `modelVersion` required. `provenance` optional, and `trainedAt` is kept as a raw string. No confidence field on purpose | 503 unreachable/timeout, 502 bad status or body; no retries | Client implemented and unit-tested; **counterpart missing** |
| — | demand, supply-demand, suitability, risk | — | — | — | — | No ML contract defined |

The provisional supply contract uses `horizonMonths`, which doesn't match the proposed ML supply model. The dataset audit proposes a target of annual × season production per district, while the backend asks for 1–12 months and the frontend draws monthly series. That granularity has not been agreed (§13).

---

## 8. Data Provenance

The platform enum is `common/api/DataClassification`: `OBSERVED`, `FORECAST`, `MODEL_PREDICTION`, `REGIONAL_ESTIMATE`, `SYNTHETIC`. The frontend mirrors it (`features/intelligence/shared/types.ts`) and maps it to UI badges through `originOf()`.

| Class | Definition | Where it is used in code | Real data of this class exists? |
|---|---|---|---|
| `OBSERVED` | Measured or officially reported | Weather provider contract (allowed). The soil enum has its own `OBSERVED` | Only user-entered soil profiles tagged OBSERVED. No observed weather, market or production data |
| `FORECAST` | A third-party forecast (e.g. a weather forecast) | Weather provider contract (allowed) | **No.** No provider exists |
| `MODEL_PREDICTION` | Produced by an ML model | `IntelligenceService.supplyForecast`, only when ML answers | **No.** There is no ML |
| `REGIONAL_ESTIMATE` | Estimated from regional aggregates | Enum value only. No backend code emits it | **No** |
| `SYNTHETIC` | Generated for development; never real | Frontend dashboard mock and supply-demand mock. The backend **refuses** SYNTHETIC weather | Yes, synthetic only, and labelled everywhere it appears |

**Soil uses a separate enum:** `SoilDataClassification = OBSERVED | ESTIMATED | SYNTHETIC`, plus a `SoilDataSource` consistency rule (§5).
- `ESTIMATED` is not in the platform enum, and the platform's `REGIONAL_ESTIMATE` is not in the soil enum.
- The ML dataset catalogue also uses `ESTIMATED`.
- Whether to unify them is an open decision (§13).

**Rules in force:**
- Synthetic data is never described as real agricultural data.
- The frontend "Prototype data" callouts and badges are driven by the classification in the response.
- Confidence is `null` unless a source states one.

**Caveat:** the dashboard mock does carry invented numeric `confidence` values (e.g. 0.71). They are labelled SYNTHETIC but look like model confidences.

---

## 9. Security / Authorization

Roles (`user/entity/Role`): `FARMER`, `FPO`, `AGRICULTURAL_OFFICER`, `ADMIN`. There is no `USER` role. Public registration always creates `FARMER`. No endpoint can create or change a role, so other roles can only be set directly in the database.

| Role | Actual permissions | Actual restrictions |
|---|---|---|
| **FARMER** | `/api/auth/me`. Full farm API on **own** farms only. All `/api/weather/**` and `/api/intelligence/**` endpoints | Another user's farm returns 404. No farm delete |
| **FPO** | Identical to FARMER; there is no FPO-specific logic | Same as FARMER |
| **AGRICULTURAL_OFFICER** | `/api/auth/me`, `/api/weather` (point), `/api/intelligence/*` | 403 on `/api/farms/**`. Farm-scoped weather and crop-recommendations return 404, because they own no farms. No officer-specific capability exists |
| **ADMIN** | Same as AGRICULTURAL_OFFICER | Same as AGRICULTURAL_OFFICER. There are no admin endpoints |

**Public endpoints:** `POST /api/auth/register`, `POST /api/auth/login`, `GET /actuator/health`. Everything else needs a valid token.

**Access errors:**
- **401 `UNAUTHORIZED`:** the token is missing, invalid or expired, or the account is disabled.
- **403 `FORBIDDEN`:** the role isn't allowed on that endpoint.

**Frontend:** there is no role-based UI. An ADMIN or OFFICER would see the Farms navigation and get the 403 message.

---

## 10. Database / Persistence

**Migrations** (Flyway, `backend/src/main/resources/db/migration`):

| Version | Change |
|---|---|
| V1 `create_farm` | `CREATE EXTENSION postgis`. Tables `farm_location`, `soil_profile` and `farm`, with CHECK constraints for ranges and enums. `farm_location.geog geography(Point,4326)` is **generated** from lat/lon and has a GiST index |
| V2 `optional_soil_profile` | `farm.soil_profile_id` becomes nullable |
| V3 `users_and_farm_ownership` | `users` table: unique email, lowercase-email CHECK, role CHECK. `farm.owner_id` references `users`, **nullable**. Index on `(owner_id, created_at DESC)`, replacing the created-at index |

**Tables:** `users`, `farm`, `farm_location`, `soil_profile`, plus Flyway's `flyway_schema_history`. All IDs are UUIDs, and enums are stored as strings.

**Relationships:**
- `users 1 ─ 0..n farm` (via `owner_id`)
- `farm n ─ 1 farm_location` (unique FK, so effectively 1:1)
- `farm n ─ 0..1 soil_profile` (unique FK)

**PostGIS:**
- Only the generated `geog` column and its index use it.
- **No query uses it**, JPA doesn't map it, and Hibernate Spatial is not installed.

**Seed data:** none; no migration inserts rows.

**Local dev database** (queried read-only during this audit):
- All three migrations applied successfully.
- 3 users, all `FARMER` and enabled.
- 4 farms, **2 of them ownerless** (`owner_id IS NULL`):
  - "Green Valley Farm", created 2026-09-23, has soil.
  - "No Soil Farm", created 2026-09-24, has no soil.
- The ownerless farms predate V3. They are kept but cannot be reached through the API.

---

## 11. Testing / Verification

Every result below was produced during this audit on 2026-09-28.

| Area | Check | Result |
|---|---|---|
| Backend | `mvnw test` (JDK 21, Testcontainers PostGIS) | **Pass: 63 tests, 0 failures, 0 errors, 0 skipped.** Per-class counts in §5 |
| Frontend | `npm run build` (includes the TypeScript compile) | **Pass.** Chunk-size warning (2.1 MB main JS) |
| Frontend | `npm run lint` | **Pass** |
| Frontend | Automated tests | None exist |
| Frontend | Browser verification | **Not done** |
| ML | Tests, API checks, training, evaluation | **Nothing to run.** No code exists. Port 8000 has no listener |
| DB | Migration state (local) | V1–V3 applied successfully |

---

## 12. Known Issues

1. **Stale weather statements remain in the frontend.**
   - `frontend/docs/intelligence-api.md` §3 (the `getWeather` row) still says "the provider labels its data SYNTHETIC". Its §5 was already corrected.
   - The comment in `frontend/src/features/intelligence/weather/api.ts` says the same thing.
   - The backend docs, `backend/CLAUDE.md` and the root `README.md` are already current.
2. **Stale table in `backend/docs/intelligence-api.md`.** The "Frontend vs this contract" table says the frontend uses `/intelligence/supply`, `periodId` and `retrievedAt`, but the frontend already uses the backend paths and parameters.
3. **`frontend/docs/intelligence-api.md` calls the intelligence controller "uncommitted work in progress".** That is still true: the `intelligence` package is untracked.
4. **Weather has no data source.** Every weather request returns 503.
5. **`WeatherProvider.Report` has a single `dataClassification`** covering both observed `current` and forecast `daily`. A real provider will need separate provenance for each.
6. **The ML service does not exist.** `supply-forecast` always returns 503 `ML_SERVICE_UNAVAILABLE`, and the ML contract is provisional.
7. **The frontend and backend intelligence body shapes differ** for supply-demand, crop recommendations and risk (§7).
8. **Supply-forecast `forecastPeriod` nullability mismatch.** The backend may return null, but the frontend Zod schema requires a string, so the frontend would report `MALFORMED_RESPONSE`. No UI calls it yet.
9. **Weather 503 gets a generic message in the frontend.** `describeError` has no case for `WEATHER_UNAVAILABLE`, so it shows the generic "server hit an unexpected error" text. No UI uses weather yet.
10. **Ownerless development farms.** The local DB has 2 farms with `owner_id = NULL`, which the API cannot reach. `owner_id` stays nullable until they are resolved.
11. **ADMIN and AGRICULTURAL_OFFICER have no defined permissions.** They get 403 on farms but have unrestricted access to weather and intelligence.
12. **Two provenance vocabularies.** The soil enum and the dataset catalogue use `ESTIMATED`; the platform enum uses `REGIONAL_ESTIMATE`.
13. **Dashboard mock farms are not the user's real farms.** The dashboard shows invented farms and districts regardless of what the user has registered.
14. **The dashboard supply-demand chart hard-codes its unit.** The screen-reader summary says "thousand tonnes" and the tooltip says "kt", whatever `d.unit` is.
15. **The dashboard mock shows invented numeric confidences and risk narratives.** They are labelled SYNTHETIC but read like real assessments.
16. **No reference data.** Regions and crops are static frontend lists, and the backend accepts any well-formed slug.
17. **`GET /api/farms` is unpaged, and there is no farm DELETE.** CORS doesn't allow DELETE either.
18. **The frontend bundle is 2.1 MB.** There is no code-splitting.
19. **Stale references in the ML dataset catalogue:** `ML_CLAUDE.md` and `ML_STATE.md` don't exist, and `ml-branch` is no longer identical to `main`.
20. **Uncommitted working tree.** The weather change, the `intelligence` package, the ML docs and many doc edits are uncommitted or untracked. The `.gitignore` edit stops ignoring `backend/.mvn/`.
21. **Tooling (Claude Code session, not a project defect).** Bash and PowerShell calls failed intermittently with "auto mode classifier gave no verdict". Retrying later worked.

---

## 13. Open Decisions (for the project owner)

1. **Weather provider.** Connect a real source (e.g. Open-Meteo, NASA POWER or IMD), or keep 503. Also decide how observed and forecast provenance are split.
2. **ML contract.**
   - The supply request and response shape.
   - Time granularity: `horizonMonths` or season/year.
   - Units, and whether ML returns uncertainty.
   - Whether and when ML exposes demand-proxy, suitability/yield and risk.
3. **The supply-demand response body.** Pick the frontend draft or the backend plan (§7).
4. **Legacy ownerless farms.** Assign them to a user or delete them. Then add a migration to make `owner_id NOT NULL`.
5. **ADMIN permissions.** What they may see or do, including whether they should keep open access to intelligence and weather.
6. **AGRICULTURAL_OFFICER permissions.** For example, read access to farms in a region, and how an officer gets that role.
7. **FPO semantics.** It is identical to FARMER today.
8. **Provenance vocabulary.** Whether to merge `SoilDataClassification` / `ESTIMATED` into the platform `DataClassification`, and whether `REGIONAL_ESTIMATE` soil may be tagged `SYNTHETIC`.
9. **MVP region and crops,** plus region/crop id format and reference data. The catalogue proposes Maharashtra with onion, tomato and soybean, and the frontend offers `mh`/`ka`.
10. **Dashboard.** Keep it as a labelled mock, or define a backend summary endpoint.
11. **Farm list.** The pagination shape, and whether farms can be deleted.

---

## 14. Current Development Status

| Area | Status | Notes |
|---|---|---|
| Frontend | PARTIAL | Auth and farms are real. Dashboard and Supply & Demand are mock. Seven modules are placeholders. Build and lint pass; no tests |
| Backend | PARTIAL | Auth, farms and soil are complete. Weather and intelligence endpoints exist but serve no data. 63/63 tests pass |
| ML Service | NOT STARTED | Dataset audit document only |
| Authentication | IMPLEMENTED | JWT HS256, BCrypt, per-request user reload; no refresh or reset |
| Farm | IMPLEMENTED | Owner-scoped CRUD without delete; unpaged list; 2 ownerless dev rows |
| Soil | IMPLEMENTED | Optional; provenance-pair validation; separate classification enum |
| Weather | NO SOURCE (503) | No provider; synthetic data refused |
| Intelligence | CONTRACT ONLY / WAITING FOR ML | Supply waits for ML; 4 endpoints always return 503 |
| ML Integration | PARTIAL | `MlClient` implemented and tested; no ML counterpart; provisional contract |
| LLM / advisory | NOT STARTED | No code |

---

## 15. Recommended Next Steps

These follow directly from the gaps above. The order within each list is dependency order, not priority.

### Backend
- Refresh the "Frontend vs this contract" table in `backend/docs/intelligence-api.md`.
- Once decided (§13.1), implement one real `WeatherProvider` with truthful, split observed/forecast provenance.
- Once decided (§13.4), resolve the ownerless farms and add a `V4` migration making `owner_id NOT NULL`.
- Replace the untyped ML request `Map` with a typed record once the ML contract is agreed.
- Once decided (§13.5–13.7), implement the ADMIN, OFFICER and FPO rules, with tests.

### Frontend
- Fix the stale weather comment in `weather/api.ts` and the `getWeather` row in `frontend/docs/intelligence-api.md`.
- Make `forecastPeriod` nullable in the supply-forecast schema, to match the backend.
- Handle `WEATHER_UNAVAILABLE` in `describeError`.
- Use `d.unit` in the dashboard chart's summary and tooltip instead of the hard-coded "thousand tonnes" / "kt".
- Once decided (§13.3), align the supply-demand draft types to the agreed body.
- Perform browser verification of the current screens.

### ML
- Scaffold `ml-service/` (FastAPI, pinned dependencies) with `POST /v1/predict/supply` matching the agreed contract.
- Build the documented supply baseline (seasonal-naive plus a 3-year mean on DES APY) before any tree model, and record `modelVersion`, `datasetVersion` and `featureVersion`.
- Fix the catalogue's stale references and use the platform classification names.

### Integration
- Agree and document the Backend → ML supply contract, including granularity, units, period format and uncertainty (§13.2).
- Add an end-to-end check that runs frontend `api` mode against the backend with a real ML service.
- Commit the current working tree in focused commits: weather-provider removal, intelligence module, docs, ML docs.

---

## 16. Important Architectural Rules

Future sessions must preserve these rules:

1. **ML predicts. Spring Boot decides. LLM explains.** No LLM exists yet; when one is added, it only explains structured facts and never produces agricultural truth.
2. **No fabricated agricultural predictions.** Without a real source the answer is an explicit unavailable state (503 `PREDICTION_UNAVAILABLE`, `WEATHER_UNAVAILABLE` or `ML_SERVICE_UNAVAILABLE`), never a placeholder number.
3. **Synthetic data must be labelled `SYNTHETIC`** in the API and the UI. Weather is stricter: production must never contain a mock weather provider, and the backend refuses synthetic weather.
4. **Preserve provenance:** source, classification, timestamps, model version, and a confidence that stays null unless the source states it. Never convert units silently.
5. **The frontend must not invent backend contracts.** Draft types are marked PROVISIONAL and are never presented as real. A failed real request is never replaced by mock data.
6. **The frontend talks only to Spring Boot.** Only `ml/MlClient` talks to ML.
7. **The backend must not implement ML models,** and the ML service must not own Spring Boot business logic, authorization or persistence.
8. **Ownership comes from the token**, never from client input. Another user's resource is reported as 404.
9. **Flyway owns the schema** (`ddl-auto=validate`). Never edit an applied migration.
10. **Avoid premature distributed infrastructure:** a modular monolith, with no Kafka, Redis, service mesh or microservices until a feature requires them.
11. **Secrets come only from environment variables.** `JWT_SECRET` has no default. Never commit credentials.
