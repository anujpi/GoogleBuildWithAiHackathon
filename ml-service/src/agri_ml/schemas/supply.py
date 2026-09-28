from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from agri_ml.schemas.common import DataClassification


class _CamelModel(BaseModel):
    # camelCase on the wire to match the Spring Boot JSON conventions.
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class SupplyHistoryPoint(_CamelModel):
    crop_year: int = Field(ge=1950, le=2100)
    area_hectares: float = Field(gt=0, description="Reported cultivated area (ha)")
    production_tonnes: float = Field(ge=0, description="Reported production (tonnes)")


class SupplyPredictionRequest(_CamelModel):
    crop: str = Field(examples=["Potato"])
    season: str = Field(examples=["Rabi"], description="S01 season label, e.g. Kharif, Rabi, Whole Year")
    crop_year: int = Field(ge=1950, le=2100, description="Crop year to forecast")
    area_hectares: float = Field(gt=0, description="Sown/planned area for the target season (ha)")
    history: list[SupplyHistoryPoint] = Field(
        min_length=1,
        max_length=3,
        description="Reported area and production for up to the 3 previous crop years; "
        "must include cropYear - 1",
    )


class PredictionInterval(_CamelModel):
    lower: float
    upper: float
    nominal_coverage: float
    method: str
    test_empirical_coverage: float


class SupplyPredictionValue(_CamelModel):
    value: float
    unit: str
    period: str
    interval: PredictionInterval | None


class SupplyEvidence(_CamelModel):
    baseline_value: float = Field(description="area x mean reported yield of the history years")
    baseline_method: str
    history_years_used: int


class SupplyProvenance(_CamelModel):
    dataset_version: str
    feature_version: str
    trained_at: str
    training_period: str
    evaluation_period: str


class SupplyPredictionResponse(_CamelModel):
    model_name: str
    model_version: str
    data_classification: DataClassification
    prediction: SupplyPredictionValue
    evidence: SupplyEvidence
    provenance: SupplyProvenance
    generated_at: datetime
