"""Request/response schemas for the transparent (non-trained) intelligence endpoints:
crop suitability, demand and anomaly. Contracts: docs/ml-contracts/intelligence.md.
"""

from datetime import datetime
from typing import Literal

from pydantic import Field

from agri_ml.schemas.common import DataClassification
from agri_ml.schemas.supply import _CamelModel

# --- crop suitability ----------------------------------------------------------------------------


class SuitabilityRequest(_CamelModel):
    state: str = Field(examples=["Uttar Pradesh"])
    season: str = Field(examples=["Rabi"], description="Season label as in the source data")
    soil_ph: float | None = Field(default=None, ge=0, le=14,
                                  description="Measured soil pH, if known")
    irrigated: bool | None = Field(default=None, description="Whether the farm has irrigation")
    top_n: int = Field(default=5, ge=1, le=20)


class SuitabilityComponent(_CamelModel):
    name: str
    score: float | None = Field(description="0-1; null when the component was not assessed")
    weight: float
    evidence: str


class HistoricalStats(_CamelModel):
    years_observed: int
    first_year: int
    last_year: int
    median_yield_t_per_ha: float
    latest_area_hectares: float
    latest_production_tonnes: float
    yield_trend_pct_per_year: float | None


class SuitabilityCandidate(_CamelModel):
    crop: str
    suitability_score: float = Field(
        description="Weighted evidence index in [0, 1]. NOT a probability and not calibrated.")
    components: list[SuitabilityComponent]
    evidence: list[str]
    not_assessed: list[str]
    history: HistoricalStats


class Provenance(_CamelModel):
    method: str
    method_version: str
    data_sources: list[str]
    dataset_version: str | None = None
    data_classification: DataClassification
    generated_at: datetime


class SuitabilityResponse(_CamelModel):
    region: str
    season: str
    score_type: str
    candidates: list[SuitabilityCandidate]
    crops_considered: int
    limitations: list[str]
    provenance: Provenance


# --- demand --------------------------------------------------------------------------------------


class DemandRequest(_CamelModel):
    crop: str = Field(examples=["Potato"])
    state: str | None = Field(default=None, description="Apportion to a state by population share")
    target_year: int = Field(ge=2000, le=2100)


class YearValue(_CamelModel):
    year: int
    value: float


class DemandInterval(_CamelModel):
    lower: float
    upper: float
    method: str


class Backtest(_CamelModel):
    method: str
    mape_pct: float
    n: int


class DemandResponse(_CamelModel):
    crop: str
    region: str
    period: str
    forecast: float
    unit: str
    data_classification: DataClassification
    interval: DemandInterval | None
    method: str
    evidence: dict
    national_history: list[YearValue]
    backtests: list[Backtest]
    limitations: list[str]
    provenance: Provenance


# --- anomaly -------------------------------------------------------------------------------------


class SeriesPoint(_CamelModel):
    period: str
    value: float


class AnomalyRequest(_CamelModel):
    metric: str = Field(examples=["yield_t_per_ha"])
    unit: str | None = None
    series: list[SeriesPoint] = Field(min_length=1, max_length=500,
                                      description="Oldest first; the last point is evaluated")
    window: int = Field(default=10, ge=3, le=100)
    threshold: float = Field(default=3.5, gt=0, description="|robust z| above this is an anomaly")


class AnomalyResponse(_CamelModel):
    metric: str
    unit: str | None
    period: str
    status: Literal["NORMAL", "ANOMALY", "INSUFFICIENT_DATA"]
    direction: Literal["HIGH", "LOW", "NONE"]
    observed_value: float
    baseline: float | None
    deviation: float | None
    deviation_pct: float | None
    robust_z: float | None
    threshold: float
    window_used: int
    method: str
    provenance: Provenance
