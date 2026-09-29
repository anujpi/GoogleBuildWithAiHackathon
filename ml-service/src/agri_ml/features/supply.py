"""Supply features: one row per series (district x crop x season) and target crop year.

Used unchanged by training and by inference, so the API cannot drift from training.

Every history feature for crop year t comes only from years t-1, t-2 and t-3, looked up by exact
year (a missing t-1 gives NaN; it is never filled from t-2). The only value from year t is `area`,
the cultivated area of the target season. Production and yield of year t are never features.

Features (FEATURE_VERSION = supply-features-v2):
- log_area              log(area_t)
- log_area_ratio_lag1   log(area_t / area_{t-1})
- log_yield_mean3       log(mean of the reported yields among t-1..t-3)
- log_yield_rel_lag{k}  log(yield_{t-k} / that mean), k = 1..3 (NaN if the year is missing)
- n_history_years       how many of t-1..t-3 have a positive reported yield
- crop, season          one-hot, using contract ids (cropId, Season enum)
No weather, soil, price or location feature exists: the model cannot use them.
"""

import numpy as np
import pandas as pd

FEATURE_VERSION = "supply-features-v2"
LAGS = (1, 2, 3)

CATEGORICAL_FEATURES = ["crop", "season"]
NUMERIC_FEATURES = [
    "log_area",
    "log_area_ratio_lag1",
    "log_yield_mean3",
    "log_yield_rel_lag1",
    "log_yield_rel_lag2",
    "log_yield_rel_lag3",
    "n_history_years",
]


def _safe_log(x: pd.Series) -> pd.Series:
    x = x.astype(float)
    return np.log(x.where(x > 0))


def build_features(df: pd.DataFrame, series_keys: list[str]) -> pd.DataFrame:
    """Add history features to `df`.

    Input columns: series_keys, crop, season, crop_year, area, production.

    Rows whose own production is NaN (e.g. the inference target row) still get features.
    Returns a copy with `yield`, lag columns, `yield_mean3` and the model features.
    """
    out = df.copy()
    out["yield"] = out["production"] / out["area"]
    # A zero or missing yield is not usable as history (log undefined / not reported).
    hist = out[series_keys + ["crop_year", "area", "yield"]].copy()
    hist.loc[~(hist["yield"] > 0), "yield"] = np.nan

    for lag in LAGS:
        shifted = hist.assign(crop_year=hist["crop_year"] + lag).rename(
            columns={"area": f"area_lag{lag}", "yield": f"yield_lag{lag}"}
        )
        out = out.merge(shifted, on=series_keys + ["crop_year"], how="left")

    lag_yields = out[[f"yield_lag{lag}" for lag in LAGS]]
    out["n_history_years"] = lag_yields.notna().sum(axis=1)
    out["yield_mean3"] = lag_yields.mean(axis=1, skipna=True)

    log_mean = _safe_log(out["yield_mean3"])
    out["log_area"] = _safe_log(out["area"])
    out["log_area_ratio_lag1"] = out["log_area"] - _safe_log(out["area_lag1"])
    out["log_yield_mean3"] = log_mean
    for lag in LAGS:
        out[f"log_yield_rel_lag{lag}"] = _safe_log(out[f"yield_lag{lag}"]) - log_mean
    return out


def model_matrix(features: pd.DataFrame, categories: dict[str, list[str]]) -> pd.DataFrame:
    """Numeric design matrix with a fixed one-hot layout taken from the training categories."""
    x = features[NUMERIC_FEATURES].astype(float).copy()
    for col in CATEGORICAL_FEATURES:
        for value in categories[col]:
            x[f"{col}={value}"] = (features[col] == value).astype(float)
    return x
