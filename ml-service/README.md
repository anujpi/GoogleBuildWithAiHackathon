# ML Service — Agricultural Intelligence Platform

The prediction layer of the platform. **ML predicts. Spring Boot decides. LLM explains.**

This service serves structured predictions with provenance to the Spring Boot backend (and only to
it). It never makes the planting decision and never produces advisory text. The engineering rules
are in [`CLAUDE.md`](CLAUDE.md), the current status is in [`ML_STATE.md`](ML_STATE.md), and the
API contract is `MASTER_SPEC.md` §9 (repo root), implemented as in [`docs/ml-contracts/`](docs/ml-contracts/README.md).

| Capability | Status |
|---|---|
| District supply estimate (`POST /v1/predict/supply`) | Pipeline, API and tests done. **No artifact yet: waiting for S01 data (API key)** |
| Market prices / anomalies | Not started (milestone M4) |

## Setup (Windows, macOS, Linux)

This needs Python 3.11 or newer. It was verified on Windows 11 with Python 3.14.7.

```bash
cd ml-service
python -m venv .venv
# Windows (Git Bash):  source .venv/Scripts/activate      PowerShell: .venv\Scripts\Activate.ps1
# macOS / Linux:       source .venv/bin/activate
pip install -r requirements.lock      # exact, verified versions (platform markers inside)
pip install -e . --no-deps
```

`pip install -e ".[ml,dev]"` also works, but it resolves the newest versions allowed by `pyproject.toml`.

- **macOS:** XGBoost needs `brew install libomp`.
- **iCloud:** if the repo lives in an iCloud-synced folder and `import agri_ml` fails after an
  editable install, iCloud has hidden the `.pth` file. Keep the venv outside the synced folder and
  symlink it.

## Data

Raw data goes under `data/`, which is git-ignored and never committed. The only dataset is S01
([`docs/datasets/s01_up.md`](docs/datasets/s01_up.md)).

```bash
# A registered data.gov.in key is required (https://data.gov.in, My Account > API key).
export DATA_GOV_IN_API_KEY=<your key>              # PowerShell: $env:DATA_GOV_IN_API_KEY="<key>"
python scripts/download_s01_crop_production.py     # UP; Potato Wheat Onion Maize -> data/raw/s01_crop_production/
```

## Train and evaluate

```bash
python scripts/train_supply.py --model-version supply-xgb-v1
```

The download script exits without writing anything if `DATA_GOV_IN_API_KEY` is unset, and it writes the
manifest only after every crop is complete. Training (`agri_ml.training.pipeline`) aborts with a
clear message if the manifest is missing, incomplete or not from a registered key, or if a scope
crop has no rows. It never trains a served model from test fixtures.

This cleans the data, scopes it, trains, evaluates against the baselines, selects the served
method, and writes the artifact to `artifacts/supply/supply-xgb-v1/`:

| File | Contents |
|---|---|
| `model.json` | XGBoost booster |
| `metadata.json` | Versions, periods, metrics, selection, interval, scope, cleaning report, source manifest |
| `series.parquet` | The exact cleaned series used, also served as history |
| `scored.csv` | Validation and test predictions of every method |
| `error_analysis.json` | Error breakdowns |
| `evaluation_report.md` | Generated evaluation report |

The script also logs a run to MLflow (`mlflow.db`, `mlartifacts/`). It never overwrites an existing
version: bump `--model-version` instead. Artifacts are git-ignored; the demo artifact is produced
by these two commands and isn't committed.

## Run

```bash
uvicorn agri_ml.api.app:app --port 8000
curl localhost:8000/health        # status UP + per-model READY / NOT_READY
```

Port 8000 is what the backend expects (`ML_SERVICE_BASE_URL`). Interactive docs are at `/docs`.
Don't expose this service publicly.

## Test and lint

```bash
pytest            # the real-artifact test is skipped until an artifact exists
ruff check .
```

To test against the real service end to end from the backend (with the ML service running):

```bash
cd ../backend && ML_E2E_BASE_URL=http://localhost:8000 ./mvnw -Dtest=MlServiceLiveTest test
```

The backend rejects a served history that isn't `OBSERVED`, so this only passes fully against a
real S01 artifact.

## Configuration

Environment variables use the `AGRI_ML_` prefix. They can also go in `ml-service/.env`, which is git-ignored.

| Variable | Default |
|---|---|
| `AGRI_ML_ENVIRONMENT` | `local` |
| `AGRI_ML_DATA_DIR` | `ml-service/data` |
| `AGRI_ML_ARTIFACTS_DIR` | `ml-service/artifacts` |
| `AGRI_ML_SUPPLY_MODEL_DIR` | `ml-service/artifacts/supply/supply-xgb-v1` |
| `AGRI_ML_MLFLOW_TRACKING_URI` | `sqlite:///…/ml-service/mlflow.db` |
| `AGRI_ML_MLFLOW_ARTIFACT_ROOT` | `file://…/ml-service/mlartifacts` |
| `DATA_GOV_IN_API_KEY` | none (read by the download script only; MASTER_SPEC §14) |

## Layout

```text
src/agri_ml/
  reference.py     canonical ids (stateId, districtId, cropId, Season) and scope
  datasets/        S01 loading, validation, cleaning, dataset version
  features/        supply features (shared by training and inference)
  models/          supply methods: MODEL + baselines (shared by evaluation and serving)
  training/        pipeline.py (stage order, manifest gate), supply.py (split, training,
                   acceptance rule, interval, spatial check, artifact)
  evaluation/      generated evaluation report
  inference/       artifact loading, request validation, prediction, provenance
  schemas/         Pydantic contract (common, supply, reference, health, errors)
  api/             FastAPI app and error envelope
scripts/           download_s01_crop_production.py, train_supply.py
docs/              ml-contracts/, datasets/, model-cards/
```
