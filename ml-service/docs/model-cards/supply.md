# Model card: supply (district production), `supply-xgb-v1`

> **Status: NOT TRAINED ON REAL DATA.** No artifact exists, because S01 could not be downloaded
> (see `docs/datasets/s01_up.md`). Every section below describes the pipeline as implemented and
> tested. **There are no measured metrics yet.** When `scripts/train_supply.py` runs, it writes
> `artifacts/supply/supply-xgb-v1/evaluation_report.md`, generated from the artifact's own
> `metadata.json`. Copy its tables into "Metrics" below. Don't type numbers by hand.

| Field | Value |
|---|---|
| Model name | `supply-production-xgb`, or `supply-baseline-<method>` when a baseline is served |
| Model version | `supply-xgb-v1` (planned; not built) |
| Feature version | `supply-features-v2` |
| Dataset version | `s01-<sha256 prefix>` (fixed at download) |
| Trained at | — |
| Training / validation / test | crop years ≤ 2010 / 2011–2012 / 2013–2014 |
| MLflow run ID | — |
| Supported scope | Whatever `GET /v1/reference/scope` lists, derived from the training series. Planned: Uttar Pradesh; potato, wheat, onion, maize; seasons as present in S01 |

## Purpose

Estimates the **reported production** (t) of one district × crop × season for one crop year,
from that series' reported history and the season's cultivated area. It is evidence for Spring
Boot, not a decision.

## Intended use

District supply context and backtests. It supplies the yield and production evidence behind crop
evidence and production risk in the Master Specification, labelled with its data vintage (through 2014).

## Not intended for

- Current-season or future-season forecasts (the data ends in 2014)
- Farm-level yield
- Any district, crop or season outside the reference scope
- Rainfall or temperature scenarios: **the model has no weather input**

## Target

`log(yield_t / mean reported yield of t-1..t-3)`. Production = area × that mean × exp(prediction).

## Features (`supply-features-v2`)

- `log_area`
- `log_area_ratio_lag1`
- `log_yield_mean3`
- `log_yield_rel_lag1..3`
- `n_history_years`
- one-hot `crop` and `season`

**It has no weather, soil, irrigation, price or location feature.** Nothing about those can be
inferred from its output.

## Baselines

These are evaluated on exactly the same rows:
- `NAIVE_LAST_YEAR_PRODUCTION`
- `AREA_X_LAST_YEAR_YIELD`
- `AREA_X_MEAN_YIELD_3Y`

## Model and selection

- **Model:** XGBoost (`reg:pseudohubererror`, eta 0.03, subsample and colsample 0.8, seed 42, nthread 1), with a grid over max_depth {2,3,4} × min_child_weight {5,20} and early stopping on validation.
- **Selection rule** (`training.supply.select_served_method`): the model is served only if its validation WAPE is ≥ 5% lower than the best baseline's **and** its test WAPE is not higher. Otherwise the best baseline is served. That rule uses the test period as a guard, so the reported test metrics are not a fully untouched estimate. The card will state which method was served.

## Evaluation design

- **Primary metric: WAPE.** It is scale-free across crops and defined when some actuals are 0.
- **Also reported:** MAE, RMSE, and MAPE on actual > 0.
- **R² is not reported.** Across crops and districts it mainly measures scale.
- **Error analysis:** by crop, season, year and district, plus the 10 worst predictions.
- **Spatial check:** 5 folds by district on the training period. It is reported only, and not used for selection.

## Metrics

**Not measured. No real-data run exists yet**, because S01 hasn't been downloaded (blocker B1 in
MASTER_SPEC §20). Every number used in tests comes from SYNTHETIC fixtures and is not a model result.

To produce the metrics:
1. `python scripts/download_s01_crop_production.py` (needs `DATA_GOV_IN_API_KEY`)
2. `python scripts/train_supply.py --model-version supply-xgb-v1`

The artifact's `evaluation_report.md` then holds, all generated from the evaluation output:
- validation and test MAE, RMSE, WAPE and MAPE for the model and each baseline, on identical rows
- the selection outcome
- the interval coverage
- the spatial folds
- breakdowns by crop, season, year and district

Copy those tables here unchanged.

## Uncertainty

An 80% empirical interval: the 10th and 90th percentiles of `log(actual / predicted)` of the served
method on validation, with test coverage reported. It has one width for every series, and there is
no confidence score.

## Known limitations and failure cases (by design)

- **Data vintage** ends in 2014.
- **Area conditioning:** backtests use the final reported area.
- **New or split districts** have too little history, and the API answers `INSUFFICIENT_HISTORY`.
- **Area outside the series' range** (0.5×min to 2×max) is rejected (`AREA_OUT_OF_RANGE`) rather than extrapolated.
- **Shocks the history can't anticipate** (drought, pest outbreaks) are expected to be the largest errors, because no weather input exists.
