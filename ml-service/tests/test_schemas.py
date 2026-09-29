import pytest
from pydantic import ValidationError

from agri_ml.schemas.common import DataClassification, Season, Unit
from agri_ml.schemas.health import HealthResponse


def test_contract_enums_are_stable_strings():
    # Must match backend common.api.DataClassification and the frontend (one vocabulary).
    assert [c.value for c in DataClassification] == [
        "OBSERVED",
        "FORECAST",
        "MODEL_PREDICTION",
        "ESTIMATED",
        "SYNTHETIC",
    ]
    assert [s.value for s in Season] == [
        "KHARIF",
        "RABI",
        "SUMMER",
        "WHOLE_YEAR",
        "AUTUMN",
        "WINTER",
    ]
    assert [u.value for u in Unit] == ["HECTARES", "TONNES", "TONNES_PER_HECTARE"]


def test_health_rejects_status_other_than_up():
    with pytest.raises(ValidationError):
        HealthResponse(status="DOWN", service="x", version="0", environment="t", models=[])
