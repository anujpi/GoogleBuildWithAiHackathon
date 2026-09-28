# Agricultural Intelligence Platform

An agricultural decision-intelligence platform for Indian farming. The goal is to take a farm from its soil, weather and history through to supply, demand and risk intelligence and an explainable recommendation:

```
Farm → Soil / Weather / History → Crop intelligence → Supply & demand forecasts
     → Supply-demand gap → Risk → Decision engine → Explanation / advisory
```

The MVP targets one Indian state and 2–3 crops. Real data is used wherever it exists. Anything synthetic is labelled as synthetic in both the API and the UI and is never presented as measured.

## Repository

| Folder | What it is | Docs |
|---|---|---|
| `backend/` | Spring Boot 4 modular monolith (Java 21, PostgreSQL + PostGIS, Flyway, Spring Security/JWT) | [backend/README.md](backend/README.md) |
| `frontend/` | React 19 + TypeScript + Vite single-page app | [frontend/README.md](frontend/README.md) |
| `ml-service/` | Planned Python/FastAPI ML service. Only a dataset audit exists so far | [ml-service/docs/dataset-catalogue.md](ml-service/docs/dataset-catalogue.md) |
| `PROJECT_STATE.md` | **Source of truth for what is actually implemented** | — |

Engineering rules for each app live in `backend/CLAUDE.md` and `frontend/CLAUDE.md`.

## What works today

| Area | Status |
|---|---|
| Accounts | **Working.** Register, login (JWT), `/api/auth/me`. The frontend has login and register pages, and every app route requires sign-in. |
| Farms + soil | **Working.** Create, list, view and edit your own farms: map-picked location, optional soil profile with provenance rules. |
| Weather | **No data source yet.** `/api/weather` validates the request and answers `503 WEATHER_UNAVAILABLE`. The backend never generates weather values. |
| Supply forecast | **Waiting for ML.** The backend calls the ML service. It returns 503/502 until that service exists. |
| Demand, supply-demand gap, crop recommendations, risk | **Contract only.** The endpoints validate input and answer `503 PREDICTION_UNAVAILABLE`. |
| Dashboard, Supply & Demand page | Render **synthetic** data, clearly labelled. |
| Market, scenarios, crop doctor, coordination, alerts, data ops | Placeholder pages only. |

## Quick start (local)

Prerequisites: Java 21, Node 20+, and Docker (for the database and the backend tests).

```bash
# 1. Database: PostGIS on host port 5433
cd backend
docker compose up -d

# 2. Backend on http://localhost:8080 (JWT_SECRET is required, >= 32 bytes)
export JWT_SECRET="$(openssl rand -base64 48)"     # PowerShell: $env:JWT_SECRET = "<value>"
./mvnw spring-boot:run                              # Windows: .\mvnw.cmd spring-boot:run

# 3. Frontend on http://localhost:5173
cd ../frontend
npm install
npm run dev
```

Open http://localhost:5173, register an account and add a farm.

## API contracts

- [backend/docs/auth-api.md](backend/docs/auth-api.md): register, login, current user, token behaviour
- [backend/docs/farm-api.md](backend/docs/farm-api.md): farms, location, soil, units, errors
- [backend/docs/intelligence-api.md](backend/docs/intelligence-api.md): weather and intelligence endpoints, their status, and how the frontend's paths and fields differ from the backend's
- [frontend/docs/intelligence-api.md](frontend/docs/intelligence-api.md): how the frontend consumes them
