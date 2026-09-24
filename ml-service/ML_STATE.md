# ML_STATE

Last updated: 2026-09-24

## Current stage

**Stage 0: ML foundation.** Work in progress on branch `ml-branch`.

## Done

- Python project (`pyproject.toml`) with core, `ml` and `dev` dependency groups. There is no PyTorch or geospatial stack yet.
- `agri_ml` package with settings (`AGRI_ML_*` env vars) and a shared `DataClassification` enum.
- FastAPI app with `GET /health` returning `status`, `service`, `version`, `environment` and `loadedModels`.
- Tests for the health endpoint and the schemas.
- MLflow local SQLite tracking store and an environment check script (`scripts/check_mlflow.py`).
- Templates for the dataset catalogue entry, the model card and the API contract.

## Verified environment (2026-09-24)

Python 3.13.3 on macOS arm64, with pandas 3.0.6, NumPy 2.5.3, scikit-learn 1.9.1, XGBoost 3.4.1
(needs `brew install libomp`), MLflow 3.16.1 and FastAPI 0.141.1. Full pin list: `requirements.lock`.

Checks that passed: `pytest` (4 tests), `ruff check`, `uvicorn` serving `/health`, and an MLflow
experiment plus run created.

## Not done (intentionally)

- No datasets have been downloaded or catalogued.
- No models: none trained, no baselines, no inference wrappers.
- No prediction endpoints.
- No backend integration.

## Models

None.

## Datasets

None catalogued.

## Open questions / decisions needed

1. **Data classification enum alignment.** ML uses `ESTIMATED` (per CLAUDE.md). The backend
   platform list uses `REGIONAL_ESTIMATE`, and `SoilProfile` uses `ESTIMATED`. The two lists
   must agree before the first prediction contract.
2. **Contracts location.** CLAUDE.md says `docs/ml-contracts/`. The foundation task asked for
   `ml-service/docs/contracts/`, which is what currently exists. Confirm with the backend team,
   or move the folder to the repo root if the backend should own it jointly.
3. **MVP region and crops** (Stage 2) are not yet agreed with the backend and frontend team.
4. **Backend precondition.** backend/CLAUDE.md §27 says the Python service should not be created
   until the corresponding backend contracts are defined. This foundation adds only `/health`
   and no prediction endpoints, so it doesn't break that rule. Prediction endpoints still wait
   for agreed contracts.
5. **Top-level README** still lists only `frontend/` and `backend/`. `ml-service/` should be
   added. It was not edited here because it sits outside ml-service.

## Next recommended step

Stage 1: dataset catalogue. Research candidate sources (government agriculture statistics,
mandi prices and arrivals, IMD weather, Soil Health Card, Earth Engine/Sentinel/Landsat). Verify
access, licensing and granularity for each, and write one `docs/datasets/<name>.md` per source.
Download nothing large.
