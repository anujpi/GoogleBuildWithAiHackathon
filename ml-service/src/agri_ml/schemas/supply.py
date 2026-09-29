from enum import StrEnum

from pydantic import Field, StrictInt

from agri_ml.reference import ID_PATTERN
from agri_ml.schemas.common import (
    CamelModel,
    Provenance,
    Quantity,
    QuantityWithInterval,
    Season,
    Unit,
)


class ServedMethod(StrEnum):
    MODEL = "MODEL"
    BASELINE = "BASELINE"


class BaselineMethod(StrEnum):
    NAIVE_LAST_YEAR_PRODUCTION = "NAIVE_LAST_YEAR_PRODUCTION"
    AREA_X_LAST_YEAR_YIELD = "AREA_X_LAST_YEAR_YIELD"
    AREA_X_MEAN_YIELD_3Y = "AREA_X_MEAN_YIELD_3Y"


class AreaSource(StrEnum):
    REQUEST = "REQUEST"  # areaHectares supplied by the caller
    REPORTED = "REPORTED"  # S01 reported area of the target crop year
    LAST_REPORTED = "LAST_REPORTED"  # S01 reported area of cropYear - 1


class SupplyRequest(CamelModel):
    district_id: str = Field(pattern=ID_PATTERN, examples=["up-agra"])
    crop_id: str = Field(pattern=ID_PATTERN, examples=["potato"])
    season: Season
    crop_year: StrictInt = Field(description="S01 crop year to estimate")
    area_hectares: float | None = Field(
        default=None,
        gt=0,
        allow_inf_nan=False,
        description="Cultivated area of the target season; null = use the reported area",
    )


class SupplyTarget(CamelModel):
    district_id: str
    crop_id: str
    season: Season
    crop_year: int


class AreaQuantity(Quantity):
    area_source: AreaSource


class SupplyEstimate(CamelModel):
    production: QuantityWithInterval
    yield_: QuantityWithInterval = Field(alias="yield")
    area: AreaQuantity
    served_method: ServedMethod
    history_years_used: list[int]
    provenance: Provenance


class SupplyBaseline(CamelModel):
    method: BaselineMethod
    production: Quantity


class ReportedValues(CamelModel):
    area: Quantity
    production: Quantity
    yield_: Quantity = Field(alias="yield")


class HistoryUnits(CamelModel):
    area: Unit
    production: Unit
    yield_: Unit = Field(alias="yield")


class HistoryPoint(CamelModel):
    crop_year: int
    area: float
    production: float
    yield_: float = Field(alias="yield")


class SupplyHistory(CamelModel):
    units: HistoryUnits
    points: list[HistoryPoint]
    provenance: Provenance


class HistoricalYieldStats(CamelModel):
    years_observed: int
    mean_yield: Quantity
    coefficient_of_variation: float | None
    downside_year_share: float | None
    years_assessed_for_downside: int
    downside_definition: str


class ModelEvaluation(CamelModel):
    training_period: str
    validation_period: str
    test_period: str
    served_method: ServedMethod
    test_wape: float
    best_baseline: BaselineMethod
    best_baseline_test_wape: float
    test_interval_coverage: float | None


class SupplyResponse(CamelModel):
    target: SupplyTarget
    estimate: SupplyEstimate
    baseline: SupplyBaseline
    reported: ReportedValues | None
    history: SupplyHistory
    historical_yield_stats: HistoricalYieldStats
    model_evaluation: ModelEvaluation
    limitations: list[str]
