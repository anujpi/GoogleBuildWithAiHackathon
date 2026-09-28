# Contract: supply prediction

**Status:** implemented in the ML service. **Not yet agreed with the backend team.** There is no
`MlClient` in the repository yet.

## Purpose
Predict production for one **state × crop × season** in one crop year. The caller supplies the
planned or sown area and recent history. ML returns a number with an interval, a baseline and
metadata. It gives no advice. Spring Boot decides, and the LLM explains.

## Data limitations (must reach every consumer)
- **State level only.** The model has no district, farm or field resolution.
- **Historical data, crop years 1997–2019.** It is not live or current-season data.
- **Third-party republication.** The training data is a Kaggle dataset ("Agricultural Crop Yield
  in Indian States Dataset", CC-BY-SA-4.0), which republishes official statistics.
- **Units not independently verified.** Hectares and tonnes are what the Kaggle publisher
  states. They have not been checked against the government source.

## Interval wording
`prediction.interval` is an **80% prediction interval**. On past data, about 80% of actual
values fell inside such intervals; on the test years, 81.9% did. It is **not** a confidence
score, and it is not a probability that this particular prediction is correct. Present it as
"80% prediction interval" or "typical historical error range", never as "confidence".

## Endpoint
`POST /v1/predict/supply`. The body is JSON with camelCase fields; unknown fields are rejected.

## Request

| Field | Type | Unit | Required | Rules |
|---|---|---|---|---|
| `crop` | string | n/a | yes | Must be one of the model's training crop labels, **exactly** as in the dataset (e.g. `Wheat`, `Potato`, `Other  Rabi pulses`). Coconut and Cotton(lint) are not supported. |
| `season` | string | n/a | yes | `Autumn`, `Kharif`, `Rabi`, `Summer`, `Whole Year` or `Winter` |
| `cropYear` | int | year | yes | 1950–2100. The crop year to predict. |
| `areaHectares` | float | hectares | yes | > 0. Planned or sown area for the target season, **for the whole state**. |
| `history` | array | n/a | yes | 1–3 items. Previous crop years for the **same state, crop and season**. Must include `cropYear − 1`. No duplicate years, and no years ≥ `cropYear`. |
| `history[].cropYear` | int | year | yes | |
| `history[].areaHectares` | float | hectares | yes | > 0 |
| `history[].productionTonnes` | float | tonnes | yes | ≥ 0 |

The model has no state feature. State matters only because the history must come from one
state's series. The caller (backend) is responsible for sending state-level history.

## Response (200)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `modelName` | string | no | `supply-production-xgb` |
| `modelVersion` | string | no | e.g. `supply-xgb-v1` |
| `dataClassification` | enum | no | always `MODEL_PREDICTION` |
| `prediction.value` | float | no | predicted production |
| `prediction.unit` | string | no | `tonnes`. This is the unit the training data's publisher states; it is not independently verified. |
| `prediction.period` | string | no | e.g. `crop year 2019, Rabi season` |
| `prediction.interval` | object | **yes** | `null` if the artifact has no calibrated interval. It is never filled with a placeholder. |
| `prediction.interval.lower` / `.upper` | float | no | tonnes |
| `prediction.interval.nominalCoverage` | float | no | 0.8 |
| `prediction.interval.method` | string | no | how the interval was derived |
| `prediction.interval.testEmpiricalCoverage` | float | no | coverage measured on the held-out test years |
| `evidence.baselineValue` | float | no | area × mean yield over the history years |
| `evidence.baselineMethod` | string | no | |
| `evidence.historyYearsUsed` | int | no | 1–3 |
| `provenance.datasetVersion` | string | no | e.g. `crop_yield-sha256-ab9bc356b1f8` |
| `provenance.featureVersion` | string | no | |
| `provenance.trainedAt` | string | no | ISO-8601 UTC |
| `provenance.trainingPeriod` | string | no | e.g. `<= 2013` |
| `provenance.evaluationPeriod` | string | no | test years, e.g. `2017-2019` |
| `provenance.trainingDataSource` | string | **yes** | *Added for v1 (additive).* e.g. `Kaggle: Agricultural Crop Yield in Indian States Dataset (akshatgupta7)` |
| `provenance.spatialGranularity` | string | **yes** | *Added for v1 (additive).* `state x crop x season x crop_year` |
| `generatedAt` | string | no | ISO-8601 UTC |

## Errors

| HTTP | When |
|---|---|
| 422 | Schema violation (missing or unknown field, area ≤ 0, empty history). Also: unsupported crop or season, history without `cropYear − 1`, duplicate or future history years, or no positive yield in the history. The `detail` field says which. |
| 503 | No supply model loaded. `/health` then shows `loadedModels: []`. |

## Example

Request:
```json
{"crop": "Wheat", "season": "Rabi", "cropYear": 2019, "areaHectares": 9852504,
 "history": [{"cropYear": 2018, "areaHectares": 9855900, "productionTonnes": 38039724},
             {"cropYear": 2017, "areaHectares": 9752941, "productionTonnes": 35645666},
             {"cropYear": 2016, "areaHectares": 9884913, "productionTonnes": 34971381}]}
```

Response (real output from `supply-xgb-v1`):
```json
{"modelName": "supply-production-xgb", "modelVersion": "supply-xgb-v1",
 "dataClassification": "MODEL_PREDICTION",
 "prediction": {"value": 37015561.2, "unit": "tonnes", "period": "crop year 2019, Rabi season",
   "interval": {"lower": 27285797.5, "upper": 45232845.9, "nominalCoverage": 0.8,
     "method": "empirical quantiles of log(actual/predicted) on the validation period",
     "testEmpiricalCoverage": 0.8193}},
 "evidence": {"baselineValue": 36297631.6,
   "baselineMethod": "area x mean reported yield of up to 3 previous crop years",
   "historyYearsUsed": 3},
 "provenance": {"datasetVersion": "crop_yield-sha256-ab9bc356b1f8",
   "featureVersion": "supply-features-v1", "trainedAt": "2026-09-28T14:45:37+00:00",
   "trainingPeriod": "<= 2013", "evaluationPeriod": "2017-2019",
   "trainingDataSource": "Kaggle: Agricultural Crop Yield in Indian States Dataset (akshatgupta7)",
   "spatialGranularity": "state x crop x season x crop_year"},
 "generatedAt": "2026-09-28T14:46:15.855747Z"}
```
(The reported actual for UP wheat in 2019 is 36,209,665. That is inside the interval, and it's a
test-period year.)

## Model details
See `docs/model-cards/supply.md` for metrics against baselines and for known limitations.
