# ML Service: Progress Report

**Project:** Agricultural Intelligence Platform, ML/Data Science service
**Date:** 24 September 2026
**Branch:** `ml-branch`. The foundation is committed (`0ec3b77`); the Stage 1 docs are not committed yet.

**Guiding rule:** ML predicts. Spring Boot decides. LLM explains.

---

## Summary

| Stage | Status |
|---|---|
| Stage 0: ML foundation | ✅ Done |
| Stage 1: Dataset catalogue and data source audit | ✅ First pass done. 25 sources audited; a few access actions are waiting on the team |
| Model training, prediction APIs, backend integration | ⏸️ Not started, on purpose |

The ML service has a working, tested Python project with a running FastAPI server and MLflow
experiment tracking. We've also audited 25 candidate data sources and know which ones actually
work. No models have been trained and no data has been downloaded. That's by design: the
engineering rules in `CLAUDE.md` require us to audit the data before building models.

**Headline:** we recommend starting with **supply forecasting for Uttar Pradesh (potato and
wheat)**, tested on historical data. Recent-year production data and mandi arrival quantities are
the main gaps.

---

## Stage 0: ML foundation (done)

### What exists
- **Python project** (`pyproject.toml`, Python 3.11+), split into lightweight dependency groups:
  - **Core:** FastAPI, Uvicorn, Pydantic
  - **ML:** pandas, NumPy, scikit-learn, XGBoost, MLflow, matplotlib, seaborn
  - **Dev:** pytest, httpx, ruff
  - PyTorch and the geospatial libraries are left out until a phase needs them.
- **`requirements.lock`** pins the exact package versions we tested, so the environment can be rebuilt reproducibly.
- **FastAPI app** with one endpoint, `GET /health`:
  ```json
  {"status":"UP","service":"agri-ml","version":"0.1.0","environment":"local","loadedModels":[]}
  ```
- **Shared data classification list** (`OBSERVED`, `FORECAST`, `ESTIMATED`, `MODEL_PREDICTION`, `SYNTHETIC`), so every future prediction states what kind of data it is.
- **Configuration** through `AGRI_ML_*` environment variables.
- **MLflow experiment tracking** stored locally in a SQLite file. A check script confirms experiments and runs can be created.
- **Tests:** 4 tests, all passing. Lint (ruff) is also clean.
- **Documentation templates:**
  - dataset catalogue entry (`docs/datasets/_TEMPLATE.md`)
  - model card (`docs/model-cards/_TEMPLATE.md`)
  - ML ⇄ Spring Boot API contract (`docs/ml-contracts/README.md`)

### Folder layout
```text
ml-service/
├── CLAUDE.md          engineering rules for the ML service
├── ML_STATE.md        detailed state tracker
├── ML_TEAM_PLAN.md    staged roadmap
├── README.md          setup / run / test instructions
├── pyproject.toml
├── requirements.lock
├── src/agri_ml/       api/, config/, schemas/
├── scripts/           MLflow check
├── tests/
├── notebooks/         exploratory/, experiments/
└── docs/              datasets/, model-cards/, ml-contracts/
```
We only created the folders we need now. Model, training and inference packages get added when their phase starts.

### Tested environment
Python 3.13.3 on macOS (Apple Silicon) with pandas 3.0.6, NumPy 2.5.3, scikit-learn 1.9.1,
XGBoost 3.4.1, MLflow 3.16.1 and FastAPI 0.141.1.

### How to run it
```bash
cd ml-service
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[ml,dev]"            # on macOS also: brew install libomp
pytest                                # run tests
uvicorn agri_ml.api.app:app --port 8000
curl localhost:8000/health
```

---

## Issues found and fixed

| Issue | Cause | Fix |
|---|---|---|
| XGBoost would not load | macOS needs the OpenMP runtime | `brew install libomp` (documented in README) |
| MLflow refused to start tracking | MLflow 3 no longer accepts the old `./mlruns` folder store | Switched to a local SQLite store (`mlflow.db`) |
| `import agri_ml` failed after a successful install | The project sits on an iCloud-synced Desktop. iCloud marks files in `.venv` as hidden, and Python 3.13 skips hidden `.pth` files, which editable installs rely on | Moved the virtual environment outside iCloud (`~/.venvs/agri-ml-service`) and linked it as `.venv`. The packaging config was correct and didn't change |

**Tip for teammates on a Mac:** if your project folder is in iCloud-synced Desktop or Documents
and you get `No module named 'agri_ml'`, see the troubleshooting section in `README.md`.

---

## Housekeeping and decisions

- Renamed `ML_CLAUDE.md` → `CLAUDE.md`, and `docs/contracts/` → `docs/ml-contracts/`.
- **Decided:** "source" and "data classification" are separate concepts.
  - **Source** is where the data came from: `SOIL_HEALTH_CARD`, `IMD`, `AGMARKNET`, `REGIONAL_ESTIMATE`, …
  - **Data classification** is what kind of data it is: `OBSERVED`, `FORECAST`, `ESTIMATED`, `MODEL_PREDICTION`, `SYNTHETIC`.
  - The backend's `REGIONAL_ESTIMATE` stays as it is, as a source.
- **Decided:** ML ⇄ backend contracts live in `ml-service/docs/ml-contracts/`.

---

## Stage 1: Data source audit (first pass done)

Goal: find out which real Indian agricultural data sources we can actually use.

**Method:** we sent small test requests to each source (1–3 rows, or the first few KB of a file,
with no large downloads). A source is **VERIFIED** only if it returned real data; a webpage
existing is not enough. The full details for every source are in
`docs/datasets/DATASET_CATALOGUE.md`, and the rankings are in `docs/datasets/DATASET_PRIORITY.md`.

### ✅ VERIFIED: returned real data
| Source | What it gives us | Key limitation |
|---|---|---|
| **data.gov.in: district crop production** | Area and production by district, season, crop and year (246k rows) | **Ends in 2014** (2015 partial, nothing after) |
| **data.gov.in: historical mandi prices** | Daily min/max/modal prices per market (82M rows, **updated daily**) | Prices only, **no arrival quantities** |
| **data.gov.in: today's mandi prices** | Live snapshot of the current day's prices | No history |
| **IMD gridded rainfall** | Official daily rainfall, 0.25° grid, 1901–2024 | Commercial-use license unclear |
| **NASA POWER** | Daily temperature and other weather for any point | Estimated (satellite/model), not station readings |
| **Open-Meteo** | Weather forecasts plus historical weather | Free tier is non-commercial only |
| **ISRIC SoilGrids** | Soil properties (pH etc.) for any point | Modelled estimates, not lab tests |

### ⛔ BLOCKED: can't access today
| Source | Why |
|---|---|
| **APY portals** (DES crop statistics) | Websites time out |
| **eNAM** | Dashboards only; no official download or API |
| **IMD API** (official forecasts) | Returns "API key missing"; needs a key or IP whitelisting from IMD |
| **Soil Health Card** | Dashboard only; no export |

### 🟡 CANDIDATE: promising, not confirmed yet
| Source | Status |
|---|---|
| **UPAg** | Reportedly has district data for **1997–2025**, which would fill the post-2014 gap. Export or API not confirmed. |
| **CEDA API** (Ashoka University) | Has mandi **arrival quantities**. Needs registration; license unknown. |
| **AGMARKNET 2.0** | Shows arrivals on the website, but has no documented API or export |
| **ICRISAT District Level Database** | Long history (1966 onwards), open terms, but stale and the download method isn't confirmed |
| **Sentinel-2, Landsat, MODIS** | Satellite catalogues work; imagery downloads not tested |
| **Google Earth Engine** | Free only for non-commercial use |
| **PlantVillage** (disease images) | License verified per copy: CC BY-SA 3.0 (Hugging Face) or CC0 (Mendeley). Lab photos, not field photos. |
| **PlantDoc** (disease images) | CC BY 4.0. Small set of field photos. |
| **MoSPI consumption survey** | A demand **proxy**. Access not confirmed (site timed out). |

**Not suitable:** FAOSTAT (country level only) and old, coarse data.gov.in price series.

### What we learned
- **No public source has actual demand data.** Every demand signal (mandi arrivals, consumption
  surveys) is a **proxy** and will be labelled as one.
- **District production data stops in 2014.** We can test supply models on history, but we
  can't make trustworthy current-year forecasts until UPAg (or another recent source) is confirmed.
- **No verified source has mandi arrival quantities yet.** The official price APIs have prices only.
- **Tomato can't be a supply target.** The production data has 0 rows for tomato in UP, even
  though its price data exists.
- **We tested data.gov.in with a shared public demo key,** which is repeatedly rate-limited. The
  team needs its own key for real use.

### Readiness by ML task
| Task | Readiness | Why |
|---|---|---|
| **Supply forecasting** | 🟡 Ready for a historical baseline | Verified production + weather data, but only up to 2014 |
| **Price anomaly detection** | 🟢 Strongest current data | Verified daily prices, updated every day |
| **Crop suitability / yield** | 🟡 Partial | Yield can be derived (production ÷ area); soil data is estimated only |
| **Disease detection** | 🟡 Data exists; deferred | Datasets with clear licenses exist, but it's scheduled for later in the plan |
| **Demand forecasting** | 🔴 Not ready | No observed demand; proxies not verified yet |

### Recommended MVP (to agree with the team)
- **Region: Uttar Pradesh.** It has the most mandi price history in India's data and good district production coverage (~75 districts).
- **Crops:**

  | Crop | Production rows (UP) | Price rows (UP) |
  |---|---|---|
  | **Potato** | 1,276 | 826,017 |
  | **Wheat** | 1,275 | 765,202 |
  | **Onion** *(tentative; Rice as the alternative)* | 1,736 | 744,451 |

- **First task: a supply forecasting baseline,** tested on history: train up to 2010, validate 2011–12, test 2013–14.
  - Start with simple baselines only: last value, moving average, historical average.
  - **Price anomaly detection** is the natural second task.

---

## What we deliberately have **not** done
- ❌ Trained any model
- ❌ Downloaded any dataset
- ❌ Created synthetic or fake data
- ❌ Built prediction endpoints
- ❌ Integrated with Spring Boot
- ❌ Changed any frontend or backend code
- ❌ Scraped undocumented website backends

## What we should NOT attempt yet
- Calling any proxy "demand".
- Claiming current-year supply forecasts (production data ends in 2014).
- Tomato or other vegetable supply models (no production data).
- Satellite pipelines or disease models before the tabular baselines exist.

---

## Next steps
1. **Register a data.gov.in API key** for the team. Without it, we can't pull the verified production and price data.
2. **Confirm UPAg can export district data for 2015–2025,** to fill the recent-years gap.
3. **Register for the CEDA API** and check its license, to get mandi arrival quantities.
4. **Agree on the MVP region and crops** (proposal: UP; potato, wheat, onion).
5. After that: pull a small, filtered UP dataset, audit it, then build the supply baselines.

## Needs input from the team
- Who registers the data.gov.in API key and the CEDA account?
- Do we agree on Uttar Pradesh and potato / wheat / onion?
- Is the product **commercial or non-commercial**? The answer changes which data sources we're allowed to use (Open-Meteo, Earth Engine, IMD).
- Add `ml-service/` to the top-level `README.md`.
