# `POST /v1/predict/supply` (MASTER_SPEC §9.3)

This endpoint estimates the **reported production** (tonnes) of one district × crop × season in one
crop year. It's built from the series' own reported history and the season's cultivated area.

## Definitions

| Term | Meaning |
|---|---|
| Region | A **district**. There are no state-level or aggregated predictions. The state is carried in the id (`up-…`) |
| District | An S01 district name as it was reported. Renamed or split districts are separate series |
| Crop | A `cropId` from the reference scope. Today: `potato`, `wheat`, `onion`, `maize` |
| Season | The S01 season, mapped to `Season`. Only seasons present in the data are supported |
| Crop year | The S01 `crop_year` label (an integer). It isn't aligned to calendar months |
| Horizon | **One crop year after the history used.** The latest crop year that can be predicted is `lastObservedYear + 1` (**2015**, because S01 ends in 2014). Earlier years are backtests against reported values |
| History | Looked up by the ML service from its versioned S01 snapshot. **The caller never sends it** |
| Minimum history | `cropYear - 1` must have reported area > 0 and production > 0. Years t-2 and t-3 are used when present |
| What it represents | The model's estimate of reported district production. It is **not** a current-season forecast, and not a farm yield |

## Request

```json
{ "districtId": "up-agra", "cropId": "potato", "season": "RABI", "cropYear": 2015, "areaHectares": null }
```

| Field | Type | Rule |
|---|---|---|
| `districtId` | string, required | Contract id; must be in `/v1/reference/scope` |
| `cropId` | string, required | Contract id |
| `season` | `Season`, required | |
| `cropYear` | integer, required (strings are rejected) | Must have a positive reported `cropYear-1` in the series; candidate range `firstYear+1 … lastYear+1` (the backend's `estimableYears`) |
| `areaHectares` | number > 0, or null | If null: the reported area of `cropYear` if it exists (`REPORTED`), otherwise that of `cropYear-1` (`LAST_REPORTED`). If given: it must lie within 0.5× the smallest to 2× the largest reported area of the series, because the model uses `log(area)` and must not extrapolate |

## Response 200

See `backend/src/test/resources/contracts/ml/supply-response.json` (and `supply-response-backtest.json`).

| Block | Classification | Content |
|---|---|---|
| `target` | — | The request, echoed back |
| `estimate` | `MODEL_PREDICTION` if `servedMethod=MODEL`, `ESTIMATED` if `BASELINE` | `production` (TONNES) and `yield` (TONNES_PER_HECTARE), each with an `interval`; `area` + `areaSource`; `servedMethod` (`MODEL` or `BASELINE`, see the model card); `historyYearsUsed`; `provenance` |
| `baseline` | — | The best baseline (chosen on validation) for comparison, e.g. `AREA_X_MEAN_YIELD_3Y` |
| `reported` | (OBSERVED) | The reported area, production and yield of `cropYear`, if S01 has them (backtest years). Otherwise null |
| `history` | as trained: `OBSERVED`, source `DES_S01_DATA_GOV_IN` for real artifacts | Every reported year of the series, with its units and provenance |
| `historicalYieldStats` | derived from OBSERVED | `yearsObserved`, `meanYield`, `coefficientOfVariation` (sample), and `downsideYearShare` = the share of assessed years with yield < 85% of the trailing mean of t-1..t-3 |
| `modelEvaluation` | — | Training, validation and test periods, the served method, test WAPE of the served method and the best baseline, and test interval coverage (all from the artifact) |
| `limitations` | — | Factual sentences, listed below |

**Uncertainty.** An 80% empirical interval: the 10th and 90th percentiles of
`log(actual / predicted)` of the served method on the validation years, applied multiplicatively.
`empiricalCoverage` is the share of test-year actuals that fell inside it. There is one width for
all series. **There is no confidence score.**

**Provenance.**
- `estimate.provenance` holds `source: ML_SERVICE`, `modelName` (for example `supply-production-xgb`,
  or `supply-baseline-…` when a baseline is served), `modelVersion`, `datasetVersion`
  (`s01-<sha256 prefix of the raw files>`), `featureVersion`, `dataThrough` (the last reported crop
  year) and `generatedAt`.
- The training, validation and test periods are in `modelEvaluation`.

**Limitations** (sentences, passed on verbatim by the backend, which adds the data-vintage sentence):
- Always: the interval has one width for every series, and the estimate uses no weather, soil or market data.
- When test coverage is more than 0.10 below nominal: the interval under-covers on held-out years.
- When `cropYear` ≤ the end of validation: the year was used to fit, select or calibrate, so its accuracy is optimistic.

## Errors

See [README.md](README.md#error-codes-93). The checks run in this order, and the first failure is returned:
1. schema (`VALIDATION_ERROR`)
2. district
3. crop
4. season
5. series
6. history (`INSUFFICIENT_HISTORY`, which covers out-of-range years)
7. area (`AREA_OUT_OF_RANGE`)
