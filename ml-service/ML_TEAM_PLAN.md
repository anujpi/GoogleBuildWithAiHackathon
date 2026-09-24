# ML TEAM PLAN — Agricultural Intelligence Platform

## 0. Mission

The ML team owns the predictive intelligence layer of the Agricultural Intelligence Platform.

The goal is not to build disconnected ML demos. The goal is to build a small, reproducible, explainable prediction platform that can be integrated cleanly into Spring Boot.

### System boundary

**ML predicts. Spring Boot decides. LLM explains.**

```text
React Frontend
      |
      v
Spring Boot Backend
      |
      +----------------------+
      |                      |
      v                      v
PostgreSQL/PostGIS       ML Service
                         FastAPI
                            |
             +--------------+---------------+
             |              |               |
             v              v               v
          Supply          Demand          Disease
          Model           Model           Model
             |
             v
       Crop / Yield Models
             |
             v
       Spring Boot Decision Engine
```

---

# 1. Responsibilities

## ML team owns
- data discovery
- source audit
- data preprocessing
- feature engineering
- model training
- evaluation
- experiment tracking
- model versioning
- model artifacts
- inference service
- ML documentation

## Shared with backend
- prediction contracts
- units
- confidence/uncertainty definitions
- model/version metadata
- integration tests

## ML does not own
- authentication
- farm ownership
- Spring Boot entities
- final crop decision/business rules
- React UI
- LLM advisory wording

---

# 2. Target ML capabilities

The full project should eventually support:

1. Supply forecasting
2. Demand forecasting
3. Crop suitability / yield prediction
4. Crop disease / health detection
5. Anomaly detection
6. Scenario-supporting predictions

These are intended to support the agricultural decision workflow described in the project brief.

---

# 3. Overall development order

```text
ML Foundation
      ↓
Dataset Catalogue
      ↓
MVP Region + Crop Selection
      ↓
Common Data Model
      ↓
Supply Forecasting
      ↓
Demand Forecasting
      ↓
Crop Suitability / Yield
      ↓
Disease Detection
      ↓
Anomaly Detection
      ↓
Scenario Support
      ↓
FastAPI Inference
      ↓
Spring Boot Integration
      ↓
End-to-End Evaluation
      ↓
Production Hardening
```

Do not train all models simultaneously.

---

# 4. Stage 0 — ML foundation

## Goal

Create a reproducible ML workspace before model development.

## Deliverables

```text
ml-service/
├── CLAUDE.md
├── README.md
├── ML_STATE.md
├── pyproject.toml
├── src/
├── notebooks/
├── scripts/
├── tests/
├── artifacts/
└── docs/
```

Set up:
- Python environment
- dependency management
- FastAPI skeleton
- health endpoint
- MLflow
- test structure
- basic configuration

### Definition of done

```text
Python environment works
FastAPI starts
Tests run
MLflow experiment can be created
```

No real model yet.

---

# 5. Stage 1 — Dataset catalogue

This is the first real ML task.

Create a catalogue with:

```text
Dataset
Source
URL / identifier
License
Geographic coverage
Temporal coverage
Spatial granularity
Temporal granularity
Target
Features
Units
Missingness
Known biases
Update frequency
Usage notes
Version
```

Organize research into:

### Agriculture
- production
- area
- yield

### Market
- prices
- mandi arrivals

### Weather
- observations
- forecasts
- warnings

### Soil
- pH
- N/P/K
- micronutrients

### Satellite/environment
- vegetation indicators
- environmental indicators

### Demand proxies
- consumption
- market signals
- validated indirect demand measures

The project brief identifies government agriculture statistics, market/mandi data, IMD weather, Soil Health Card data, and Google Earth Engine/Sentinel/Landsat as candidate sources. The actual accessibility/licensing/API availability must be verified before implementation.

---

# 6. Stage 2 — Select the MVP region and crops

Agree with the main backend/frontend team on:

```text
Region
Crop A
Crop B
Crop C
```

Selection criteria:
- sufficient historical data
- usable market data
- usable weather data
- usable production data
- consistent geographic unit
- meaningful demo value

Do not select crops only because they are popular.

The source concept recommends a focused MVP with one region/state and 2–3 crops.

---

# 7. Stage 3 — Common data model

Standardize dimensions:

```text
region_id
crop_id
date / period
season
```

Standardize units. Examples:

```text
production → tonnes
yield → tonnes/hectare
area → hectares
price → currency/unit
rainfall → mm
temperature → Celsius
```

Never silently mix units.

Every transformed dataset should record its version and preprocessing steps.

---

# 8. Stage 4 — Supply forecasting

## Question

How much of crop X is likely to be available in region Y in the future period?

## Potential inputs

Start with:
- historical production
- cultivated area
- historical yield
- season
- weather
- crop
- validated production signals

Later consider:
- satellite indicators
- crop-health signals
- soil/environment features

## Pipeline

```text
Data audit
→ EDA
→ baseline
→ feature engineering
→ candidate models
→ time-aware validation
→ error analysis
→ MLflow
→ model artifact
→ inference wrapper
```

## Baselines

At minimum compare against a simple baseline such as:
- last period
- seasonal naive
- moving average
- historical regional mean

## Candidate models

Start with transparent baselines and tree-based approaches such as XGBoost if justified by the data.

Do not jump directly to deep learning.

## Output contract

```json
{
  "crop": "tomato",
  "regionId": "region-123",
  "forecastPeriod": "2027-KHARIF",
  "predictedSupply": 205000,
  "unit": "TONNES",
  "confidence": 0.72,
  "dataClassification": "MODEL_PREDICTION",
  "modelVersion": "supply-v1",
  "featureVersion": "supply-features-v2",
  "datasetVersion": "production-2026-09"
}
```

Do not fake confidence. If the model does not support meaningful confidence, return an appropriate uncertainty representation instead.

---

# 9. Stage 5 — Demand forecasting

## Question

How much of crop X is likely to be needed in region Y?

## Important limitation

Fine-grained retail/POS data may not be available.

Therefore explicitly distinguish:

```text
Observed demand
Demand proxy
Model prediction
Synthetic demonstration
```

A demand proxy must not be presented as actual retail sales.

## Potential inputs

- historical consumption proxy
- seasonality
- mandi arrivals
- prices
- regional production
- historical trends

## Pipeline

```text
Signal audit
→ EDA
→ demand baseline
→ lag/seasonal features
→ candidate model
→ time-aware validation
→ error analysis
→ MLflow
→ inference
```

## Output

Include:
- predicted demand
- unit
- forecast period
- signal type
- uncertainty/confidence
- model metadata

---

# 10. Stage 6 — Crop suitability / yield

## Question

How suitable is candidate crop X for a farm/region under the available environmental conditions?

## Inputs

- soil
- weather
- season
- location
- historical yield/production
- satellite/environment indicators where available

## Output

```text
crop
suitability
expectedYield
unit
confidence/uncertainty
featureContributions
modelVersion
featureVersion
datasetVersion
```

Example:

```text
Tomato → suitability 0.88
Chilli → suitability 0.81
Millet → suitability 0.73
```

This is **evidence**, not the final recommendation.

The Spring Boot Decision Engine will later combine it with:
- supply
- demand
- market pressure
- weather risk
- disease risk
- farm constraints

---

# 11. Stage 7 — Disease detection

Do after the tabular forecasting foundations are stable.

## Flow

```text
Image
 ↓
Data audit
 ↓
Preprocessing
 ↓
Train/validation/test
 ↓
Augmentation
 ↓
Transfer learning
 ↓
Evaluation
 ↓
Confusion matrix
 ↓
Error analysis
 ↓
Versioned model
```

Metrics:
- precision
- recall
- F1
- confusion matrix
- per-class performance

Do not rely on accuracy alone if classes are imbalanced.

Output:

```text
disease/stress class
confidence
class probabilities
modelVersion
```

Backend will later add contextual information such as crop, weather and location.

---

# 12. Stage 8 — Anomaly detection

Potential signals:
- price
- arrivals
- weather
- crop health
- supply
- demand

Start with simple statistical baselines.

Only adopt more complex anomaly models when they demonstrate useful performance.

Output:

```text
signal
period
anomalyScore
status
modelVersion
confidence where supported
```

---

# 13. Stage 9 — Scenario support

The product needs to answer scenarios such as:
- rainfall +20%
- demand +10%
- supply -15%

ML should provide prediction functions that accept controlled assumptions.

Return:

```text
baseline prediction
scenario prediction
difference
uncertainty
```

The backend Scenario Engine will orchestrate the overall scenario.

Do not mutate historical observations.

---

# 14. Stage 10 — FastAPI inference service

Once models are stable:

```text
POST /v1/predict/supply
POST /v1/predict/demand
POST /v1/predict/crop-suitability
POST /v1/predict/disease
POST /v1/predict/anomaly
GET  /health
```

The API returns structured JSON only.

No natural-language advice belongs here.

---

# 15. Stage 11 — ML contracts

Create:

```text
docs/ml-contracts/
├── supply.md
├── demand.md
├── crop-suitability.md
├── disease.md
└── anomaly.md
```

Each contract contains:

```text
Purpose
Endpoint
Request
Response
Units
Required fields
Optional fields
Null behavior
Confidence/uncertainty
Data classification
Model version
Feature version
Dataset version
Error cases
Example
```

This is the bridge between the ML team and the Spring Boot team.

---

# 16. Stage 12 — Spring Boot integration

Integration order:

```text
ML contract
   ↓
FastAPI endpoint
   ↓
Python tests
   ↓
Spring Boot ML client/adapter
   ↓
Integration test
   ↓
Decision Engine
```

Spring Boot remains responsible for:
- orchestration
- persistence
- authorization
- business rules
- final decisions

ML remains responsible for prediction.

---

# 17. MLflow workflow

Every serious experiment should be tracked.

Example:

```text
Experiment: supply_forecast_region

Run: xgboost-v3
Dataset: production-2026-09
Features: supply-features-v2
Training: 2018–2024
Validation: 2025
Test: 2026
Metrics: MAE, RMSE
Artifact: tracked model artifact
```

MLflow is the source for experiment history, not notebook memory.

---

# 18. Model cards

Each production-capable model should document:

```text
Model name
Purpose
Intended use
Not intended for
Training data
Evaluation data
Metrics
Known limitations
Biases
Geographic scope
Temporal scope
Version
Owner
```

Especially important for disease and crop-suitability models.

---

# 19. Data quality and uncertainty

Every model must document:
- missingness
- geographic coverage
- temporal coverage
- known data gaps
- uncertainty
- confidence limitations

Never fabricate a confidence value.

A model that performs well only on one region should not be represented as a nationwide model.

---

# 20. Collaboration protocol

When ML needs backend work:

1. Update the relevant ML contract.
2. Communicate the requested change.
3. Agree on the request/response shape.
4. Implement the change in the relevant branch.
5. Integration-test it.

Avoid direct edits to unrelated Spring Boot logic.

---

# 21. Git workflow

Suggested branches:

```text
main
│
├── ml/foundation
├── ml/data-catalogue
├── ml/supply-baseline
├── ml/supply-model
├── ml/demand-baseline
├── ml/demand-model
├── ml/crop-suitability
├── ml/disease-v1
├── ml/anomaly
└── ml/fastapi-inference
```

Use focused commits such as:

```text
chore: initialize ml service
feat: add dataset catalogue
feat: add supply baseline
feat: add supply xgboost model
feat: add demand forecast baseline
feat: add crop suitability model
feat: add disease classifier
feat: add fastapi inference endpoint
```

Never commit large datasets or model binaries directly to Git.

---

# 22. Tooling for the ML teammate

## Core tools
- Python
- VS Code or PyCharm
- Jupyter
- Git/GitHub
- Docker

## Core packages
- pandas
- NumPy
- scikit-learn
- XGBoost
- Pydantic
- FastAPI
- Uvicorn
- MLflow
- matplotlib
- seaborn

## Deep learning
- PyTorch
- torchvision

## Later, for geospatial/satellite work
- Earth Engine Python API
- GeoPandas
- rasterio
- geemap

Do not install everything up front. Add packages when the phase requires them.

---

# 23. Claude/tooling setup for the ML teammate

Recommended capabilities:

### GitHub integration
Use for:
- repository context
- branches/PRs/issues
- reviewing implementation changes

### Exa or equivalent research/search tool
Useful for:
- finding datasets
- finding papers
- comparing modelling approaches
- researching public agricultural data sources

### Hugging Face integration
Useful for:
- finding datasets
- inspecting models
- exploring Spaces
- researching pretrained vision models

### Jupyter
For exploratory analysis, visualization and experiments.

### MLflow
For experiment tracking and model comparison.

The ML teammate does **not** need the frontend-focused component/design tools used by the main frontend workflow.

---

# 24. What not to build

Do not build yet:
- Kubernetes
- Kafka
- vector databases
- distributed feature stores
- giant LLM pipelines
- huge transformer models without evidence they are needed
- generic agricultural chatbot
- fake retail datasets presented as real
- business decision logic inside Python
- direct writes into the main application database

---

# 25. Weekly/phase deliverables

For every ML phase, the teammate should deliver:

1. Code
2. Dataset/source notes
3. Notebook or reproducible experiment where useful
4. Evaluation results
5. Model artifact/version
6. MLflow run(s)
7. Contract update if needed
8. ML_STATE.md update
9. Known limitations
10. Next recommended step

---

# 26. Final end-to-end target

```text
Farm / Region
     ↓
Data preparation
     ↓
 ┌───────────────┬───────────────┬───────────────┐
 ↓               ↓               ↓
Supply          Demand       Crop/Health
Forecast        Forecast       Models
 ↓               ↓               ↓
 └───────────────┴───────────────┘
                 ↓
          Structured Predictions
                 ↓
             FastAPI ML
                 ↓
          Spring Boot Adapter
                 ↓
           Decision Engine
                 ↓
      Trade-offs + Risk + Action
                 ↓
              AI Advisory
                 ↓
              React UI
```

The finished ML system is successful when the predictions are:
- defensible
- reproducible
- versioned
- explainable enough for the product
- uncertainty-aware
- integrated through stable contracts
- useful to the final agricultural decision workflow

A model that looks impressive in a notebook but cannot be integrated reliably is not considered complete.
