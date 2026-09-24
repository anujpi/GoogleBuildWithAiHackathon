# CLAUDE.md — Agricultural Intelligence Platform / ML Service

## Purpose
This file is the engineering contract for the ML/Data Science service of the Agricultural Intelligence Platform.

The ML service is separate from the React frontend and Spring Boot backend.

### Ownership
ML owns:
- dataset discovery and audit
- preprocessing and feature engineering
- model training and evaluation
- experiment tracking
- model/version management
- inference code and ML API contracts
- model artifacts and ML documentation

ML does not own:
- React UI
- Spring Boot domain/business logic
- authentication/authorization
- the final agricultural decision
- LLM advisory wording

### Core boundary
**ML predicts. Spring Boot decides. LLM explains.**

ML returns structured predictions with evidence/metadata. It must not directly decide what a farmer should plant.

---

## Product context
The platform combines:
- soil
- weather
- satellite/environmental signals
- agricultural history
- crop intelligence
- supply
- demand
- market intelligence
- production and market risk
- scenario analysis
- crop-health intelligence
- regional supply-demand coordination
- explainable advisory

Core workflow:

Farm
→ Soil / Weather / Satellite / Agricultural History
→ Crop Intelligence
→ Expected Production
→ Supply Forecast
→ Demand Forecast
→ Supply-Demand Gap
→ Market Risk + Production Risk
→ Decision Engine
→ Explainability
→ AI Advisory
→ Scenario Analysis
→ Regional Supply/Demand Coordination

---

## ML capabilities
The ML system will eventually support:

1. Supply forecasting
2. Demand forecasting
3. Crop suitability / yield prediction
4. Crop disease / health diagnosis
5. Anomaly detection
6. Scenario-supporting predictions

### Supply forecasting
Potential inputs:
- historical production
- cultivated area
- historical yield
- weather
- crop
- season
- satellite/environment indicators
- crop-health signals

Return prediction, unit, period, uncertainty/confidence, and model metadata.

### Demand forecasting
Potential inputs:
- historical consumption proxies
- market signals
- mandi arrivals
- price trends
- seasonality
- regional signals

Never represent a demand proxy as actual retail sales.

### Crop suitability / yield
Potential inputs:
- soil
- weather
- season
- location
- historical yield/production
- satellite/environment indicators

Return candidate-crop evidence, not the final recommendation.

### Disease/health
image → preprocessing → model → class probabilities → diagnosis + confidence.
Backend later adds crop/weather/context.

### Anomalies
Potential targets:
- price
- arrivals
- weather
- crop health
- supply
- demand

### Scenarios
Support altered assumptions such as rainfall, temperature, supply, demand and production; do not mutate historical observations.

---

## Technology
Preferred stack:
- Python 3.x
- FastAPI
- Pydantic
- pandas
- NumPy
- scikit-learn
- XGBoost
- PyTorch
- torchvision
- MLflow
- matplotlib
- seaborn

Possible later:
- Earth Engine Python API
- GeoPandas
- rasterio
- geemap
- DVC
- object storage

Do not add every library on day one.

---

## Repository structure
Recommended:

```text
ml-service/
├── CLAUDE.md
├── README.md
├── ML_STATE.md
├── pyproject.toml
├── src/
│   └── agri_ml/
│       ├── api/
│       ├── common/
│       ├── config/
│       ├── datasets/
│       ├── features/
│       ├── models/
│       │   ├── supply/
│       │   ├── demand/
│       │   ├── crop/
│       │   ├── disease/
│       │   └── anomaly/
│       ├── training/
│       ├── evaluation/
│       ├── inference/
│       └── schemas/
├── notebooks/
│   ├── exploratory/
│   └── experiments/
├── scripts/
├── tests/
├── artifacts/
└── docs/
```

Do not create unused folders just to fill the tree.

---

## Data principles
### Never fabricate real-world data
If unavailable:
- document the gap
- use a validated proxy where appropriate
- otherwise use explicitly synthetic data for development/testing only

Synthetic data must never be presented as observed real-world measurements.

### Data classification
Use explicit classifications where applicable:
- OBSERVED
- FORECAST
- MODEL_PREDICTION
- ESTIMATED
- SYNTHETIC

### Provenance
Record where relevant:
- source
- source URL/identifier
- access date
- observation period
- geographic granularity
- units
- license/usage terms
- preprocessing steps
- version

---

## Dataset discipline
Every dataset needs a catalogue entry containing:

```text
Dataset name
Source
URL / identifier
License
Geographic coverage
Temporal coverage
Granularity
Target
Features
Units
Missing values
Known biases
Update frequency
Preprocessing
Train/validation/test usage
Version
```

Do not train before understanding what one row represents, the time/geographic unit, leakage risks, and whether multiple rows represent the same entity/period.

---

## Time-series leakage
Forecasting data must respect time.

Do not randomly split future observations into training.

Preferred:

```text
TRAIN | VALIDATION | TEST
past -----------------> future
```

Document exact boundaries.

---

## Spatial leakage
Agricultural data is spatial. Nearby regions can share signals and conditions.

When appropriate, evaluate by:
- time
- region
- crop
- unseen-region scenarios

Document the evaluation design.

---

## Baseline-first rule
Every prediction task must establish a simple baseline before a complex model.

Examples:
- supply: previous period / seasonal average / moving average
- demand: previous period / seasonal naive
- yield: historical regional mean
- disease: simple baseline classifier where meaningful

Adopt complex models only when they demonstrate meaningful improvement.

---

## Model lifecycle

```text
Data audit
→ EDA
→ Feature engineering
→ Baseline
→ Candidate models
→ Time/spatial-aware validation
→ Error analysis
→ Experiment tracking
→ Final evaluation
→ Model artifact
→ Inference wrapper
→ API contract
```

Do not skip error analysis.

---

## Experiment tracking
Use MLflow. Track:
- run ID
- dataset version
- feature version
- model version
- hyperparameters
- metrics
- train/validation/test periods
- random seed where relevant
- artifact references

Do not rely only on notebooks.

---

## Model versioning
Every production-capable model should have:
- modelName
- modelVersion
- featureVersion
- datasetVersion
- trainedAt
- trainingPeriod
- evaluationPeriod
- metrics

Do not silently overwrite a trained model.

---

## Evaluation
Possible forecasting metrics:
- MAE
- RMSE
- MAPE/SMAPE where appropriate
- R² where informative

Classification:
- precision
- recall
- F1
- confusion matrix
- per-class metrics
- PR-AUC/ROC-AUC where appropriate

Do not report only accuracy for imbalanced disease datasets.

Inspect worst errors, class imbalance, geographic failures, temporal failures and edge cases.

---

## Uncertainty
Do not turn predictions into absolute claims.

Where possible expose:
- confidence
- prediction interval
- uncertainty
- data-quality limitations

Never fabricate a confidence number just to fill a response field.

---

## Explainability
Where feasible use:
- feature importance
- permutation importance
- SHAP
- class probabilities
- error analysis

Do not claim causal relationships from feature importance alone.

Prefer wording such as “important predictor” or “associated signal” when causality is not established.

---

## ML API
After models stabilize, FastAPI may expose:

```text
POST /v1/predict/supply
POST /v1/predict/demand
POST /v1/predict/crop-suitability
POST /v1/predict/disease
POST /v1/predict/anomaly
GET  /health
```

Return structured JSON only. Do not return natural-language advice.

Every prediction should include, where relevant:
- prediction
- unit
- confidence/uncertainty
- dataClassification
- modelVersion
- featureVersion
- datasetVersion
- generatedAt

---

## Integration boundary

```text
Spring Boot
    ↓
ML client/adapter
    ↓
FastAPI
    ↓
Model
    ↓
Structured prediction
    ↓
Spring Boot Decision Engine
```

The ML service should not directly write to the main application database unless explicitly designed later.

---

## Contract-first integration
Before implementing a new ML endpoint:
1. Define the contract in `docs/ml-contracts/`.
2. Agree on request fields.
3. Agree on response fields.
4. Define units.
5. Define nullability.
6. Define error cases.
7. Define model/version metadata.
8. Implement.

---

## Testing
Cover:
- schema validation
- preprocessing
- feature transformations
- inference
- edge cases
- invalid inputs
- model loading failures
- API responses

Use ML evaluation experiments—not only unit tests—to assess model quality.

---

## Reproducibility
Record:
- Python version
- dependency versions
- random seeds
- dataset version
- feature version
- model version
- training configuration

Do not depend on untracked notebook state.

---

## Security
Never commit:
- API keys
- cloud credentials
- private dataset credentials
- model registry secrets

Do not expose inference endpoints publicly without a planned integration/security layer.

---

## Collaboration
ML should work mainly inside:
- `ml-service/`
- `docs/ml-contracts/`

Do not modify Spring Boot business logic directly.

If backend changes are needed:
1. document the contract
2. communicate the change
3. implement only after agreement

Suggested branches:
- `ml/foundation`
- `ml/data-catalogue`
- `ml/supply-baseline`
- `ml/supply-model`
- `ml/demand-baseline`
- `ml/demand-model`
- `ml/crop-suitability`
- `ml/disease-v1`
- `ml/fastapi-inference`

---

## Current task
Do not train a model immediately.

First:
1. inspect the repository
2. create the ML service directory if needed
3. create `CLAUDE.md`
4. create Python project setup
5. create package structure
6. create `ML_STATE.md`
7. create `docs/ml-contracts/`
8. create dataset catalogue template
9. create README
10. report the plan

Wait before choosing or training a model.

---

## Definition of done
An ML feature is complete when:
- dataset/source is documented
- preprocessing is reproducible
- baseline exists
- candidate model evaluation is documented
- test evaluation is correctly designed
- errors are analyzed
- model metadata is recorded
- inference wrapper exists
- API contract is documented
- tests pass
- model artifact is versioned
- known limitations are documented
