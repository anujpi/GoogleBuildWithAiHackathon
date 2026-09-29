from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """camelCase on the wire (Spring Boot convention). Unknown fields are rejected, not ignored."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class DataClassification(StrEnum):
    """How a value was obtained. The one vocabulary shared by ML, backend and frontend."""

    OBSERVED = "OBSERVED"
    FORECAST = "FORECAST"
    MODEL_PREDICTION = "MODEL_PREDICTION"
    ESTIMATED = "ESTIMATED"
    SYNTHETIC = "SYNTHETIC"


class Season(StrEnum):
    KHARIF = "KHARIF"
    RABI = "RABI"
    SUMMER = "SUMMER"
    WHOLE_YEAR = "WHOLE_YEAR"
    AUTUMN = "AUTUMN"
    WINTER = "WINTER"


class Unit(StrEnum):
    HECTARES = "HECTARES"
    TONNES = "TONNES"
    TONNES_PER_HECTARE = "TONNES_PER_HECTARE"


class Interval(CamelModel):
    """Prediction interval in the unit of the value it belongs to."""

    lower: float
    upper: float
    nominal_coverage: float
    # Share of test-period actuals that fell inside their interval; null if never measured.
    empirical_coverage: float | None
    method: str


class Quantity(CamelModel):
    value: float
    unit: Unit


class QuantityWithInterval(Quantity):
    interval: Interval | None


class Provenance(CamelModel):
    source: str
    data_classification: DataClassification
    dataset_version: str | None
    model_name: str | None
    model_version: str | None
    feature_version: str | None
    # Latest period the underlying data covers: "YYYY" (crop year), "YYYY-MM" or "YYYY-MM-DD".
    data_through: str | None
    generated_at: datetime


class ErrorBody(CamelModel):
    code: str
    message: str
    details: dict


class ErrorResponse(CamelModel):
    error: ErrorBody
