# ML_STATE

Last updated: 2026-09-24. Branch: `ml-branch`. Foundation committed in `0ec3b77`; the Stage 1 docs are not committed yet.

## Current stage

- **Stage 0: ML foundation.** Done.
- **Stage 1: Dataset catalogue + data source audit.** First pass done (2026-09-24). See
  `docs/datasets/DATASET_CATALOGUE.md` and `docs/datasets/DATASET_PRIORITY.md`. Open items are
  access actions only a team member can take (listed below).

---

## Stage 0: ML foundation (done)

### Built
- **`pyproject.toml`:** Hatchling build with a src-layout. Dependencies are split into three groups:
  - core: FastAPI, Uvicorn, Pydantic, pydantic-settings
  - `ml`: pandas, NumPy, scikit-learn, XGBoost, MLflow, matplotlib, seaborn
  - `dev`: pytest, httpx, ruff

  There is no PyTorch or geospatial stack yet.
- **`requirements.lock`:** exact versions of the verified environment.
- **`src/agri_ml/`:**
  - `config/settings.py`: settings from `AGRI_ML_*` environment variables (environment, MLflow URIs, data and artifact dirs)
  - `schemas/common.py`: `DataClassification` with OBSERVED, FORECAST, MODEL_PREDICTION, ESTIMATED and SYNTHETIC
  - `schemas/health.py` and `api/app.py`: `GET /health` returning `{status, service, version, environment, loadedModels}`
- **Tests:** `tests/test_health.py` and `tests/test_schemas.py`. All 4 pass.
- **MLflow:** local SQLite store (`mlflow.db`) with artifacts in `mlartifacts/`. MLflow 3 rejects
  the old `./mlruns` file store. `scripts/check_mlflow.py` creates an experiment and an empty run.
- **Docs:** `README.md`, `notebooks/README.md` and three templates:
  - `docs/datasets/_TEMPLATE.md`
  - `docs/model-cards/_TEMPLATE.md`
  - `docs/ml-contracts/README.md`
- **`.gitignore`:** keeps `.venv`, data, artifacts, `mlflow.db`, `mlartifacts/` and model binaries out of Git.

### Housekeeping
- `ML_CLAUDE.md` was renamed to `CLAUDE.md` with no content change.
- `docs/contracts/` was renamed to `docs/ml-contracts/`, as `CLAUDE.md` specifies, and the README was updated.
- The stray `src/.DS_Store` was removed.

### Verified environment
- Python 3.13.3 on macOS arm64.
- pandas 3.0.6, NumPy 2.5.3, scikit-learn 1.9.1, XGBoost 3.4.1, MLflow 3.16.1, FastAPI 0.141.1.
- XGBoost on macOS needs `brew install libomp`, which is installed on this machine.
- These checks passed: `pip install -e .`, `import agri_ml` (resolves to `src/agri_ml/__init__.py`),
  `pytest`, `ruff check`, `uvicorn agri_ml.api.app:app` serving `/health` with 200, and the MLflow run.

### Packaging issue (resolved)
- **Symptom:** `import agri_ml` failed with `ModuleNotFoundError` after a successful `pip install -e .`.
- **Cause:** the repo sits on an iCloud-synced Desktop, and the sync keeps setting the macOS `hidden`
  flag on files inside `.venv`. Python 3.13's `site.py` skips hidden `.pth` files, and editable
  installs depend on `_editable_impl_agri_ml.pth`. Neither the Hatchling config nor `pyproject.toml`
  was at fault.
- **Fix:** the venv now lives at `~/.venvs/agri-ml-service`, outside iCloud, and `ml-service/.venv`
  is a symlink to it. `pyproject.toml` is unchanged. Running `chflags nohidden` only works until
  the next sync. The README troubleshooting section covers this.

---

## Stage 1: Dataset audit (first pass done)

25 sources audited with small test requests only (see `docs/datasets/DATASET_CATALOGUE.md`).

- **VERIFIED (real data returned):**
  - S01 data.gov.in district crop production (1997–2014)
  - S06 data.gov.in historical daily mandi prices (82M rows, updated daily, no arrivals)
  - S07 data.gov.in current-day mandi prices
  - S11 IMD gridded rainfall
  - S14 NASA POWER
  - S15 Open-Meteo
  - S17 SoilGrids
- **BLOCKED:**
  - S03 DES APY portals (timeouts)
  - S10 eNAM (no official export/API)
  - S13 IMD API (HTTP 401, key or whitelisting needed)
  - S16 Soil Health Card (no export)
- **CANDIDATE:** UPAg, ICRISAT DLD, AGMARKNET 2.0, CEDA API (has arrival quantities; JWT
  required), IMD Tmax/Tmin, Sentinel-2, Landsat/MODIS, Earth Engine, PlantVillage (CC BY-SA 3.0 on
  Hugging Face / CC0 on Mendeley), PlantDoc (CC BY 4.0), HCES (**PROXY**).
- **NOT_SUITABLE:** FAOSTAT (national only), miscellaneous old price series.

### Key findings
- The district production data (S01) **ends in 2014** (2015 partial). Recent years need UPAg (unverified).
- No verified source has **mandi arrival quantities**. S06/S07 are prices only.
- No observed demand data exists. All demand signals are **PROXIES**.
- Tomato has **no production data** in S01 (0 UP rows), so it can't be a supply target.
- All data.gov.in tests used the public **sample key**, which is repeatedly rate-limited.

### Proposed MVP (needs team agreement)
- **Region:** Uttar Pradesh.
- **Crops:** Potato, Wheat, Onion (tentative; Rice as the alternative).
- **First task:** supply forecasting baseline as a historical back-test (1997–2014).

### Blocking actions (team)
1. Register a data.gov.in API key.
2. Confirm UPAg district APY export (2015–2025).
3. Register for the CEDA API and confirm its license.
4. Decide commercial vs. non-commercial use (affects Open-Meteo, Earth Engine, IMD).
5. Request IMD API access if official forecasts are required.

## Not done (intentionally)
- No datasets downloaded; no synthetic data created.
- No models, baselines or inference wrappers.
- No prediction endpoints; no Spring Boot integration.

## Decisions
1. **Resolved:** `REGIONAL_ESTIMATE` (backend) is a **source**, not a data classification.
   Source and `dataClassification` are separate concepts. ML keeps OBSERVED, FORECAST, ESTIMATED,
   MODEL_PREDICTION and SYNTHETIC.
2. **Resolved:** contracts live in `ml-service/docs/ml-contracts/`.
3. **Open:** MVP region and crops (Stage 2). Proposal: Uttar Pradesh; Potato, Wheat, Onion. Agree with the backend and frontend team.
4. **Open:** register a data.gov.in API key for the team. The sample key is rate-limited.
5. **Open:** the top-level `README.md` doesn't mention `ml-service/` yet (outside ML scope).

## Next recommended step
Once the team API key exists and the region/crops are agreed: write a catalogue entry
(`docs/datasets/<name>.md`) for S01, pull UP potato/wheat district data (small, filtered), run the
data audit and EDA, then build the supply baselines (last value, moving average, historical mean)
with a time-based split. No model training before that audit.
