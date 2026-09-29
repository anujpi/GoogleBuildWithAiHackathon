# Backend

This is a Spring Boot modular monolith. It owns authentication, farms and soil, weather, orchestration of the intelligence endpoints, persistence, validation and data provenance. The frontend talks only to this service; the ML service is called only from here.

Stack: Java 21 · Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server for JWT, Actuator) · PostgreSQL 17 + PostGIS · Flyway · Lombok · Testcontainers.

## Run locally

```bash
docker compose up -d                          # PostGIS on host port 5433 (5432 is often taken by a local Postgres)
export JWT_SECRET="$(openssl rand -base64 48)"   # required; PowerShell: $env:JWT_SECRET = "<value>"
./mvnw spring-boot:run                        # http://localhost:8080 ; Windows: .\mvnw.cmd spring-boot:run
```

Check it's up: `GET http://localhost:8080/actuator/health`.

Flyway applies the migrations on startup, and Hibernate only validates the schema.

## Configuration

Everything comes from environment variables. The defaults exist for local development only.

| Env var | Default | Notes |
|---|---|---|
| `JWT_SECRET` | none, **required** | HS256 key of at least 32 bytes. Startup fails without it. Never commit it. |
| `JWT_EXPIRATION` | `PT1H` | Access-token lifetime. |
| `DB_URL` | `jdbc:postgresql://localhost:5433/agri` | Matches `compose.yaml`. |
| `DB_USERNAME` / `DB_PASSWORD` | `agri` / `agri` | For local development only. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated. Never `*`. |
| `ML_SERVICE_BASE_URL` | `http://localhost:8000` | The FastAPI ML service. |
| `ML_SERVICE_CONNECT_TIMEOUT` / `ML_SERVICE_READ_TIMEOUT` | `2s` / `10s` | |
| `WEATHER_BASE_URL` | `https://api.open-meteo.com` | Open-Meteo Forecast API. No API key |
| `WEATHER_CONNECT_TIMEOUT` / `WEATHER_READ_TIMEOUT` | `2s` / `5s` | |
| `WEATHER_CACHE_TTL` | `PT30M` | Successful answers only; failures are never cached |
| `REFERENCE_SYNC_ON_STARTUP` | `true` | Mirrors the ML scope into `ref_*`. A failure is logged and recorded; the app still starts |
| `BOOTSTRAP_ADMIN_EMAIL` | unset | If set and no ADMIN exists, this already-registered user becomes ADMIN at startup |

For local development, `JWT_SECRET` can live in the git-ignored `backend/application-local.properties`, which `application.properties` imports automatically. A real environment variable still takes priority.

## Modules (`src/main/java/com/argiintelligence/backend/`)

The package name is spelled `argiintelligence` on purpose.

| Package | Contents |
|---|---|
| `common` | `ApiError`, `ApiException`, `GlobalExceptionHandler`, `DataClassification`, `Provenance`, `RiskLevel`, `PageResponse`, the `X-Request-Id` filter |
| `configuration` | `CorsConfig` |
| `auth` | Register, login and `/me`. `security/` holds the URL role gates (`SecurityConfig`), `JwtService`, the JWT-to-user converter and the JSON 401/403 handler. `AdminBootstrap` |
| `user` | `User` (table `users`), `Role` (FARMER, FPO, AGRICULTURAL_OFFICER, ADMIN), district assignments |
| `reference` | The ML scope mirrored into `ref_*` tables (sync at startup and on admin request), `crop_requirement`, `GET /api/reference/scope` |
| `farm` | Farm, FarmLocation, optional SoilProfile, `districtId`. Every lookup is scoped (owner, district or legacy) |
| `weather` | `OpenMeteoWeatherProvider` (the only provider), `WeatherService` (cache, refuses `SYNTHETIC`), health indicator |
| `ml` | `MlClient`, the only code that makes HTTP calls to the ML service; health indicator |
| `intelligence` | Supply (ML), crop evidence (`crop-evidence-v1`) and risk (`risk-rules-v1`) |
| `regional` | Read-only view of assigned districts (FPO, AGRICULTURAL_OFFICER) or all districts (ADMIN) |
| `admin` | Users, roles, districts, system and ML health, model and dataset metadata, reference sync, audit, legacy farms |
| `audit` | `audit_event` writes and search |

## API

| Endpoint | Auth | Status |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | public | Working |
| `GET /api/auth/me` | token | Working |
| `POST/GET /api/farms`, `GET/PUT /api/farms/{id}` | FARMER or FPO, own farms | Working. No DELETE |
| `GET /api/farms/{id}/weather`, `/crop-evidence`, `/risk` | FARMER or FPO, own farms | Working (see blockers) |
| `GET /api/reference/scope` | token | Working. Empty until ML serves an artifact (B1) |
| `GET /api/weather` | token | Working: real Open-Meteo data |
| `GET /api/intelligence/supply` | token | Working. 503 `ML_PREDICTION_UNAVAILABLE` until ML has a trained artifact (B1) |
| `GET /api/regional/**` | FPO, AGRICULTURAL_OFFICER (assigned districts), ADMIN | Working. Read-only |
| `/api/admin/**` | ADMIN | Working |
| `GET /actuator/health` | public | Working. `DEGRADED` (not `DOWN`) when ML or weather is unavailable |

Full contracts: [docs/auth-api.md](docs/auth-api.md), [docs/farm-api.md](docs/farm-api.md), [docs/intelligence-api.md](docs/intelligence-api.md), [docs/admin-api.md](docs/admin-api.md).

**Blockers** (MASTER_SPEC §20):
- **B1:** there is no trained ML artifact yet. Supply is unavailable, the reference scope is empty, and the ML-based evidence is `UNAVAILABLE`.
- **B2:** the FAO EcoCrop limits are not transcribed yet (`crop_requirement` is NULL), so the soil pH and temperature evidence is `UNAVAILABLE`.

Nothing is ever substituted for missing data.

Every error uses one shape: `{ timestamp, status, code, message, path, details[] }`. It never contains a stack trace.

## Database

Migrations are in `src/main/resources/db/migration`. Never edit one that has already been applied; add a new version instead.

| Version | Change |
|---|---|
| V1 | PostGIS extension; `farm`, `farm_location` (with a generated `geog` point and GiST index) and `soil_profile` |
| V2 | Soil profile becomes optional |
| V3 | `users` table; `farm.owner_id`. The column is nullable because farms created before accounts existed have no known owner. They are kept, and an ADMIN can give them a first owner |
| V4 | Reference tables (`ref_state`, `ref_district`, `ref_crop`, `ref_supply_series`, `ref_dataset`) and `reference_sync` |
| V5 | `farm.district_id` and `user_district_assignment` |
| V6 | `audit_event` |
| V7 | Seeds the four crops, plus `crop_requirement` with every limit NULL (blocker B2) |

## Tests

```bash
./mvnw test        # Docker must be running
```

Integration tests start their own `postgis/postgis:17-3.5` container through Testcontainers and apply the real migrations. They don't use the compose database or an H2 database. A test-only JWT secret is injected in `TestcontainersConfiguration`.

The last run (2026-09-29) had **156 tests: 0 failures, 0 errors, 3 skipped**. The 3 skipped tests are `MlServiceLiveTest`, which needs `ML_E2E_BASE_URL` and a live ML service with an artifact.

| Class | Tests |
|---|---|
| `MlClientTest` (golden ML contract files in `src/test/resources/contracts/ml`) | 31 |
| `RiskRulesTest` (every threshold boundary) | 28 |
| `FarmControllerIntegrationTest` | 17 |
| `AccessControlIntegrationTest` (RBAC matrix, regional, admin, legacy farms, audit, request ids) | 14 |
| `AuthControllerIntegrationTest` | 9 |
| `IntelligenceControllerIntegrationTest` (supply) | 8 |
| `OpenMeteoWeatherProviderTest` (mock HTTP: success, timeout, 429, 5xx, malformed) | 8 |
| `WeatherControllerIntegrationTest` | 8 |
| `CropEvidenceRiskIntegrationTest` | 7 |
| `GlobalExceptionHandlerTest`, `CropEvidenceTierTest`, `WeatherServiceTest` | 5 each |
| `BackendApplicationTests` (health, clean-DB migrations, V7 seed, legacy-row migration) | 4 |
| `ReferenceIntegrationTest` | 4 |
| `MlServiceLiveTest` (real HTTP; runs only with `ML_E2E_BASE_URL=http://localhost:8000`) | 3 |

Tests never reach the network: `TestcontainersConfiguration` turns off the startup sync and points ML and weather at a closed local port.
## Conventions

These are in [CLAUDE.md](CLAUDE.md):
- DTOs, never entities, in API responses.
- Keep the service and repository layers separate.
- Put external data behind provider interfaces.
- Tag every value with provenance and a data classification, and never invent values.
- No Kafka, Redis or microservices until a feature needs them.
