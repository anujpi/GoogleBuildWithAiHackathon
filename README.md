# Agricultural Intelligence Platform

Built for Google Build with AI, Problem Statement #04 (Agricultural Intelligence).

An evidence-first decision platform for Indian farmers and FPOs. It links **what a piece of land can grow** with
**what the market needs**, and uses Google Gemini to explain the result in English or Hindi. Every number on screen
is labelled with where it came from: observed, forecast, model prediction, estimate or synthetic.

## Problem

Crop choices in India are usually made without four things at once: local agronomic evidence, a view of regional
supply against demand, early warnings (weather, disease, anomalies), and advice the farmer can actually read in
their own language. Recommending whatever sells can push unsuitable land into a crop. Ignoring the market can drive
everyone into the same surplus crop.

## Solution

One farm-level journey that combines land and market in both directions (**Land → Market**):

1. **Current conditions**: a live 7-day weather forecast for the farm location (Open-Meteo).
2. **Crop suitability**: a transparent evidence index built from 1997–2019 state × season yield history, soil pH
   and water need.
3. **Crop health**: an image classifier for Potato and Tomato leaf diseases (MobileNetV3, transfer learning on
   PlantVillage).
4. **Supply forecast**: a state-level XGBoost model, back-tested against baselines.
5. **Demand forecast**: a FAOSTAT consumption proxy with a back-tested statistical baseline, apportioned to the
   state by population.
6. **Supply−demand gap, anomaly checks and risk**: documented rules and robust statistics.
7. **Decision**: made by the backend. Demand never overrides land suitability; the backend suggests alternatives
   instead.
8. **AI explanation**: Gemini explains *only* the structured evidence, in English or Hindi, with read-aloud. The
   backend checks every number in Gemini's answer against that evidence.

## Architecture

```
React (Vite, TanStack Query)  ──►  Spring Boot 4 (JWT, orchestration, decision rules, Gemini client)
                                        │                 │                 │
                                        ▼                 ▼                 ▼
                              FastAPI ML service     PostgreSQL+PostGIS   Google Gemini API
                              (suitability, supply,  (users, farms,       Open-Meteo API
                               demand, anomaly,       production history)
                               disease)
```

**ML predicts, Spring Boot decides, Gemini explains, React presents.** React calls only the backend. Only the
backend calls the ML service, Gemini and Open-Meteo. Each ML result reaches the UI as an independent section, so a
failure shows as "UNAVAILABLE, with the reason" and nothing is quietly substituted.

## AI and ML components

| Module | Method | Data | Evaluation / honesty |
|---|---|---|---|
| Crop suitability | Weighted evidence index (6 documented components) | Kaggle crop_yield (state × crop × season, 1997–2020); indicative pH and water-need table | Not a probability and not calibrated; no accuracy is claimed |
| Disease | MobileNetV3-Large, ImageNet-pretrained backbone (frozen) + linear-probe head; 13 Potato/Tomato classes | PlantVillage (CC BY-SA 3.0), leakage-aware split by leaf, filename and perceptual hash | Held-out test (n=4,080): accuracy 0.951, macro-F1 0.942 against 0.660 for a colour-histogram baseline; lab imagery, so field accuracy was not measured |
| Supply | XGBoost vs. area × last-yield baseline | Kaggle crop_yield | Test 2017–19: WAPE 9.27 % against 9.53 % for the baseline, a **marginal** gain; the 80 % interval covers 81.9 % |
| Demand | Naive vs. linear trend, chosen by a rolling back-test | FAOSTAT Food Balance Sheets, India (2010–2023); Census 2011 population | 1-step back-test MAPE is reported with every response; it is a **proxy**, not market demand |
| Anomaly | Robust z-score (median/MAD) | Any series; used on yield, production and demand | Transparent, not trained |
| Risk and decision | Rule engine (`backend-decision-rules-v1`) | All of the above plus live weather | Thresholds are documented heuristics |
| Advisory | **Google Gemini** (`gemini-2.5-flash`, JSON-schema output) | The evidence object only | Numbers are checked against the evidence; without a key, a labelled template is used |

## Datasets

| Dataset | Use | License | Where |
|---|---|---|---|
| Kaggle "Agricultural Crop Yield in Indian States" (akshatgupta7) | Supply, suitability, anomalies | CC BY-SA 4.0 | `backend/src/main/resources/data/kaggle-crop-yield/`, `ml-service/docs/datasets/crop_yield.md` |
| PlantVillage (Hugging Face `mohanty/PlantVillage`) | Disease classifier | CC BY-SA 3.0 | Downloaded locally, git-ignored; see `ml-service/docs/datasets/plantvillage.md` |
| FAOSTAT Food Balance Sheets, India | Demand proxy | CC BY 4.0 | `ml-service/reference_data/` |
| Census of India 2011 state population | State share of demand | Govt statistics / CC BY-SA (Wikipedia table) | `ml-service/reference_data/` |
| Open-Meteo forecast API | Live weather | CC BY 4.0, non-commercial free tier | Called live by the backend |

The data.gov.in mandi price and arrival API (S06/S07) was unreachable when this was built, so the platform uses **no
price data**, and says so.

## India scale

- **Every endpoint is keyed by state, season and crop.** Nothing is tied to one city. The historical evidence covers
  30 states, and demand apportionment covers 36 states and UTs.
- **To analyse a new region, add a farm in any state.** Suitability, supply, demand, gap and risk are computed for
  that state automatically.
- **Evidence is state-level. The farm contributes its location (weather), soil pH and irrigation.** District-level
  production data exists (data.gov.in S01, up to 2014) but isn't wired in yet.

## Local setup

Needs Java 21, Python 3.11+, Node 20+, and PostgreSQL 17 with PostGIS on port 5433 (`backend/compose.yaml` does
this with Docker).

```bash
# 1. Database (Docker) — or a local PostgreSQL 17 + PostGIS on :5433 with db/user/password agri
cd backend && docker compose up -d && cd ..

# 2. ML service
cd ml-service && python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.lock && pip install -e ".[dl]"
cp ../backend/src/main/resources/data/kaggle-crop-yield/crop_yield.csv data/raw/   # suitability data
python scripts/train_supply.py                       # supply artifact
# optional disease model: see ml-service/docs/datasets/plantvillage.md, then
# python scripts/prepare_plantvillage.py && python scripts/train_disease_probe.py   # CPU, ~30 min
uvicorn agri_ml.api.app:app --port 8000

# 3. Backend
cd backend
export JWT_SECRET=$(openssl rand -base64 48)
export GEMINI_API_KEY=...        # optional: without it the advisory uses a labelled template
./mvnw spring-boot:run           # :8080; Flyway creates the schema and loads the history

# 4. Frontend
cd frontend && npm ci && npm run dev    # http://localhost:5173
```

Deployment: see [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md). Nothing is deployed yet.

## Demo flow

1. Register or sign in, then open **Farm Intelligence**. It's the default landing page.
2. Pick a farm. The demo farm is a sample input: Agra, Uttar Pradesh, Rabi, borewell, soil pH 7.2 labelled
   SYNTHETIC.
3. The page shows:
   - **Current conditions**: live Open-Meteo data.
   - **Decision**: for example, "Potato is suitable, but regional supply is well above the demand proxy", with Wheat,
     Mustard and Gram as alternatives.
   - **Crop suitability**: ranked, with evidence. Click a crop to switch the focus.
4. **Crop doctor**: upload a potato or tomato leaf photo (samples are in `docs/demo-images/`, including one the model gets wrong). You get the class, probability, top-3 classes and an
   expert-review flag.
5. **Supply, demand and gap**: ML supply forecast, FAOSTAT demand, surplus or deficit, and the current-year demand
   outlook.
6. **Risk and anomalies**: each factor with its level and the rule that set it.
7. **Explain with AI**: Gemini's explanation, key factors, actions and uncertainty. Switch to हिन्दी and press it
   again, then press **Read aloud**.

The full demo script is in [`docs/SUBMISSION.md`](docs/SUBMISSION.md).

## Limitations

- **The historical data ends in 2019/2020.** The supply "forecast" is for crop year 2020; it is not a live-season
  forecast.
- **Demand is a national consumption proxy** apportioned by 2011 population. There are no mandi prices or arrivals.
- **Suitability is state-level evidence**, not a field-level agronomic model. The pH and water table is indicative.
- **The disease model is trained on lab-style PlantVillage photos** and covers Potato and Tomato only. Its field
  accuracy is unknown, and its probabilities are uncalibrated.
- **The risk thresholds are heuristics.** No risk score is a probability.
- **Backend integration tests use Testcontainers**, so they need Docker.

## Repository

- `frontend/`: React + TypeScript. See `frontend/CLAUDE.md`.
- `backend/`: Spring Boot. API docs are in `backend/docs/`.
- `ml-service/`: FastAPI and ML. See `ml-service/README.md`, `ML_STATE.md`, and `docs/` (datasets, model cards,
  contracts).
