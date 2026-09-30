from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from agri_ml.schemas.common import DataClassification


class _CamelModel(BaseModel):
    # camelCase on the wire to match the Spring Boot JSON conventions.
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class DiseaseClass(_CamelModel):
    class_label: str = Field(description="Training class label, e.g. Potato___Late_blight")
    crop: str = Field(description="Crop encoded in the class label (Potato or Tomato)")
    disease: str = Field(description="Disease encoded in the class label; 'healthy' if none")
    probability: float = Field(ge=0, le=1, description="Softmax probability of this class")


class DiseaseConfidence(_CamelModel):
    value: float = Field(ge=0, le=1, description="Softmax probability of the predicted class")
    method: str
    calibrated: bool = Field(description="False: no calibration step was applied; the value is "
                             "a raw softmax probability")
    low_confidence_threshold: float | None = Field(
        description="Threshold applied by the ML service; null means none is applied and the "
        "backend must apply its own conservative threshold")


class DiseaseProvenance(_CamelModel):
    dataset_version: str
    feature_version: str
    model_version: str
    trained_at: str
    training_data_source: str
    training_data_license: str
    generated_at: datetime


class DiseasePredictionResponse(_CamelModel):
    model_name: str
    model_version: str
    data_classification: DataClassification
    prediction: DiseaseClass
    confidence: DiseaseConfidence
    top_classes: list[DiseaseClass] = Field(description="Highest-probability classes, best first")
    supported_crops: list[str]
    provenance: DiseaseProvenance
