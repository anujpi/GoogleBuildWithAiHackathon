from enum import StrEnum
from typing import Literal

from agri_ml.schemas.common import CamelModel


class ModelReadiness(StrEnum):
    READY = "READY"
    NOT_READY = "NOT_READY"


class ModelStatus(CamelModel):
    capability: Literal["SUPPLY"]
    status: ModelReadiness
    model_version: str | None
    dataset_version: str | None
    feature_version: str | None
    # Error code when NOT_READY: MODEL_NOT_LOADED (no artifact) or ARTIFACT_LOAD_ERROR.
    reason: str | None


class HealthResponse(CamelModel):
    # The process is up. Whether predictions are available is per model, in `models`.
    status: Literal["UP"]
    service: str
    version: str
    environment: str
    models: list[ModelStatus]
