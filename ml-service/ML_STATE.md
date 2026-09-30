# ML_STATE

Last updated: 2026-09-30. Branch: `ml-branch`. The supply work is committed (`ec2ef04`, `9e94c97`);
the hackathon additions below (disease, suitability, demand, anomaly) are **not committed**.

## Hackathon MVP additions (2026-09-30, not committed)
- **Disease** `POST /v1/predict/disease`: MobileNetV3-Large (ImageNet) as a frozen feature extractor with a linear-probe head
  (`scripts/train_disease_probe.py`, CPU) on 13
  PlantVillage Potato/Tomato classes, using a leakage-aware split (`docs/datasets/plantvillage.md`). Artifact
  `artifacts/disease/disease-mnv3-probe-v1/`. Metrics and limits are in `docs/model-cards/disease.md`.
  Training on MPS stalled four times (process stuck in state U). Each stall coincided with other heavy processes
  on this 8 GB machine: memory pressure, with 520k page-outs. So the served model is the CPU linear probe. Full fine-tuning
  (`scripts/train_disease.py`) reached 0.985 validation macro-F1 after 2 epochs but never finished; no artifact was saved.
- **Crop suitability** `POST /v1/predict/crop-suitability`: a transparent weighted evidence index over
  crop_yield (no trained model, no accuracy claim).
- **Demand** `POST /v1/predict/demand`: FAOSTAT FBS India "domestic supply quantity" (a proxy). Naive and
  linear-trend baselines are both back-tested and the better one is picked. State values use the Census 2011
  population share. The data lives in `reference_data/` (committed, ~65 KB, see its README).
- **Anomaly** `POST /v1/predict/anomaly`: robust z-score (median/MAD) on the last point of any series.
- Contract: `docs/ml-contracts/intelligence.md`. Tests: `tests/test_intelligence.py`.
- The data.gov.in API (mandi prices) refused connections from this machine on 2026-09-30, so no price data
  is used.

## Current stage

- **Stage 0: ML foundation.** Done.
- **Stage 1: Dataset catalogue + data source audit.** First pass done (2026-09-24). See
  `docs/datasets/DATASET_CATALOGUE.md` and `docs/datasets/DATASET_PRIORITY.md`.
- **MVP supply model.** A state-level model is trained on the Kaggle crop_yield dataset and
  served at `POST /v1/predict/supply` (see the sections below). Backend agreement on the
  contract is pending.

The S01/API-key blocking actions listed under Stage 1 no longer block the MVP supply model.

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

## crop_yield dataset prepared (2026-09-28, not committed)
- Source identified: Kaggle "Agricultural Crop Yield in Indian States Dataset" (`akshatgupta7`),
  CC-BY-SA-4.0. The local file matches Kaggle's listed size exactly (1,620,945 bytes).
- Audit of the real file: 19,689 rows × 10 columns, 0 NaN, 0 duplicate rows or keys, 30 states,
  55 crops, 6 seasons, 1997–2020 (**2020 = Uttarakhand only**).
- Main findings:
  - Fertilizer and Pesticide are Area × a per-year rate (ESTIMATED)
  - Yield ≠ Production ÷ Area for about 80% of rows
  - Coconut uses a different Production unit
  - 111 State × Crop pairs switch season label (UP Potato: Whole Year → Rabi in 2004)
- Units are stated by the publisher only; none are verified.
- Added `quality_flags()`, `season_label_changes()` and `series_year_gaps()` to
  `datasets/crop_yield.py`, plus `scripts/prepare_crop_yield.py`. The prepare script writes
  cleaned and flagged output; it drops no rows and never overwrites.
- The audit script is extended. The tests cover the flags, the prepare script, and invariants
  checked against the real file.
- Full details: `docs/datasets/crop_yield.md`.
- The original CSV lives in a team member's `~/Downloads/`, and a copy is at
  `data/raw/crop_yield.csv` (git-ignored).

## MVP supply model trained (2026-09-28, not committed)
- **Decision (team):** the Kaggle crop_yield dataset is the MVP supply-training source. S01 and
  the data.gov.in API key are no longer blockers.
- **Pipeline:** the existing supply code, changed from district to **state** series keys. The
  new `crop_yield.to_supply_frame()` maps the columns and applies the documented exclusions
  (1,194 rows).
- **Training:** `scripts/train_supply.py` now reads the Kaggle CSV. Split: train ≤ 2013,
  validate 2014–16, test 2017–19.
- **Artifact:** `artifacts/supply/supply-xgb-v1/`, MLflow run `09afff2f322842c7ac51aef6c4c12e76`.
- **Test results:** WAPE 9.27% against 9.53% for the best baseline (area × last year's yield).
  RMSE is about 2.6% worse. The model beats that baseline on only 46.8% of rows, so the gain is
  **marginal** and whether to adopt the model is open for the team. The 80% interval covers 81.9%
  on test.
- **API:** the response is unchanged, apart from two optional provenance fields
  (`trainingDataSource`, `spatialGranularity`).
- **Docs:** contract `docs/ml-contracts/supply.md`; model card `docs/model-cards/supply.md`.
- **Fixes:** the S01 `clean()` now normalises column names (the test that was failing passes).
  The S01 code is kept but no longer used for training.
- **Tests:** 45 pass, including tests against the real artifact.

## Not done (intentionally)
- No synthetic data used in place of real data (the test fixtures are labelled SYNTHETIC).
- No Spring Boot integration. There is no `MlClient` in the repository yet.
- No leave-state-out evaluation, and no weather or price features.

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
