"""Train and evaluate the district supply (production) model against simple baselines.

Target: production (tonnes) of one district x crop x season in crop year t, given the season's
cultivated area and the series' reported history for t-1..t-3.

The model predicts log(yield_t / mean yield of t-1..t-3); production = area x mean x exp(pred).
The ratio form keeps potato (~20 t/ha) and wheat (~3 t/ha) on one scale.

Served method (acceptance rule): the model is served only if its validation WAPE is at least 5%
lower than the best baseline's (chosen on validation) AND its test WAPE is not higher. Otherwise
the best baseline is served. The interval is calibrated for whichever method is served.
"""

import json
import platform
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path

import numpy as np
import pandas as pd
import xgboost as xgb

from agri_ml.features.supply import (
    CATEGORICAL_FEATURES,
    FEATURE_VERSION,
    NUMERIC_FEATURES,
    build_features,
    model_matrix,
)
from agri_ml.models.supply import BASELINES, MODEL, predict_production
from agri_ml.reference import build_scope

MODEL_NAME = "supply-production-xgb"
SERIES_KEYS = ["district_id", "crop", "season"]
INTERVAL_QUANTILES = (0.1, 0.9)  # nominal 80% interval
INTERVAL_METHOD = "EMPIRICAL_LOG_RESIDUAL_QUANTILES_VALIDATION"
MIN_VALIDATION_IMPROVEMENT = 0.05
SPATIAL_FOLDS = 5


@dataclass(frozen=True)
class Split:
    train_end: int
    val_start: int
    val_end: int
    test_start: int
    test_end: int


@dataclass
class TrainResult:
    booster: xgb.Booster
    metadata: dict
    scored: pd.DataFrame  # validation + test rows with every method's prediction


def regression_metrics(actual: pd.Series, predicted: pd.Series) -> dict[str, float | int]:
    """MAE, RMSE, WAPE (primary: defined with zero actuals, scale-free across crops) and MAPE over
    rows with production > 0. R² is not reported: production spans orders of magnitude across
    crops and districts, so R² would mostly measure that spread, not forecast skill."""
    err = predicted - actual
    positive = actual > 0
    return {
        "n": int(len(actual)),
        "mae": float(err.abs().mean()),
        "rmse": float(np.sqrt((err**2).mean())),
        "wape": float(err.abs().sum() / actual.abs().sum()),
        "mape_nonzero": float((err[positive].abs() / actual[positive]).mean()),
        "n_zero_actual": int((~positive).sum()),
    }


def _eligible(features: pd.DataFrame) -> pd.DataFrame:
    # Every method, including baselines, needs last year's reported (positive) yield and area.
    return features[
        features["production"].notna()
        & features["yield_lag1"].notna()
        & features["area_lag1"].notna()
    ].copy()


def _target(f: pd.DataFrame) -> pd.Series:
    return np.log(f["yield"]) - np.log(f["yield_mean3"])


def _wape(f: pd.DataFrame, pred: pd.Series) -> float:
    return float((pred - f["production"]).abs().sum() / f["production"].abs().sum())


def _period(years: pd.Series) -> str:
    return f"{int(years.min())}-{int(years.max())}"


def select_served_method(val_wape: dict, test_wape: dict) -> tuple[str, str]:
    """MASTER_SPEC §9.6 acceptance rule. Returns (served method, best baseline).

    The best baseline is the one with the lowest validation WAPE. MODEL is served only if its
    validation WAPE is at least 5% lower than that baseline's AND its test WAPE is not higher;
    otherwise that baseline is served.
    """
    best_baseline = min(BASELINES, key=val_wape.get)
    accepted = (
        val_wape[MODEL] <= (1 - MIN_VALIDATION_IMPROVEMENT) * val_wape[best_baseline]
        and test_wape[MODEL] <= test_wape[best_baseline]
    )
    return (MODEL if accepted else best_baseline), best_baseline


def spatial_evaluation(
    train: pd.DataFrame, categories: dict, params: dict, n_trees: int, baseline: str
) -> dict:
    """Held-out-district check on the training period: districts are split into folds; each fold
    is predicted by a model trained on the other districts. Reported only, not used for selection.
    Years are shared between folds, so this measures generalisation to unseen districts, not to
    unseen years."""
    districts = sorted(train["district_id"].unique())
    fold_of = {d: i % SPATIAL_FOLDS for i, d in enumerate(districts)}
    folds = train["district_id"].map(fold_of)
    rows = []
    for k in range(SPATIAL_FOLDS):
        held, rest = train[folds == k], train[(folds != k) & (train["yield"] > 0)]
        if held.empty or rest.empty:
            continue
        booster = xgb.train(
            params,
            xgb.DMatrix(model_matrix(rest, categories), label=_target(rest)),
            num_boost_round=n_trees,
        )
        rows.append(
            {
                "fold": k,
                "districts": int(held["district_id"].nunique()),
                "n": len(held),
                "wapeModel": _wape(held, predict_production(MODEL, held, booster, categories)),
                "wapeBaseline": _wape(held, predict_production(baseline, held)),
            }
        )
    return {
        "folds": rows,
        "baseline": baseline,
        "method": f"{SPATIAL_FOLDS}-fold by district on the training period",
    }


def train_and_evaluate(
    series: pd.DataFrame,
    split: Split,
    dataset_version: str,
    seed: int = 42,
    param_grid: list[dict] | None = None,
    data_source: str = "DES_S01_DATA_GOV_IN",
    data_classification: str = "OBSERVED",
) -> TrainResult:
    """`series`: output of `reference.to_scope` (one row per district x crop x season x year).

    `data_source`/`data_classification` describe the training rows and are served as the
    provenance of the history; tests pass SYNTHETIC so a synthetic artifact is never labelled real.
    """
    features = build_features(series, SERIES_KEYS)
    data = _eligible(features)
    categories = {c: sorted(data[c].unique().tolist()) for c in CATEGORICAL_FEATURES}

    train = data[data["crop_year"] <= split.train_end]
    val = data[data["crop_year"].between(split.val_start, split.val_end)]
    test = data[data["crop_year"].between(split.test_start, split.test_end)]
    if train.empty or val.empty or test.empty:
        raise ValueError("train, validation and test periods must all contain eligible rows")
    if (
        not train["crop_year"].max()
        < val["crop_year"].min()
        <= val["crop_year"].max()
        < test["crop_year"].min()
    ):
        raise ValueError("chronological split violated")

    # Zero-production rows have no defined log-yield target; they stay in val/test scoring.
    fit_train, fit_val = train[train["yield"] > 0], val[val["yield"] > 0]
    dtrain = xgb.DMatrix(model_matrix(fit_train, categories), label=_target(fit_train))
    dval = xgb.DMatrix(model_matrix(fit_val, categories), label=_target(fit_val))

    param_grid = param_grid or [
        {"max_depth": d, "min_child_weight": w} for d in (2, 3, 4) for w in (5, 20)
    ]
    base_params = {
        "objective": "reg:pseudohubererror",
        "eta": 0.03,
        "subsample": 0.8,
        "colsample_bytree": 0.8,
        "seed": seed,
        "nthread": 1,
    }

    best, search = None, []
    for extra in param_grid:
        params = {**base_params, **extra}
        booster = xgb.train(
            params,
            dtrain,
            num_boost_round=2000,
            evals=[(dval, "val")],
            early_stopping_rounds=100,
            verbose_eval=False,
        )
        n_trees = booster.best_iteration + 1
        # Keep only the trees up to the best iteration so the saved model predicts the same.
        booster = booster[:n_trees]
        val_wape = _wape(val, predict_production(MODEL, val, booster, categories))
        search.append({**extra, "n_trees": n_trees, "val_wape": val_wape})
        if best is None or val_wape < best[0]:
            best = (val_wape, params, n_trees, booster)
    _, best_params, n_trees, booster = best

    scored = pd.concat([val.assign(split="validation"), test.assign(split="test")])
    methods = [MODEL, *BASELINES]
    for m in methods:
        scored[f"pred_{m}"] = predict_production(m, scored, booster, categories)
    metrics = {
        part: {m: regression_metrics(g["production"], g[f"pred_{m}"]) for m in methods}
        for part, g in scored.groupby("split")
    }

    val_wape = {m: metrics["validation"][m]["wape"] for m in methods}
    test_wape = {m: metrics["test"][m]["wape"] for m in methods}
    served, best_baseline = select_served_method(val_wape, test_wape)

    # Empirical interval for the served method: quantiles of log(actual/predicted) on validation,
    # coverage measured on test.
    pred_col = f"pred_{served}"
    val_s = scored[
        (scored["split"] == "validation") & (scored["production"] > 0) & (scored[pred_col] > 0)
    ]
    log_res = np.log(val_s["production"] / val_s[pred_col])
    q_lo, q_hi = (float(np.quantile(log_res, q)) for q in INTERVAL_QUANTILES)
    test_s = scored[scored["split"] == "test"]
    covered = (test_s["production"] >= test_s[pred_col] * np.exp(q_lo)) & (
        test_s["production"] <= test_s[pred_col] * np.exp(q_hi)
    )

    metadata = {
        "modelName": MODEL_NAME,
        "servedModelName": MODEL_NAME
        if served == MODEL
        else f"supply-baseline-{served.lower().replace('_', '-')}",
        "modelType": "xgboost.Booster, target = log(yield_t / mean yield t-1..t-3)",
        "featureVersion": FEATURE_VERSION,
        "datasetVersion": dataset_version,
        "data": {"source": data_source, "classification": data_classification},
        "dataThrough": str(int(series["crop_year"].max())),
        "servedMethod": served,
        "trainedAt": datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "environment": {
            "python": platform.python_version(),
            "xgboost": xgb.__version__,
            "pandas": pd.__version__,
            "numpy": np.__version__,
        },
        "target": {
            "name": "production",
            "unit": "TONNES",
            "granularity": "district x crop x season x crop_year",
            "horizon": "1 crop year after the latest reported year of the series",
        },
        "features": {
            "numeric": NUMERIC_FEATURES,
            "categorical": CATEGORICAL_FEATURES,
            "categories": categories,
        },
        "split": asdict(split),
        "trainingPeriod": _period(train["crop_year"]),
        "validationPeriod": _period(val["crop_year"]),
        "testPeriod": _period(test["crop_year"]),
        "rows": {"train": len(train), "validation": len(val), "test": len(test)},
        "hyperparameters": {**best_params, "n_trees": n_trees},
        "hyperparameterSearch": search,
        "seed": seed,
        "metrics": metrics,
        "selection": {
            "rule": "serve MODEL iff validation WAPE <= 0.95 x best baseline validation WAPE "
            "and test WAPE <= that baseline's test WAPE; else serve the best baseline",
            "servedMethod": served,
            "bestBaseline": best_baseline,
            "validationWape": val_wape,
            "testWape": test_wape,
        },
        "interval": {
            "method": INTERVAL_METHOD,
            "appliesTo": served,
            "nominalCoverage": INTERVAL_QUANTILES[1] - INTERVAL_QUANTILES[0],
            "logResidualQuantiles": [q_lo, q_hi],
            "testEmpiricalCoverage": float(covered.mean()),
        },
        "spatialEvaluation": spatial_evaluation(
            train, categories, best_params, n_trees, best_baseline
        ),
        "scope": build_scope(series),
        "limitations": [
            f"Reported data ends in crop year {int(series['crop_year'].max())}; estimates are not "
            "current-season forecasts.",
            "Backtests condition on the final reported area of the target year, which a real "
            "forecast would not know; backtest accuracy is optimistic.",
            "No weather, soil, irrigation, price or location feature is used.",
            "The interval has one width for every district, crop and season.",
        ],
    }
    return TrainResult(booster=booster, metadata=metadata, scored=scored)


def error_analysis(scored: pd.DataFrame, served: str, baseline: str) -> dict:
    test = scored[scored["split"] == "test"].copy()
    test["abs_err_served"] = (test[f"pred_{served}"] - test["production"]).abs()
    test["abs_err_baseline"] = (test[f"pred_{baseline}"] - test["production"]).abs()

    def by(col: str) -> list[dict]:
        g = test.groupby(col).agg(
            n=("production", "size"),
            actual_total=("production", "sum"),
            mae_served=("abs_err_served", "mean"),
            mae_baseline=("abs_err_baseline", "mean"),
        )
        g["wape_served"] = test.groupby(col)["abs_err_served"].sum() / g["actual_total"]
        g["wape_baseline"] = test.groupby(col)["abs_err_baseline"].sum() / g["actual_total"]
        return g.reset_index().round(4).to_dict(orient="records")

    # When a baseline is served, served and baseline are the same column: list it once.
    columns = [*SERIES_KEYS, "crop_year", "area", "production", f"pred_{served}",
               f"pred_{baseline}", "yield_lag1", "yield_mean3"]
    worst = test.nlargest(10, "abs_err_served")[list(dict.fromkeys(columns))]
    return {
        "servedMethod": served,
        "comparedBaseline": baseline,
        "byCrop": by("crop"),
        "bySeason": by("season"),
        "byYear": by("crop_year"),
        "worstDistrictsByServedWape": sorted(by("district_id"), key=lambda r: -r["wape_served"])[
            :10
        ],
        "worst10Predictions": worst.round(3).to_dict(orient="records"),
    }


def save_artifact(
    result: TrainResult,
    out_dir: Path,
    model_version: str,
    series: pd.DataFrame,
    extra: dict | None = None,
) -> Path:
    """Write a self-contained artifact: inference needs nothing else from the training run."""
    if out_dir.exists():
        raise FileExistsError(f"{out_dir} exists; bump the model version instead of overwriting")
    out_dir.mkdir(parents=True)
    result.booster.save_model(out_dir / "model.json")
    series.to_parquet(out_dir / "series.parquet", index=False)
    metadata = {**result.metadata, "modelVersion": model_version, **(extra or {})}
    (out_dir / "metadata.json").write_text(json.dumps(metadata, indent=2, default=str))
    return out_dir
