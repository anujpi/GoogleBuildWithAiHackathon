"""Tests for the transparent intelligence endpoints: crop suitability, demand, anomaly.

Anomaly tests use SYNTHETIC series (code-path checks only). Suitability and demand tests use the
real committed reference data / real crop_yield.csv and are skipped when those are absent.
"""

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from agri_ml.api.app import create_app
from agri_ml.config import get_settings
from agri_ml.models import anomaly, demand

SETTINGS = get_settings()
has_crop_yield = pytest.mark.skipif(not SETTINGS.crop_yield_csv.is_file(),
                                    reason="crop_yield.csv not present")


@pytest.fixture(scope="module")
def client(tmp_path_factory):
    missing = tmp_path_factory.mktemp("none") / "missing"
    return TestClient(create_app(supply_model_dir=missing, disease_model_dir=missing))


# --- anomaly (SYNTHETIC series) ------------------------------------------------------------------

def test_anomaly_flags_a_large_drop():
    r = anomaly.evaluate([20, 21, 19, 22, 20, 21, 23, 20, 21, 12], window=10, threshold=3.5)
    assert r["status"] == "ANOMALY" and r["direction"] == "LOW"
    assert r["baseline"] == 21 and r["window_used"] == 9


def test_anomaly_normal_value():
    r = anomaly.evaluate([20, 21, 19, 22, 20, 21, 23, 20, 21, 21.5], window=10, threshold=3.5)
    assert r["status"] == "NORMAL" and r["direction"] == "NONE"


def test_anomaly_insufficient_history():
    r = anomaly.evaluate([1, 2, 3], window=10, threshold=3.5)
    assert r["status"] == "INSUFFICIENT_DATA" and r["robust_z"] is None


def test_anomaly_flat_history_change_is_anomalous():
    r = anomaly.evaluate([5, 5, 5, 5, 5, 5, 6], window=10, threshold=3.5)
    assert r["status"] == "ANOMALY" and r["robust_z"] is None


def test_anomaly_api(client):
    series = [{"period": str(2010 + i), "value": v} for i, v in enumerate([1, 1, 2, 1, 2, 1, 9])]
    body = client.post("/v1/predict/anomaly", json={"metric": "m", "series": series}).json()
    assert body["status"] == "ANOMALY" and body["period"] == "2016"


def test_anomaly_api_rejects_empty_series(client):
    assert client.post("/v1/predict/anomaly", json={"metric": "m", "series": []}).status_code == 422


# --- demand (real committed FAOSTAT extract) -----------------------------------------------------

def test_demand_forecast_is_labelled_and_backtested(client):
    r = client.post("/v1/predict/demand", json={"crop": "Potato", "targetYear": 2025})
    assert r.status_code == 200
    b = r.json()
    assert b["dataClassification"] == "FORECAST" and b["unit"] == "tonnes"
    assert b["interval"]["lower"] < b["forecast"] < b["interval"]["upper"]
    assert {t["n"] for t in b["backtests"]} == {demand.BACKTEST_YEARS}
    assert b["region"] == "India"


def test_demand_observed_year_returns_observed_value(client):
    b = client.post("/v1/predict/demand", json={"crop": "Potato", "targetYear": 2020}).json()
    assert b["dataClassification"] == "OBSERVED" and b["interval"] is None
    assert b["forecast"] == pytest.approx(50677.0 * 1000)  # FAOSTAT 2020 domestic supply


def test_demand_state_share_is_estimated(client):
    nat = client.post("/v1/predict/demand", json={"crop": "Wheat", "targetYear": 2020}).json()
    st = client.post("/v1/predict/demand",
                     json={"crop": "Wheat", "targetYear": 2020, "state": "Uttar Pradesh"}).json()
    assert st["dataClassification"] == "ESTIMATED"
    share = st["evidence"]["stateShareOfPopulation2011"]
    assert 0.15 < share < 0.18
    assert st["forecast"] == pytest.approx(nat["forecast"] * share, rel=1e-3)


def test_demand_unsupported_crop_is_422(client):
    assert client.post("/v1/predict/demand",
                       json={"crop": "Saffron", "targetYear": 2025}).status_code == 422


def test_demand_missing_reference_data_is_503(tmp_path):
    demand.load_data.cache_clear()
    app = create_app(supply_model_dir=tmp_path / "x", disease_model_dir=tmp_path / "x")
    app.dependency_overrides[get_settings] = lambda: SETTINGS.model_copy(
        update={"reference_data_dir": Path(tmp_path)})
    r = TestClient(app).post("/v1/predict/demand", json={"crop": "Potato", "targetYear": 2025})
    assert r.status_code == 503


# --- crop suitability (real crop_yield.csv) ------------------------------------------------------

@has_crop_yield
def test_suitability_ranks_candidates_with_evidence(client):
    r = client.post("/v1/predict/crop-suitability",
                    json={"state": "Uttar Pradesh", "season": "Rabi", "topN": 5})
    assert r.status_code == 200
    b = r.json()
    scores = [c["suitabilityScore"] for c in b["candidates"]]
    assert scores == sorted(scores, reverse=True) and all(0 <= s <= 1 for s in scores)
    assert "not a probability" in b["scoreType"]
    top = b["candidates"][0]
    assert top["evidence"] and any("soil_ph" in n for n in top["notAssessed"])


@has_crop_yield
def test_suitability_soil_ph_changes_components(client):
    b = client.post("/v1/predict/crop-suitability",
                    json={"state": "Uttar Pradesh", "season": "Rabi", "soilPh": 6.5,
                          "irrigated": True}).json()
    wheat = next(c for c in b["candidates"] if c["crop"] == "Wheat")
    comp = {c["name"]: c["score"] for c in wheat["components"]}
    assert comp["soil_ph"] == 1.0 and comp["water"] == 1.0


@has_crop_yield
def test_suitability_unknown_state_is_422(client):
    assert client.post("/v1/predict/crop-suitability",
                       json={"state": "Atlantis", "season": "Rabi"}).status_code == 422
