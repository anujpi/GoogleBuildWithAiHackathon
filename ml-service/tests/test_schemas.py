import pytest
from pydantic import ValidationError

from agri_ml.schemas.common import DataClassification
from agri_ml.schemas.health import HealthResponse


def test_data_classification_values_are_stable_strings():
    assert [c.value for c in DataClassification] == [
        "OBSERVED",
        "FORECAST",
        "MODEL_PREDICTION",
        "ESTIMATED",
        "SYNTHETIC",
    ]


def test_health_rejects_status_other_than_up():
    with pytest.raises(ValidationError):
        HealthResponse(status="DOWN", service="x", version="0", environment="t", loaded_models=[])
