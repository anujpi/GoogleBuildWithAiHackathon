"""Serve the supply estimate for one district x crop x season x crop year from a saved artifact.

The artifact (see training.supply.save_artifact) holds the model, its metadata and the exact
cleaned series it was trained on; history is looked up there, never supplied by the caller.
"""

import json
from datetime import UTC, datetime
from pathlib import Path

import numpy as np
import pandas as pd
import xgboost as xgb

from agri_ml.features.supply import build_features
from agri_ml.models.supply import predict_production
from agri_ml.schemas.common import (
    DataClassification,
    Interval,
    Provenance,
    Quantity,
    QuantityWithInterval,
    Unit,
)
from agri_ml.schemas.supply import (
    AreaQuantity,
    AreaSource,
    BaselineMethod,
    HistoricalYieldStats,
    HistoryPoint,
    HistoryUnits,
    ModelEvaluation,
    ReportedValues,
    ServedMethod,
    SupplyBaseline,
    SupplyEstimate,
    SupplyHistory,
    SupplyRequest,
    SupplyResponse,
    SupplyTarget,
)

SERIES_KEYS = ["district_id", "crop", "season"]
# MASTER_SPEC §12: ML supply source.
SOURCE_MODEL = "ML_SERVICE"
# A requested area outside [MIN, MAX] x the series' reported area range is an extrapolation the
# model never saw (log_area is a feature), so it is rejected rather than silently answered.
AREA_RANGE_FACTOR = (0.5, 2.0)
DOWNSIDE_THRESHOLD = 0.85
DOWNSIDE_DEFINITION = "YIELD_BELOW_85PCT_OF_TRAILING_3Y_MEAN"


class SupplyError(Exception):
    """A request the model cannot answer. `code` is part of the API contract."""

    def __init__(self, code: str, status: int, message: str, details: dict | None = None):
        super().__init__(message)
        self.code, self.status, self.message, self.details = code, status, message, details or {}


class ModelLoadError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


class SupplyModel:
    def __init__(self, booster: xgb.Booster, metadata: dict, series: pd.DataFrame):
        self.booster = booster
        self.metadata = metadata
        self.series = series
        self.scope = metadata["scope"]
        self.served = metadata["selection"]["servedMethod"]
        self.categories: dict[str, list[str]] = metadata["features"]["categories"]
        self._districts = {d["districtId"] for d in self.scope["districts"]}
        self._crops = {c["cropId"] for c in self.scope["crops"]}
        self._seasons = set(self.scope["seasons"])

    @classmethod
    def load(cls, model_dir: Path) -> "SupplyModel":
        files = [model_dir / n for n in ("model.json", "metadata.json", "series.parquet")]
        if not all(f.is_file() for f in files):
            raise ModelLoadError(
                "MODEL_NOT_LOADED", f"supply model artifact not found in {model_dir}"
            )
        try:
            metadata = json.loads(files[1].read_text())
            booster = xgb.Booster()
            booster.load_model(files[0])
            series = pd.read_parquet(files[2])
            return cls(booster, metadata, series)
        except Exception as exc:
            raise ModelLoadError(
                "ARTIFACT_LOAD_ERROR", f"could not load supply model from {model_dir}: {exc}"
            ) from exc

    @property
    def version(self) -> str:
        return self.metadata["modelVersion"]

    @property
    def data_source(self) -> str:
        """Where the training rows (and served history) came from, as recorded at training."""
        return self.metadata["data"]["source"]

    @property
    def data_through(self) -> str:
        return str(self.scope["lastObservedYear"])

    def predict(self, req: SupplyRequest) -> SupplyResponse:
        s = self._series_for(req)
        by_year = s.set_index("crop_year")
        prev = by_year.loc[req.crop_year - 1] if req.crop_year - 1 in by_year.index else None
        if prev is None or not (prev["area"] > 0 and prev["production"] > 0):
            raise SupplyError(
                "INSUFFICIENT_HISTORY",
                422,
                f"no positive reported production for cropYear - 1 ({req.crop_year - 1})",
                {
                    "requiredYear": req.crop_year - 1,
                    "estimableYears": [
                        int(s["crop_year"].min()) + 1,
                        self.scope["lastObservedYear"] + 1,
                    ],
                },
            )

        area, area_source = self._area(req, by_year, prev)
        history = s[s["crop_year"] < req.crop_year]
        panel = pd.concat(
            [
                history,
                pd.DataFrame(
                    [
                        {
                            "district_id": req.district_id,
                            "crop": req.crop_id,
                            "season": req.season.value,
                            "crop_year": req.crop_year,
                            "area": area,
                            "production": np.nan,
                        }
                    ]
                ),
            ],
            ignore_index=True,
        )
        f = build_features(panel, SERIES_KEYS)
        target = f[f["crop_year"] == req.crop_year]

        try:
            value = float(
                predict_production(self.served, target, self.booster, self.categories).iloc[0]
            )
            baseline_method = self.metadata["selection"]["bestBaseline"]
            baseline = float(predict_production(baseline_method, target).iloc[0])
        except Exception as exc:
            raise SupplyError("INTERNAL_ERROR", 500, f"prediction failed: {exc}") from exc
        if not (np.isfinite(value) and value >= 0 and np.isfinite(baseline)):
            raise SupplyError("INTERNAL_ERROR", 500, "prediction produced a non-finite value")

        now = datetime.now(UTC)
        interval = self.metadata["interval"]
        q_lo, q_hi = interval["logResidualQuantiles"]

        def with_interval(v: float, unit: Unit) -> QuantityWithInterval:
            return QuantityWithInterval(
                value=v,
                unit=unit,
                interval=Interval(
                    lower=v * float(np.exp(q_lo)),
                    upper=v * float(np.exp(q_hi)),
                    nominal_coverage=interval["nominalCoverage"],
                    empirical_coverage=interval["testEmpiricalCoverage"],
                    method=interval["method"],
                ),
            )

        used = [int(y) for y in history["crop_year"] if req.crop_year - 3 <= y < req.crop_year]
        return SupplyResponse(
            target=SupplyTarget(
                district_id=req.district_id,
                crop_id=req.crop_id,
                season=req.season,
                crop_year=req.crop_year,
            ),
            estimate=SupplyEstimate(
                production=with_interval(value, Unit.TONNES),
                yield_=with_interval(value / area, Unit.TONNES_PER_HECTARE),
                area=AreaQuantity(value=area, unit=Unit.HECTARES, area_source=area_source),
                served_method=ServedMethod.MODEL
                if self.served == "MODEL"
                else ServedMethod.BASELINE,
                history_years_used=sorted(used),
                provenance=Provenance(
                    source=SOURCE_MODEL,
                    # MASTER_SPEC §12: a served baseline is ESTIMATED, not MODEL_PREDICTION.
                    data_classification=DataClassification.MODEL_PREDICTION
                    if self.served == "MODEL"
                    else DataClassification.ESTIMATED,
                    dataset_version=self.metadata["datasetVersion"],
                    model_name=self.metadata["servedModelName"],
                    model_version=self.version,
                    feature_version=self.metadata["featureVersion"],
                    data_through=self.data_through,
                    generated_at=now,
                ),
            ),
            baseline=SupplyBaseline(
                method=BaselineMethod(baseline_method),
                production=Quantity(value=baseline, unit=Unit.TONNES),
            ),
            reported=self._reported(by_year, req.crop_year),
            history=self._history(s, now),
            historical_yield_stats=self._yield_stats(s),
            model_evaluation=self._evaluation(),
            limitations=self._limitations(req.crop_year),
        )

    def _series_for(self, req: SupplyRequest) -> pd.DataFrame:
        if req.district_id not in self._districts:
            raise SupplyError(
                "UNSUPPORTED_DISTRICT",
                422,
                f"district {req.district_id!r} is not supported",
                {"districtId": req.district_id},
            )
        if req.crop_id not in self._crops:
            raise SupplyError(
                "UNSUPPORTED_CROP",
                422,
                f"crop {req.crop_id!r} is not supported",
                {"cropId": req.crop_id, "supported": sorted(self._crops)},
            )
        if req.season.value not in self._seasons:
            raise SupplyError(
                "UNSUPPORTED_SEASON",
                422,
                f"season {req.season.value} is not supported",
                {"season": req.season.value, "supported": sorted(self._seasons)},
            )
        s = self.series[
            (self.series["district_id"] == req.district_id)
            & (self.series["crop"] == req.crop_id)
            & (self.series["season"] == req.season.value)
        ]
        if s.empty:
            raise SupplyError(
                "UNSUPPORTED_SERIES",
                422,
                "no reported data for this district, crop and season",
                {"districtId": req.district_id, "cropId": req.crop_id, "season": req.season.value},
            )
        return s.sort_values("crop_year")

    @staticmethod
    def _area(
        req: SupplyRequest, by_year: pd.DataFrame, prev: pd.Series
    ) -> tuple[float, AreaSource]:
        if req.area_hectares is None:
            if req.crop_year in by_year.index:
                return float(by_year.loc[req.crop_year, "area"]), AreaSource.REPORTED
            return float(prev["area"]), AreaSource.LAST_REPORTED
        lo, hi = (
            by_year["area"].min() * AREA_RANGE_FACTOR[0],
            by_year["area"].max() * AREA_RANGE_FACTOR[1],
        )
        if not lo <= req.area_hectares <= hi:
            raise SupplyError(
                "AREA_OUT_OF_RANGE",
                422,
                "areaHectares is outside the range this series was trained on",
                {"minAreaHectares": float(lo), "maxAreaHectares": float(hi)},
            )
        return float(req.area_hectares), AreaSource.REQUEST

    @staticmethod
    def _reported(by_year: pd.DataFrame, year: int) -> ReportedValues | None:
        if year not in by_year.index:
            return None
        row = by_year.loc[year]
        return ReportedValues(
            area=Quantity(value=row["area"], unit=Unit.HECTARES),
            production=Quantity(value=row["production"], unit=Unit.TONNES),
            yield_=Quantity(value=row["production"] / row["area"], unit=Unit.TONNES_PER_HECTARE),
        )

    def _history(self, s: pd.DataFrame, now: datetime) -> SupplyHistory:
        return SupplyHistory(
            units=HistoryUnits(
                area=Unit.HECTARES, production=Unit.TONNES, yield_=Unit.TONNES_PER_HECTARE
            ),
            points=[
                HistoryPoint(
                    crop_year=int(r.crop_year),
                    area=r.area,
                    production=r.production,
                    yield_=r.production / r.area,
                )
                for r in s.itertuples()
            ],
            provenance=Provenance(
                source=self.data_source,
                data_classification=DataClassification(self.metadata["data"]["classification"]),
                dataset_version=self.metadata["datasetVersion"],
                model_name=None,
                model_version=None,
                feature_version=None,
                data_through=self.data_through,
                generated_at=now,
            ),
        )

    @staticmethod
    def _yield_stats(s: pd.DataFrame) -> HistoricalYieldStats:
        """Descriptive statistics of the reported record (OBSERVED); not model inputs."""
        f = build_features(s, SERIES_KEYS)
        positive = f[f["yield"] > 0]
        y = positive["yield"]
        assessed = positive[positive["yield_mean3"].notna()]
        downside = (assessed["yield"] < DOWNSIDE_THRESHOLD * assessed["yield_mean3"]).mean()
        return HistoricalYieldStats(
            years_observed=len(positive),
            mean_yield=Quantity(value=float(y.mean()), unit=Unit.TONNES_PER_HECTARE),
            coefficient_of_variation=float(y.std(ddof=1) / y.mean()) if len(y) > 1 else None,
            downside_year_share=float(downside) if len(assessed) else None,
            years_assessed_for_downside=len(assessed),
            downside_definition=DOWNSIDE_DEFINITION,
        )

    def _evaluation(self) -> ModelEvaluation:
        m, sel = self.metadata, self.metadata["selection"]
        return ModelEvaluation(
            training_period=m["trainingPeriod"],
            validation_period=m["validationPeriod"],
            test_period=m["testPeriod"],
            served_method=ServedMethod.MODEL if self.served == "MODEL" else ServedMethod.BASELINE,
            test_wape=sel["testWape"][self.served],
            best_baseline=BaselineMethod(sel["bestBaseline"]),
            best_baseline_test_wape=sel["testWape"][sel["bestBaseline"]],
            test_interval_coverage=m["interval"]["testEmpiricalCoverage"],
        )

    def _limitations(self, crop_year: int) -> list[str]:
        """Factual caveats, passed on verbatim by the backend (MASTER_SPEC §8.2), which adds
        the data-vintage sentence itself. Not advice."""
        notes = [
            "The interval has one width for every district, crop and season; it is not specific "
            "to this series.",
            "The estimate uses reported area and production history only: no weather, soil or "
            "market data.",
        ]
        interval = self.metadata["interval"]
        if interval["testEmpiricalCoverage"] < interval["nominalCoverage"] - 0.1:
            notes.append(
                "On held-out years the interval contained the actual value less often than its "
                "nominal coverage."
            )
        if crop_year <= self.metadata["split"]["val_end"]:
            notes.append(
                "This crop year was used to fit, select or calibrate the model, so accuracy "
                "for it is optimistic."
            )
        return notes
