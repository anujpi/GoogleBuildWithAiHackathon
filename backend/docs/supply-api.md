# Supply forecast API contract

A **regional (state-level)** production forecast from the Python ML service. It is not a farm-level
forecast.

**Authentication:** send `Authorization: Bearer <accessToken>` (see `auth-api.md`). Any
authenticated role may call it; it exposes no farm or user data.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/api/supply/forecast` | `200` + `SupplyForecastResponse` | `400`, `401`, `422`, `502`, `503` |

## Data limitations (show these to users)
- **State level only.** There is no district, farm or field resolution.
- **Historical.** The model and history cover crop years 1997–2019. This is not a live or
  current-season forecast.
- **Third-party source.** History and training data come from a Kaggle republication of official
  statistics ("Agricultural Crop Yield in Indian States Dataset", CC-BY-SA-4.0).
- **Units not independently verified.** Hectares and tonnes are what the dataset publisher
  states. They haven't been checked against the government source.
- The response always includes these statements in `limitations`.

## Query parameters

| Param | Type | Required | Rules |
|---|---|---|---|
| `state` | string | yes | Not blank, ≤ 100 characters. Case-insensitive, e.g. `Uttar Pradesh`. |
| `crop` | string | yes | Not blank, ≤ 100 characters. Case-insensitive, but otherwise spelled **exactly** as in the dataset (e.g. `Wheat`, `Rice`, `Other  Rabi pulses` with two spaces). |
| `season` | string | yes | Only `KHARIF` or `RABI` (case-insensitive). `ZAID`, `OTHER` and anything else return `400 UNSUPPORTED_SEASON`. |
| `cropYear` | integer | yes | 1901–2100. The crop year to forecast. |
| `areaHectares` | number | yes | > 0. The **whole state's** planned or sown area for this crop and season, in hectares. It's an explicit assumption from the caller or a scenario, **never a farm's area**. |

Example: `GET /api/supply/forecast?state=Uttar%20Pradesh&crop=Wheat&season=RABI&cropYear=2019&areaHectares=9852504`

## How it works
1. The backend loads observed history from `production_history` for the same state, crop and
   season, for crop years `cropYear−3` to `cropYear−1`. The previous crop year is required.
2. It calls ML `POST /v1/predict/supply` (contract: `ml-service/docs/ml-contracts/supply.md`).
   It sends that history and your `areaHectares`, using the dataset's crop and season spelling.
3. It returns the ML result unchanged, next to the history it used.

`production_history` holds the real Kaggle `crop_yield.csv` rows (19,041 rows), loaded by the
Flyway migration `V5__load_kaggle_crop_yield`. That's the same file the model was trained on
(`crop_yield-sha256-ab9bc356b1f8`). Coconut and Cotton(lint) aren't loaded, because their
production isn't in tonnes.

## SupplyForecastResponse

| Field | Type | Nullable | Meaning |
|---|---|---|---|
| `state`, `crop` | string | no | The dataset's spelling of the matched series |
| `season` | string | no | `KHARIF` or `RABI` |
| `sourceSeasonLabel` | string | no | The dataset's label sent to ML: `Kharif` or `Rabi` |
| `cropYear` | int | no | The forecast crop year |
| `targetAreaHectares` | number | no | Your `areaHectares`, echoed back |
| `targetAreaSource` | string | no | Always `REQUEST_INPUT`: an assumption, not observed data |
| `forecast.expectedProductionTonnes` | number | no | The ML prediction, in tonnes |
| `forecast.period` | string | no | e.g. `crop year 2019, Rabi season` |
| `forecast.dataClassification` | string | no | `MODEL_PREDICTION` |
| `forecast.predictionInterval` | object | **yes** | `null` when ML returns none; it's never filled in |
| `forecast.predictionInterval.lowerTonnes` / `upperTonnes` | number | no | Interval bounds, in tonnes |
| `forecast.predictionInterval.nominalCoverage` | number | no | `0.8` |
| `forecast.predictionInterval.testEmpiricalCoverage` | number | no | The share of held-out test years inside the interval |
| `forecast.predictionInterval.method` | string | no | How ML derived the interval |
| `forecast.generatedAt` | string | no | ISO-8601 time at which ML produced the prediction |
| `baseline.productionTonnes` | number | no | ML's simple baseline (area × mean historical yield), kept separate from the prediction |
| `baseline.method` | string | no | How the baseline was computed |
| `baseline.historyYearsUsed` | int | no | 1–3 |
| `history[]` | array | no | The observed rows sent to ML, newest first |
| `history[].cropYear`, `areaHectares`, `productionTonnes` | | no | Values exactly as stored |
| `history[].source` | string | no | `KAGGLE_CROP_YIELD` |
| `history[].dataClassification` | string | no | `OBSERVED` |
| `history[].datasetVersion` | string | no | `crop_yield-sha256-ab9bc356b1f8` |
| `model.modelName`, `modelVersion`, `featureVersion`, `datasetVersion`, `trainedAt`, `trainingPeriod`, `evaluationPeriod` | string | no | Model provenance from ML |
| `model.trainingDataSource`, `model.spatialGranularity` | string | **yes** | Model provenance from ML (nullable in the ML contract) |
| `limitations` | string[] | no | The data limitations above, plus notes that the interval isn't a confidence score and that the area is an assumption |

**Interval wording:** `predictionInterval` is an **80% prediction interval**. It is not a
confidence score. Label it "80% prediction interval" or "typical historical error range", never
"confidence".

## Errors (standard `ApiError` shape; see `farm-api.md`)

| `code` | Status | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | A parameter is missing, has the wrong type or breaks a rule. `details[].field` names it. |
| `UNSUPPORTED_SEASON` | 400 | `season` isn't `KHARIF` or `RABI` |
| `UNAUTHORIZED` | 401 | Missing or invalid token |
| `INSUFFICIENT_HISTORY` | 422 | There's no observed row for `cropYear − 1` in this series, or the series doesn't exist. The message gives the recorded year span. The data ends in 2019 (2020 exists only for Uttarakhand), so for example any `cropYear` after 2020 fails, and 2021 works only for Uttarakhand. |
| `ML_REQUEST_REJECTED` | 422 | ML answered 422 (e.g. a crop the model doesn't support, or no positive yield in the history). `details[]` has one entry per ML reason, with `field` as a dotted path such as `history[0].cropYear`, or `null` when ML gave a general reason. |
| `ML_INVALID_RESPONSE` | 502 | ML answered with an unexpected status, a malformed body, missing required fields, or a unit other than tonnes |
| `ML_SERVICE_UNAVAILABLE` | 503 | ML is unreachable or didn't respond in time |
| `ML_MODEL_UNAVAILABLE` | 503 | ML is up but has no supply model loaded |

## Configuration
`ML_SERVICE_URL` (default `http://localhost:8000`), `ML_CONNECT_TIMEOUT` (default `PT2S`) and
`ML_READ_TIMEOUT` (default `PT10S`). The client uses HTTP/1.1 only: the JDK client's h2c upgrade
makes uvicorn drop the request body.
