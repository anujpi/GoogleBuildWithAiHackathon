# ML Service — Agricultural Intelligence Platform

The prediction layer of the platform. **ML predicts. Spring Boot decides. LLM explains.**

This service returns structured predictions with provenance and version metadata to the
Spring Boot backend. It does not make the final decision about what to plant, and it doesn't
produce advisory text. See [`CLAUDE.md`](CLAUDE.md) for the engineering rules and
[`ML_TEAM_PLAN.md`](ML_TEAM_PLAN.md) for the staged roadmap.

Current stage: **Stage 0 — foundation**. No models, datasets or prediction endpoints exist yet.
See [`ML_STATE.md`](ML_STATE.md).

## Setup

Requires Python 3.11+.

```bash
cd ml-service
python3 -m venv .venv
source .venv/bin/activate
pip install -e ".[ml,dev]"
```

On macOS, XGBoost needs the OpenMP runtime: `brew install libomp`.

`requirements.lock` has the exact versions that were verified (`pip freeze`). To reproduce that
environment, run `pip install -r requirements.lock && pip install -e . --no-deps`.

**`ModuleNotFoundError: No module named 'agri_ml'` after `pip install -e .` (macOS):**
Python 3.13+ skips any `.pth` file that has the macOS `hidden` flag (see `site.py`), and every
editable install relies on one (`_editable_impl_agri_ml.pth`). To confirm, run
`python -v -c pass 2>&1 | grep "Skipping hidden"`. If the project sits in an iCloud-synced
folder (Desktop or Documents), the sync keeps re-applying that flag. Running `chflags` only fixes
it until the next sync. Instead, keep the venv outside the synced folder and link it:

```bash
python3 -m venv ~/.venvs/agri-ml-service
ln -s ~/.venvs/agri-ml-service .venv      # .venv/bin/python keeps working
.venv/bin/pip install -r requirements.lock && .venv/bin/pip install -e . --no-deps
```

Dependency groups in `pyproject.toml`:

| Group | Contents | Install |
|---|---|---|
| core | FastAPI, Uvicorn, Pydantic, pydantic-settings | `pip install -e .` |
| `ml` | pandas, NumPy, scikit-learn, XGBoost, MLflow, matplotlib, seaborn | `.[ml]` |
| `dev` | pytest, httpx, ruff | `.[dev]` |

PyTorch/torchvision and the geospatial libraries (Earth Engine, GeoPandas, rasterio) are left out
on purpose. Add them in the phase that needs them.

## Run

```bash
uvicorn agri_ml.api.app:app --reload --port 8000
curl localhost:8000/health
```

Port 8000 avoids the backend (8080), the frontend (5173) and Postgres (5433). Interactive docs
are at `/docs`. Don't expose this service publicly. Only the backend should call it.

## Test and lint

```bash
pytest
ruff check .
```

## MLflow

Tracking uses a local SQLite store by default: `ml-service/mlflow.db`, with artifacts in
`ml-service/mlartifacts/`. Both are git-ignored. MLflow 3 refuses the old `./mlruns` file store.

```bash
python scripts/check_mlflow.py     # creates an experiment and an empty run
mlflow ui --backend-store-uri sqlite:///mlflow.db --port 5000
```

To use a tracking server, set `AGRI_ML_MLFLOW_TRACKING_URI`.

## Configuration

Environment variables use the `AGRI_ML_` prefix. You can also put them in `ml-service/.env`,
which is git-ignored.

| Variable | Default |
|---|---|
| `AGRI_ML_ENVIRONMENT` | `local` |
| `AGRI_ML_MLFLOW_TRACKING_URI` | `sqlite:///…/ml-service/mlflow.db` |
| `AGRI_ML_MLFLOW_ARTIFACT_ROOT` | `file://…/ml-service/mlartifacts` |
| `AGRI_ML_ARTIFACTS_DIR` | `ml-service/artifacts` |
| `AGRI_ML_DATA_DIR` | `ml-service/data` |

## Layout

```text
ml-service/
├── src/agri_ml/
│   ├── api/        FastAPI app (currently only /health)
│   ├── config/     settings
│   └── schemas/    shared Pydantic types (DataClassification, health)
├── scripts/        runnable utilities (MLflow check)
├── tests/
├── notebooks/      exploratory/ and experiments/ (see notebooks/README.md)
└── docs/
    ├── datasets/     dataset catalogue (one file per dataset, from _TEMPLATE.md)
    ├── model-cards/  one card per production-capable model
    └── ml-contracts/ ML ⇄ Spring Boot API contracts
```

These packages from the planned layout get added when their phase starts, not before:
`common/`, `datasets/`, `features/`, `models/{supply,demand,crop,disease,anomaly}/`,
`training/`, `evaluation/`, `inference/`.

`data/`, `artifacts/`, `mlflow.db` and `mlartifacts/` are git-ignored. Never commit datasets, model binaries or credentials.
