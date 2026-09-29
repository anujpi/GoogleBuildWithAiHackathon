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

## Modules (`src/main/java/com/argiintelligence/backend/`)

The package name is spelled `argiintelligence` on purpose.

| Package | Contents |
|---|---|
| `common` | `ApiError` (the error body used everywhere), `ApiException`, `GlobalExceptionHandler`, `DataClassification` |
| `configuration` | `CorsConfig` |
| `auth` | Register, login and `/me`. `security/` holds the URL rules (`SecurityConfig`), `JwtService`, the JWT-to-user converter and the JSON 401/403 handler. |
| `user` | `User` entity (table `users`), `Role` (FARMER, FPO, AGRICULTURAL_OFFICER, ADMIN) |
| `farm` | Farm, FarmLocation and optional SoilProfile. Every query is scoped to the owner. |
| `weather` | `WeatherProvider` interface with **no implementation yet**, so the endpoints return 503. `WeatherService` refuses any `SYNTHETIC` report. |
| `ml` | `MlClient`, the only code that makes HTTP calls to the ML service |
| `intelligence` | `/api/intelligence/supply` through `MlClient` (the old placeholder endpoints were removed, MASTER_SPEC §6.9) |

## API

| Endpoint | Auth | Status |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | public | Working |
| `GET /api/auth/me` | token | Working |
| `POST/GET /api/farms`, `GET/PUT /api/farms/{id}` | token, FARMER or FPO | Working. No DELETE. |
| `GET /api/weather`, `GET /api/weather/farms/{farmId}` | token | No source: 503 `WEATHER_UNAVAILABLE` after validation and the ownership check |
| `GET /api/intelligence/supply` | token | District supply estimate from the ML service (`districtId`, `cropId`, `season`, `cropYear`, optional `areaHectares`); MASTER_SPEC §8 |
| `GET /actuator/health` | public | Working |

Full contracts: [docs/auth-api.md](docs/auth-api.md), [docs/farm-api.md](docs/farm-api.md), [docs/intelligence-api.md](docs/intelligence-api.md).

Every error uses one shape: `{ timestamp, status, code, message, path, details[] }`. It never contains a stack trace.

## Database

Migrations are in `src/main/resources/db/migration`. Never edit one that has already been applied; add a new version instead.

| Version | Change |
|---|---|
| V1 | PostGIS extension; `farm`, `farm_location` (with a generated `geog` point and GiST index) and `soil_profile` |
| V2 | Soil profile becomes optional |
| V3 | `users` table; `farm.owner_id`. The column is nullable because farms created before accounts existed have no known owner, so they are kept but can't be reached through the API. |

## Tests

```bash
./mvnw test        # Docker must be running
```

Integration tests start their own `postgis/postgis:17-3.5` container through Testcontainers and apply the real migrations. They don't use the compose database or an H2 database. A test-only JWT secret is injected in `TestcontainersConfiguration`.

The last run (2026-09-28) passed **63 of 63** tests:

| Class | Tests |
|---|---|
| `AuthControllerIntegrationTest` | 9 |
| `FarmControllerIntegrationTest` | 17 |
| `WeatherControllerIntegrationTest` (no provider) | 7 |
| `WeatherProviderIntegrationTest` (provider test double) | 4 |
| `IntelligenceControllerIntegrationTest` | 8 |
| `MlClientTest` (golden ML contract files in `src/test/resources/contracts/ml`) | 31 |
| `MlServiceLiveTest` (real HTTP; runs only with `ML_E2E_BASE_URL=http://localhost:8000`) | 3 |
| `GlobalExceptionHandlerTest` | 5 |
| `BackendApplicationTests` | 1 |

## Conventions

These are in [CLAUDE.md](CLAUDE.md):
- DTOs, never entities, in API responses.
- Keep the service and repository layers separate.
- Put external data behind provider interfaces.
- Tag every value with provenance and a data classification, and never invent values.
- No Kafka, Redis or microservices until a feature needs them.
