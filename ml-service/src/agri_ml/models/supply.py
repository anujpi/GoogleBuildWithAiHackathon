"""Supply prediction methods shared by training (evaluation) and inference (serving).

Every method maps a feature frame (from `features.supply.build_features`) to production in tonnes.
Keeping them here means the served method is computed exactly as it was evaluated.
"""

import numpy as np
import pandas as pd
import xgboost as xgb

from agri_ml.features.supply import model_matrix

MODEL = "MODEL"
BASELINES = {
    "NAIVE_LAST_YEAR_PRODUCTION": lambda f: f["yield_lag1"] * f["area_lag1"],
    "AREA_X_LAST_YEAR_YIELD": lambda f: f["area"] * f["yield_lag1"],
    "AREA_X_MEAN_YIELD_3Y": lambda f: f["area"] * f["yield_mean3"],
}


def predict_production(
    method: str,
    features: pd.DataFrame,
    booster: xgb.Booster | None = None,
    categories: dict[str, list[str]] | None = None,
) -> pd.Series:
    """Production (tonnes) for each feature row, by `method` (MODEL or a BASELINES key).

    MODEL predicts log(yield_t / yield_mean3); production = area x yield_mean3 x exp(prediction).
    """
    if method in BASELINES:
        return BASELINES[method](features)
    if method != MODEL or booster is None or categories is None:
        raise ValueError(f"unknown method {method!r} or missing booster/categories")
    ratio = booster.predict(xgb.DMatrix(model_matrix(features, categories)))
    return features["area"] * features["yield_mean3"] * np.exp(ratio)
