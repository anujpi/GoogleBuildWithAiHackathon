"""Train and evaluate the district supply (production) model against simple baselines.

Target: production (tonnes) of one district x crop x season in crop year t.
Horizon: one crop year ahead, given the target season's cultivated (sown/planned) area and the
series' reported history for t-1..t-3.

The model predicts the log-ratio between this year's yield and the mean yield of the last up to
three years. Production = area x mean-yield x exp(prediction). The ratio form keeps potato
(~20 t/ha) and wheat (~3 t/ha) on one scale.
"""

import json
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

MODEL_NAME = "supply-production-xgb"
SERIES_KEYS = ["state_name", "district_name", "crop", "season"]
INTERVAL_QUANTILES = (0.1, 0.9)  # nominal 80% interval


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
    scored: pd.DataFrame  # val + test rows with baseline and model predictions


def regression_metrics(actual: pd.Series, predicted: pd.Series) -> dict[str, float | int]:
    err = predicted - actual
    positive = actual > 0
    return {
        "n": int(len(actual)),
        "mae": float(err.abs().mean()),
        "rmse": float(np.sqrt((err**2).mean())),
        # WAPE is defined even when some actuals are zero, unlike MAPE.
        "wape": float(err.abs().sum() / actual.abs().sum()),
        # MAPE only over rows with production > 0; zero-production rows are counted separately.
        "mape_nonzero": float((err[positive].abs() / actual[positive]).mean()),
        "n_zero_actual": int((~positive).sum()),
    }


def baseline_predictions(f: pd.DataFrame) -> dict[str, pd.Series]:
    return {
        "naive_last_year_production": f["yield_lag1"] * f["area_lag1"],
        "area_x_last_year_yield": f["area"] * f["yield_lag1"],
        "area_x_mean3_yield": f["area"] * f["yield_mean3"],
    }


def _eligible(features: pd.DataFrame) -> pd.DataFrame:
    # Every method, including baselines, needs last year's reported yield and area.
    return features[
        features["production"].notna()
        & features["yield_lag1"].notna()
        & features["area_lag1"].notna()
    ].copy()


def _target(f: pd.DataFrame) -> pd.Series:
    return np.log(f["yield"]) - np.log(f["yield_mean3"])


def train_and_evaluate(
    clean: pd.DataFrame,
    split: Split,
    dataset_version: str,
    seed: int = 42,
    param_grid: list[dict] | None = None,
) -> TrainResult:
    features = build_features(clean, SERIES_KEYS)
    data = _eligible(features)
    categories = {c: sorted(data[c].unique().tolist()) for c in CATEGORICAL_FEATURES}

    train = data[data["crop_year"] <= split.train_end]
    val = data[data["crop_year"].between(split.val_start, split.val_end)]
    test = data[data["crop_year"].between(split.test_start, split.test_end)]
    assert train["crop_year"].max() < val["crop_year"].min() <= val["crop_year"].max() < test[
        "crop_year"
    ].min(), "chronological split violated"

    # Zero-production rows have no defined log-yield target; they stay in val/test scoring.
    fit_train = train[train["yield"] > 0]
    fit_val = val[val["yield"] > 0]
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
    }

    def predict(booster: xgb.Booster, f: pd.DataFrame) -> pd.Series:
        ratio = booster.predict(xgb.DMatrix(model_matrix(f, categories)))
        return f["area"] * f["yield_mean3"] * np.exp(ratio)

    best = None
    search = []
    for extra in param_grid:
        params = {**base_params, **extra}
        booster = xgb.train(params, dtrain, num_boost_round=2000, evals=[(dval, "val")],
                            early_stopping_rounds=100, verbose_eval=False)
        n_trees = booster.best_iteration + 1
        # Keep only the trees up to the best iteration so the saved model predicts the same.
        booster = booster[:n_trees]
        val_mae = regression_metrics(val["production"], predict(booster, val))["mae"]
        search.append({**extra, "n_trees": n_trees, "val_mae": val_mae})
        if best is None or val_mae < best[0]:
            best = (val_mae, {**params, "n_trees": n_trees}, booster)
    _, best_params, booster = best

    scored = pd.concat([val.assign(split="validation"), test.assign(split="test")])
    for name, pred in baseline_predictions(scored).items():
        scored[f"pred_{name}"] = pred
    scored["pred_model"] = predict(booster, scored)

    # Empirical interval: quantiles of log(actual / predicted) on validation, checked on test.
    val_s = scored[(scored["split"] == "validation") & (scored["production"] > 0)]
    log_res = np.log(val_s["production"] / val_s["pred_model"])
    q_lo, q_hi = (float(np.quantile(log_res, q)) for q in INTERVAL_QUANTILES)
    test_s = scored[scored["split"] == "test"]
    covered = (test_s["production"] >= test_s["pred_model"] * np.exp(q_lo)) & (
        test_s["production"] <= test_s["pred_model"] * np.exp(q_hi)
    )

    methods = ["model"] + list(baseline_predictions(scored).keys())
    metrics = {
        part: {
            m: regression_metrics(g["production"], g[f"pred_{m}"]) for m in methods
        }
        for part, g in scored.groupby("split")
    }

    metadata = {
        "modelName": MODEL_NAME,
        "modelType": "xgboost.Booster (gradient-boosted trees), target = log(yield_t / mean yield t-1..t-3)",
        "featureVersion": FEATURE_VERSION,
        "datasetVersion": dataset_version,
        "trainedAt": datetime.now(UTC).isoformat(timespec="seconds"),
        "target": {"name": "production", "unit": "tonnes",
                   "granularity": "district x crop x season x crop_year",
                   "horizon": "1 crop year ahead"},
        "features": {"numeric": NUMERIC_FEATURES, "categorical": CATEGORICAL_FEATURES,
                     "categories": categories},
        "split": asdict(split),
        "trainingPeriod": f"<= {split.train_end}",
        "validationPeriod": f"{split.val_start}-{split.val_end}",
        "testPeriod": f"{split.test_start}-{split.test_end}",
        "rows": {"train": len(train), "validation": len(val), "test": len(test)},
        "hyperparameters": best_params,
        "hyperparameterSearch": search,
        "seed": seed,
        "metrics": metrics,
        "interval": {
            "method": "empirical quantiles of log(actual/predicted) on the validation period",
            "nominalCoverage": INTERVAL_QUANTILES[1] - INTERVAL_QUANTILES[0],
            "logResidualQuantiles": [q_lo, q_hi],
            "testEmpiricalCoverage": float(covered.mean()),
        },
    }
    return TrainResult(booster=booster, metadata=metadata, scored=scored)


def error_analysis(scored: pd.DataFrame) -> dict:
    test = scored[scored["split"] == "test"].copy()
    test["abs_err_model"] = (test["pred_model"] - test["production"]).abs()
    test["abs_err_baseline"] = (test["pred_area_x_mean3_yield"] - test["production"]).abs()

    def by(col: str) -> list[dict]:
        g = test.groupby(col).agg(
            n=("production", "size"),
            actual_total=("production", "sum"),
            mae_model=("abs_err_model", "mean"),
            mae_baseline_area_x_mean3=("abs_err_baseline", "mean"),
        )
        g["wape_model"] = test.groupby(col)["abs_err_model"].sum() / g["actual_total"]
        g["wape_baseline_area_x_mean3"] = (
            test.groupby(col)["abs_err_baseline"].sum() / g["actual_total"]
        )
        return g.reset_index().round(4).to_dict(orient="records")

    worst = test.nlargest(10, "abs_err_model")[
        SERIES_KEYS + ["crop_year", "area", "production", "pred_model",
                       "pred_area_x_mean3_yield", "yield_lag1", "yield_mean3"]
    ]
    return {
        "byCrop": by("crop"),
        "bySeason": by("season"),
        "byYear": by("crop_year"),
        "worstDistrictsByModelWape": sorted(by("district_name"),
                                            key=lambda r: -r["wape_model"])[:10],
        "worst10Predictions": worst.round(3).to_dict(orient="records"),
    }


def save_artifact(result: TrainResult, out_dir: Path, model_version: str,
                  extra: dict | None = None) -> Path:
    if out_dir.exists():
        raise FileExistsError(f"{out_dir} exists; bump the model version instead of overwriting")
    out_dir.mkdir(parents=True)
    result.booster.save_model(out_dir / "model.json")
    metadata = {**result.metadata, "modelVersion": model_version, **(extra or {})}
    (out_dir / "metadata.json").write_text(json.dumps(metadata, indent=2, default=str))
    return out_dir
