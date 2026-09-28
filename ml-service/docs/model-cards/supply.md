# Model card: supply-production-xgb supply-xgb-v1

| Field | Value |
|---|---|
| Model name | `supply-production-xgb` |
| Model version | `supply-xgb-v1` |
| Feature version | `supply-features-v1` |
| Dataset version | `crop_yield-sha256-ab9bc356b1f8` (preprocessing `crop-yield-prep-v1`) |
| Owner | ML team |
| Trained at | 2026-09-28T14:45:37Z |
| Training period | Crop years ≤ 2013 |
| Validation period | 2014–2016 (model selection and interval calibration) |
| Evaluation period | 2017–2019 (held-out test) |
| MLflow run ID | `09afff2f322842c7ac51aef6c4c12e76` (experiment `supply-forecasting`, local `mlflow.db`) |
| Artifact | `artifacts/supply/supply-xgb-v1/` (git-ignored): `model.json`, `metadata.json`, `error_analysis.json`, `scored.csv` |
| Geographic scope | **State level**, 30 Indian states and UTs. No district or farm resolution. |
| Temporal scope | Historical crop years 1997–2019 |

> **Data limitations:**
> - state level only
> - historical crop years 1997–2019
> - trained on a Kaggle third-party republication of official statistics
> - units (hectares, tonnes) not independently verified against the government source

## Purpose
The model predicts production for one **state × crop × season** in crop year *t*. Its inputs are
that season's planned or sown area and the reported area and production for up to three previous
crop years (t-1 must be included). It returns a structured number with an interval and a
baseline. Spring Boot decides what to do with it, and the LLM layer explains it.

## Intended use
- Supply evidence for the Decision Engine and scenario analysis at state level, for example
  "what if the area changes".
- It must be served with its baseline value and interval, never as a bare number.

## Not intended for
- District-, farm- or field-level predictions.
- Current-season forecasts presented as live data. The training data ends in 2019.
- Coconut and Cotton(lint), which are excluded (their Production is in a different unit). The
  API rejects them with 422.
- Causal claims about what drives production.

## Training data
The Kaggle "Agricultural Crop Yield in Indian States Dataset", `crop_yield.csv`, CC-BY-SA-4.0.
See `docs/datasets/crop_yield.md`.
- **Classification:** OBSERVED official statistics, republished by a third party.
- **Units:** Area in hectares and Production in metric tons, **as stated by the publisher.
  Not verified** against the upstream government source.
- **Mapping:** State → `state_name`, Crop → `crop`, Season → `season`, Crop_Year → `crop_year`,
  Area → `area`, Production → `production`.
- **Columns not used:**
  - `Yield`: disagrees with Production ÷ Area for about 80% of rows, so yield is recomputed as
    Production ÷ Area
  - `Fertilizer` and `Pesticide`: each is Area × one per-year rate, so they add nothing and
    would leak Area
  - `Annual_Rainfall`: same-year value with unverified year alignment
- **Excluded rows (`to_supply_frame()`):**

  | Reason | Rows |
  |---|---|
  | Coconut and Cotton(lint) (different Production unit) | 648 |
  | Rows flagged as series jumps (>10× from the series median) | 505 |
  | 2020 (one state only) | 37 |
  | `Oilseeds total` (aggregate of other rows) | 29 |
  | **Total** | **1,194 of 19,689, leaving 18,495** |

## Features (`src/agri_ml/features/supply.py`)
For the target row (state, crop, season, t):
- `log_area`: the target season's area, supplied by the caller
- `log_area_ratio_lag1`: change in area against t-1
- `log_yield_mean3`: log of the mean yield over t-1 to t-3
- `log_yield_rel_lag1..3`: each lagged yield relative to that mean
- `n_history_years`
- one-hot `crop` and `season`

Lags are matched by exact year and never filled from older years. Nothing from year *t* is used
except the area.

**Target:** log(yield_t ÷ mean yield over t-1 to t-3). Production = area × mean yield × e^prediction.

## Evaluation design
- **Chronological split.** Train ≤ 2013, validate 2014–2016, test 2017–2019. No random split.
- **Eligible rows.** A row is scored only if it has last year's area and yield: 10,400 train,
  2,590 validation and 2,789 test rows. Zero-production rows stay in scoring but are not used
  for fitting.
- **Model selection.** A grid of 6 settings (max_depth 2/3/4 × min_child_weight 5/20), with
  early stopping on validation MAE. Selected: depth 4, min_child_weight 5, 127 trees,
  pseudo-Huber loss.
- **Not done yet.** No leave-state-out evaluation, so there is no claim about unseen states.

## Metrics (test 2017–2019, n = 2,789)

| Method | MAE (t) | RMSE (t) | WAPE | MAPE (production > 0) |
|---|---|---|---|---|
| Naive: last year's production | 117,086 | 912,885 | 13.76% | 113.0% |
| Area × last year's yield | 81,114 | **587,045** | 9.53% | 15.5% |
| Area × mean yield of 3 years | 85,168 | 710,448 | 10.01% | 15.7% |
| **XGBoost (this model)** | **78,863** | 602,086 | **9.27%** | **15.0%** |

Validation (2014–2016, n = 2,590): the model's WAPE is 11.24%, against 11.52% for
area × mean-3 yield and 12.57% for area × last year's yield.

**Honest reading: the improvement over the best simple baseline is marginal.**
- WAPE improves by 0.26 points (about 3% relative) and MAE by about 2.8%.
- RMSE is about 2.6% **worse** than area × last year's yield.
- Row by row, the model beats that baseline on only **46.8%** of test rows. Its median absolute
  percentage error is 7.0%, against 6.7% for the baseline.
- The gain is concentrated in large series.

Under the baseline-first rule, it's a team decision whether this margin justifies the model over
`area × last year's yield`. The API returns the `area × mean-3 yield` baseline in
`evidence.baselineValue` either way.

## Uncertainty
- **Method:** empirical 10th and 90th percentiles of log(actual ÷ predicted) on the **validation**
  period. They are applied multiplicatively: [pred × e^−0.305, pred × e^+0.200], i.e. about
  −26% / +22%.
- **Nominal coverage:** 80%. **Measured coverage on test: 81.9%.**
- It is a **prediction interval, not a confidence score.** Don't label it "confidence" in the
  API, UI or LLM wording.
- This is one global interval width. It is not conditioned on crop, state or data quality. It
  describes typical historical error, not the probability of any single outcome.

## Error analysis (test)
- **By year:** WAPE 11.1% (2017), 9.4% (2018), 7.4% (2019). In 2019 the area × mean-3 baseline
  (7.1%) beat the model.
- **By season:** the model beats area × mean-3 in every season except Whole Year (9.6% against
  8.8%).
- **By crop (largest volumes):**
  - Sugarcane 7.9%, Rice 7.2%, Wheat 7.3%, Potato 9.8%
  - worse: Maize 15.3%, Soyabean 17.4%, Banana 22.2%
  - Onion (10.7%) and Jute are slightly worse than the baseline
- **Weakest states:** Andhra Pradesh 18.2%, Chhattisgarh 17.4% (baseline 16.2%, so worse than
  the baseline), Bihar 16.1%.
- **Largest absolute errors:** Sugarcane (UP, Maharashtra, Bihar) and UP Wheat 2017 (predicted
  28.7M against an actual 35.6M).
- **Systematic pattern:** the model under-predicts rising series. UP Wheat, Potato and Rice in
  2017 and 2018 are all under the actual value. Yield trends aren't modelled beyond three lags.

## Known limitations and biases
- State-level only. Andhra Pradesh before and after 2013 is a different territory (Telangana
  split off).
- Season labels switch within some series (e.g. UP Potato: Whole Year until 2003, then Rabi). The
  first years after a switch have no lags and aren't scored.
- Units are the publisher's claim and unverified. Supplied `Yield` isn't used.
- No weather, soil or price signals. The only year-*t* input is area.
- Crop names are kept exactly as in the file (`Other  Rabi pulses` has two spaces), and the API
  requires the exact label.
- The interval is global and slightly over-covers on test.

## Reproducibility check
On 2026-09-28 the model was retrained from a clean state, into an empty artifact directory with
a separate MLflow store and the same seed (42). It produced a **byte-identical `model.json`**,
and all metadata matched apart from `trainedAt`.

## How to reproduce
```bash
cp <crop_yield.csv> data/raw/crop_yield.csv        # sha256 ab9bc356b1f8...
python scripts/train_supply.py --model-version supply-xgb-v2   # a new version; v1 is never overwritten
```
