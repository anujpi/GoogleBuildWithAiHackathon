"""API contract tests: success, every error code, readiness (SYNTHETIC model, see conftest)."""

import json
from datetime import datetime
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from agri_ml import __version__
from agri_ml.api.app import create_app
from agri_ml.config import get_settings
from agri_ml.schemas.common import ErrorResponse
from agri_ml.schemas.health import HealthResponse
from agri_ml.schemas.reference import ScopeResponse
from agri_ml.schemas.supply import SupplyRequest, SupplyResponse

VALID = {
    "districtId": "up-agra",
    "cropId": "potato",
    "season": "RABI",
    "cropYear": 2015,
    "areaHectares": None,
}
# MASTER_SPEC §16: golden ML responses live with the backend tests; both sides validate them.
EXAMPLES = (
    Path(__file__).resolve().parents[2]
    / "backend"
    / "src"
    / "test"
    / "resources"
    / "contracts"
    / "ml"
)


@pytest.fixture(scope="module")
def client(model_dir):
    return TestClient(create_app(supply_model_dir=model_dir), raise_server_exceptions=False)


def post(client, **patch):
    return client.post("/v1/predict/supply", json={**VALID, **patch})


def assert_error(response, status, code):
    assert response.status_code == status, response.text
    body = ErrorResponse.model_validate(response.json())
    assert body.error.code == code
    return body.error


# --- success -------------------------------------------------------------------------------


def test_prediction_for_next_crop_year(client):
    r = post(client)
    assert r.status_code == 200, r.text
    body = SupplyResponse.model_validate(r.json())
    est = body.estimate
    assert est.production.unit == "TONNES" and est.production.value > 0
    assert est.yield_.unit == "TONNES_PER_HECTARE"
    assert est.yield_.value == pytest.approx(est.production.value / est.area.value)
    assert est.area.area_source == "LAST_REPORTED"
    assert est.production.interval.lower <= est.production.value <= est.production.interval.upper
    assert est.history_years_used == [2012, 2013, 2014]
    assert body.reported is None
    assert not any("fit, select or calibrate" in n for n in body.limitations)


def test_prediction_provenance_is_complete_and_classified(client):
    body = post(client).json()
    p = body["estimate"]["provenance"]
    served = body["estimate"]["servedMethod"]
    assert p["dataClassification"] == ("MODEL_PREDICTION" if served == "MODEL" else "ESTIMATED")
    assert p["source"] == "ML_SERVICE"
    assert p["modelVersion"] == "supply-test"
    assert p["datasetVersion"] == "synthetic-test"
    assert p["featureVersion"] == "supply-features-v2"
    assert p["dataThrough"] == "2014"
    datetime.fromisoformat(p["generatedAt"])
    h = body["history"]["provenance"]
    assert (h["dataClassification"], h["source"], h["modelVersion"]) == (
        "SYNTHETIC",
        "TEST_FIXTURE",
        None,
    )
    ev = body["modelEvaluation"]
    assert (ev["trainingPeriod"], ev["testPeriod"]) == ("1998-2010", "2013-2014")


def test_backtest_year_returns_reported_values(client):
    body = post(client, cropYear=2013).json()
    assert body["reported"]["production"]["unit"] == "TONNES"
    assert body["estimate"]["area"]["areaSource"] == "REPORTED"
    assert not any("calibrate" in n for n in body["limitations"])
    assert any("calibrate" in n for n in post(client, cropYear=2012).json()["limitations"])


def test_requested_area_is_used(client):
    reported = post(client).json()["estimate"]["area"]["value"]
    body = post(client, areaHectares=reported * 1.1).json()
    assert body["estimate"]["area"] == {
        "value": pytest.approx(reported * 1.1),
        "unit": "HECTARES",
        "areaSource": "REQUEST",
    }


def test_historical_yield_stats_are_descriptive(client):
    stats = post(client).json()["historicalYieldStats"]
    assert stats["yearsObserved"] == 18
    assert 0 <= stats["downsideYearShare"] <= 1
    assert stats["downsideDefinition"] == "YIELD_BELOW_85PCT_OF_TRAILING_3Y_MEAN"


def test_served_baseline_is_computed_like_evaluation(model_dir):
    app = create_app(supply_model_dir=model_dir)
    model = app.state.supply_model
    model.served = "AREA_X_MEAN_YIELD_3Y"
    body = TestClient(app).post("/v1/predict/supply", json=VALID).json()
    assert body["estimate"]["servedMethod"] == "BASELINE"
    # MASTER_SPEC §12: a served baseline is ESTIMATED, not MODEL_PREDICTION.
    assert body["estimate"]["provenance"]["dataClassification"] == "ESTIMATED"
    history = [p for p in body["history"]["points"] if 2012 <= p["cropYear"] <= 2014]
    mean3 = sum(p["yield"] for p in history) / 3
    assert body["estimate"]["production"]["value"] == pytest.approx(
        body["estimate"]["area"]["value"] * mean3
    )


# --- errors --------------------------------------------------------------------------------


@pytest.mark.parametrize(
    "patch",
    [
        {"unexpected": 1},  # unknown field
        {"season": "MONSOON"},  # not a Season
        {"cropYear": "2015"},  # string, not an integer
        {"areaHectares": -5},
        {"districtId": "UP Agra"},  # not a contract id
    ],
)
def test_invalid_request_is_422_validation_error(client, patch):
    err = assert_error(post(client, **patch), 422, "VALIDATION_ERROR")
    assert err.details["errors"]


def test_missing_field_is_422(client):
    body = {k: v for k, v in VALID.items() if k != "cropId"}
    assert_error(client.post("/v1/predict/supply", json=body), 422, "VALIDATION_ERROR")


@pytest.mark.parametrize(
    ("patch", "status", "code"),
    [
        ({"districtId": "ka-ramanagara"}, 422, "UNSUPPORTED_DISTRICT"),
        ({"cropId": "tomato"}, 422, "UNSUPPORTED_CROP"),
        ({"season": "AUTUMN"}, 422, "UNSUPPORTED_SEASON"),
        ({"districtId": "up-meerut", "cropId": "wheat"}, 422, "UNSUPPORTED_SERIES"),
        ({"cropYear": 2016}, 422, "INSUFFICIENT_HISTORY"),
        ({"cropYear": 1997}, 422, "INSUFFICIENT_HISTORY"),
        ({"districtId": "up-varanasi", "cropYear": 2010}, 422, "INSUFFICIENT_HISTORY"),
        ({"areaHectares": 2.0}, 422, "AREA_OUT_OF_RANGE"),
    ],
)
def test_unanswerable_requests_are_rejected(client, patch, status, code):
    assert_error(post(client, **patch), status, code)


def test_model_not_loaded_is_503(tmp_path):
    client = TestClient(create_app(supply_model_dir=tmp_path / "missing"))
    assert_error(client.post("/v1/predict/supply", json=VALID), 503, "MODEL_NOT_LOADED")
    assert_error(client.get("/v1/reference/scope"), 503, "MODEL_NOT_LOADED")


def test_prediction_failure_is_internal_error(client, monkeypatch):
    def boom(*_, **__):
        raise ValueError("bad matrix")

    monkeypatch.setattr("agri_ml.inference.supply.predict_production", boom)
    assert_error(post(client), 500, "INTERNAL_ERROR")


def test_unexpected_failure_is_internal_error(client, monkeypatch):
    def boom(*_, **__):
        raise RuntimeError("bug")

    monkeypatch.setattr("agri_ml.inference.supply.SupplyModel.predict", boom)
    assert_error(post(client), 500, "INTERNAL_ERROR")


def test_unknown_route_uses_error_envelope(client):
    assert_error(client.get("/v1/predict/unknown"), 404, "NOT_FOUND")


# --- readiness and reference ---------------------------------------------------------------


def test_health_ready_with_versions(client):
    body = client.get("/health").json()
    assert body["status"] == "UP" and body["version"] == __version__
    assert body["models"] == [
        {
            "capability": "SUPPLY",
            "status": "READY",
            "modelVersion": "supply-test",
            "datasetVersion": "synthetic-test",
            "featureVersion": "supply-features-v2",
            "reason": None,
        }
    ]


def test_health_not_ready_without_artifact(tmp_path):
    body = TestClient(create_app(supply_model_dir=tmp_path / "missing")).get("/health").json()
    assert body["status"] == "UP"
    assert body["models"][0]["status"] == "NOT_READY"
    assert body["models"][0]["reason"] == "MODEL_NOT_LOADED"


def test_reference_scope_lists_only_supported_combinations(client):
    body = ScopeResponse.model_validate(client.get("/v1/reference/scope").json())
    assert [c.crop_id for c in body.crops] == ["potato", "wheat"]
    assert [s.value for s in body.seasons] == ["RABI"]
    series = {(s.district_id, s.crop_id) for s in body.supply_series}
    assert ("up-meerut", "wheat") not in series and ("up-agra", "potato") in series
    assert body.market_series == []
    assert body.datasets[0].data_through == "2014"


# --- contract examples ---------------------------------------------------------------------


@pytest.mark.parametrize(
    ("name", "model"),
    [
        ("supply-request.json", SupplyRequest),
        ("supply-response.json", SupplyResponse),
        ("supply-response-backtest.json", SupplyResponse),
        ("reference-scope-response.json", ScopeResponse),
        ("health-response.json", HealthResponse),
        ("error-unsupported-crop.json", ErrorResponse),
        ("error-insufficient-history.json", ErrorResponse),
        ("error-validation.json", ErrorResponse),
    ],
)
def test_contract_examples_match_schemas(name, model):
    model.model_validate(json.loads((EXAMPLES / name).read_text()))


# --- the real trained artifact -------------------------------------------------------------


def test_real_artifact_serves_prediction():
    model_dir = get_settings().supply_model_dir
    if not (model_dir / "model.json").is_file():
        pytest.skip(f"real supply artifact not built at {model_dir}")
    client = TestClient(create_app(supply_model_dir=model_dir))
    scope = client.get("/v1/reference/scope").json()
    s = next(s for s in scope["supplySeries"] if s["lastYear"] == 2014)
    r = client.post(
        "/v1/predict/supply",
        json={
            "districtId": s["districtId"],
            "cropId": s["cropId"],
            "season": s["season"],
            "cropYear": 2015,
        },
    )
    assert r.status_code == 200, r.text
    assert r.json()["estimate"]["production"]["value"] >= 0
