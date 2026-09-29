# ML ⇄ Spring Boot contract

The contract is **`MASTER_SPEC.md` §9** (repo root), and that file is canonical. This folder documents
how the ML service implements it.
- The Pydantic schemas are in `src/agri_ml/schemas/`.
- The golden JSON files are in **`backend/src/test/resources/contracts/ml/`** (§16). The ML tests
  (`tests/test_api.py::test_contract_examples_match_schemas`) validate those files against the
  schemas, and the backend's `MlClientTest` deserialises the same files. A change on either side fails a test.

The golden values come from a model trained on **SYNTHETIC** test rows. Their versions say
`SYNTHETIC-EXAMPLE`, and their history is classified `SYNTHETIC` (the backend rejects that from a
live service). **Only the shape is normative.**

| Endpoint | Purpose | Doc |
|---|---|---|
| `GET /health` | The process is up, and each model is READY or NOT_READY | below |
| `GET /v1/reference/scope` | The supported states, districts, crops, seasons and series | [reference.md](reference.md) |
| `POST /v1/predict/supply` | District supply estimate | [supply.md](supply.md) |

## Conventions

- **JSON:** camelCase. Unknown request fields are rejected, never ignored.
- **Ids** (D3): lower-case slugs matching `^[a-z0-9][a-z0-9-]{0,49}$`.
  - `stateId`, e.g. `up`
  - `districtId` = `<stateId>-<slug of the S01 district name>`, e.g. `up-agra`
  - `cropId`, e.g. `potato`
- **Enums:**
  - `Season`: `KHARIF, RABI, SUMMER, WHOLE_YEAR, AUTUMN, WINTER`
  - `DataClassification` (D4): `OBSERVED, FORECAST, MODEL_PREDICTION, ESTIMATED, SYNTHETIC`
  - `Unit`: `HECTARES, TONNES, TONNES_PER_HECTARE`
- **Provenance:** `{ source, dataClassification, datasetVersion, modelName, modelVersion, featureVersion, dataThrough, generatedAt }`. Unknown values are null.
  - The estimate's source is `ML_SERVICE`: `MODEL_PREDICTION` when `servedMethod=MODEL`, `ESTIMATED` when `servedMethod=BASELINE` (§12).
  - The history's source is `DES_S01_DATA_GOV_IN` and its classification is `OBSERVED`. Both are recorded in the artifact at training time.
- **Uncertainty:** `interval { lower, upper, nominalCoverage, empiricalCoverage, method }`. There is no `confidence` field.
- **Limitations:** factual sentences, which the backend passes on verbatim. The backend adds the data-vintage sentence itself.

## Error codes (§9.3)

The body is `{ "error": { "code", "message", "details" } }`.

| HTTP | Code | When |
|---|---|---|
| 422 | `VALIDATION_ERROR` | Schema, pattern or type violation, or an unknown field. `details.errors[]` names the fields |
| 422 | `UNSUPPORTED_DISTRICT` / `UNSUPPORTED_CROP` / `UNSUPPORTED_SEASON` | The id isn't in the artifact scope |
| 422 | `UNSUPPORTED_SERIES` | Each id is supported, but the district × crop × season has no data |
| 422 | `INSUFFICIENT_HISTORY` | No positive reported production for `cropYear-1`. This includes any year outside the series' estimable years; `details.estimableYears` gives the range |
| 422 | `AREA_OUT_OF_RANGE` | **Extension, pending a spec edit.** `areaHectares` outside 0.5×min–2×max of the series' reported area. The model conditions on log(area), so it refuses to extrapolate. The backend maps it to `UNSUPPORTED_INPUT` |
| 503 | `MODEL_NOT_LOADED` / `ARTIFACT_LOAD_ERROR` | No usable artifact |
| 500 | `INTERNAL_ERROR` | Unexpected, including a prediction that fails or isn't finite |
| 404 / 405 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` | Wrong route |

The spec's `AREA_UNAVAILABLE` is never emitted. A prediction needs a positive reported `cropYear-1`
row, and that row always has an area, so a missing area is reported as `INSUFFICIENT_HISTORY` instead.

## `GET /health`

This returns 200 whenever the process is up. Prediction availability is reported per model:

```json
{ "status": "UP", "service": "agri-ml", "version": "0.1.0", "environment": "local",
  "models": [ { "capability": "SUPPLY", "status": "READY | NOT_READY", "modelVersion": "…|null",
                "datasetVersion": "…|null", "featureVersion": "…|null",
                "reason": "null | MODEL_NOT_LOADED | ARTIFACT_LOAD_ERROR" } ] }
```
