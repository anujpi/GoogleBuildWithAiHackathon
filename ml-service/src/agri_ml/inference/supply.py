"""Load a saved supply model and predict production for one district x crop x season."""

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd
import xgboost as xgb

from agri_ml.features.supply import build_features, model_matrix

SERIES_KEYS = ["series"]


class ModelLoadError(RuntimeError):
    pass


class InvalidInputError(ValueError):
    pass


@dataclass(frozen=True)
class HistoryPoint:
    crop_year: int
    area_hectares: float
    production_tonnes: float


@dataclass(frozen=True)
class SupplyPrediction:
    value: float
    lower: float | None
    upper: float | None
    baseline_value: float
    history_years_used: int


class SupplyModel:
    def __init__(self, booster: xgb.Booster, metadata: dict):
        self.booster = booster
        self.metadata = metadata
        self.categories: dict[str, list[str]] = metadata["features"]["categories"]

    @classmethod
    def load(cls, model_dir: Path) -> "SupplyModel":
        model_path, meta_path = model_dir / "model.json", model_dir / "metadata.json"
        if not model_path.is_file() or not meta_path.is_file():
            raise ModelLoadError(f"supply model artifact not found in {model_dir}")
        try:
            metadata = json.loads(meta_path.read_text())
            booster = xgb.Booster()
            booster.load_model(model_path)
        except Exception as exc:
            raise ModelLoadError(f"could not load supply model from {model_dir}: {exc}") from exc
        return cls(booster, metadata)

    @property
    def version(self) -> str:
        return self.metadata["modelVersion"]

    def predict(self, crop: str, season: str, crop_year: int, area_hectares: float,
                history: list[HistoryPoint]) -> SupplyPrediction:
        for field, value in (("crop", crop), ("season", season)):
            if value not in self.categories[field]:
                raise InvalidInputError(
                    f"unsupported {field} {value!r}; supported: {self.categories[field]}"
                )
        years = [h.crop_year for h in history]
        if len(set(years)) != len(years):
            raise InvalidInputError("history contains duplicate crop years")
        if any(y >= crop_year for y in years):
            raise InvalidInputError("history must only contain crop years before cropYear")
        if crop_year - 1 not in years:
            raise InvalidInputError("history must include the previous crop year (cropYear - 1)")

        rows = [
            {"crop_year": h.crop_year, "area": h.area_hectares, "production": h.production_tonnes}
            for h in history
        ] + [{"crop_year": crop_year, "area": area_hectares, "production": np.nan}]
        panel = pd.DataFrame(rows).assign(series="request", crop=crop, season=season)
        features = build_features(panel, SERIES_KEYS)
        target = features[features["crop_year"] == crop_year]
        if not target["yield_mean3"].iloc[0] > 0:
            raise InvalidInputError("history has no positive yield in the last three crop years")

        ratio = self.booster.predict(xgb.DMatrix(model_matrix(target, self.categories)))[0]
        baseline = float(area_hectares * target["yield_mean3"].iloc[0])
        value = baseline * float(np.exp(ratio))

        interval = self.metadata.get("interval")
        lower = upper = None
        if interval:
            q_lo, q_hi = interval["logResidualQuantiles"]
            lower, upper = value * float(np.exp(q_lo)), value * float(np.exp(q_hi))
        return SupplyPrediction(
            value=value, lower=lower, upper=upper, baseline_value=baseline,
            history_years_used=int(target["n_history_years"].iloc[0]),
        )
