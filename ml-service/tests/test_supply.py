"""Supply pipeline, inference and API tests.

Plumbing tests use a tiny model trained on explicitly SYNTHETIC rows generated here. They check
code paths only and say nothing about model quality (see docs/model-cards/supply.md for that).
The last test exercises the real trained artifact and is skipped when it has not been built.
"""

import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient

from agri_ml.api.app import create_app
from agri_ml.config import get_settings
from agri_ml.datasets.crop_production import clean
from agri_ml.features.supply import build_features
from agri_ml.inference.supply import HistoryPoint, InvalidInputError, ModelLoadError, SupplyModel
from agri_ml.training.supply import SERIES_KEYS, Split, save_artifact, train_and_evaluate

SPLIT = Split(train_end=2010, val_start=2011, val_end=2012, test_start=2013, test_end=2014)


def synthetic_clean_frame() -> pd.DataFrame:
    """SYNTHETIC test data only; never used for training a served model."""
    rng = np.random.default_rng(0)
    rows = []
    for state in ["A", "B", "C", "D"]:
        for crop, season, base_yield in [("Potato", "Rabi", 25.0), ("Wheat", "Rabi", 3.0)]:
            area = rng.uniform(5_000, 20_000)
            for year in range(1998, 2015):
                y = base_yield * rng.uniform(0.8, 1.2)
                a = area * rng.uniform(0.9, 1.1)
                rows.append({"state_name": f"Synthetic {state}", "crop": crop,
                             "season": season, "crop_year": year, "area": a, "production": a * y})
    return pd.DataFrame(rows)


@pytest.fixture(scope="module")
def model_dir(tmp_path_factory):
    result = train_and_evaluate(
        synthetic_clean_frame(), SPLIT, dataset_version="synthetic-test",
        param_grid=[{"max_depth": 2, "min_child_weight": 1}],
    )
    return save_artifact(result, tmp_path_factory.mktemp("art") / "supply-test", "supply-test")


@pytest.fixture
def client(model_dir, tmp_path):
    # No disease model, so /health lists only the supply model under test.
    return TestClient(create_app(supply_model_dir=model_dir,
                                 disease_model_dir=tmp_path / "no-disease-model"))


VALID = {
    "crop": "Potato",
    "season": "Rabi",
    "cropYear": 2015,
    "areaHectares": 10000,
    "history": [
        {"cropYear": 2014, "areaHectares": 9800, "productionTonnes": 245000},
        {"cropYear": 2013, "areaHectares": 9500, "productionTonnes": 230000},
    ],
}


# --- preprocessing -------------------------------------------------------------------------

def test_clean_normalises_and_drops_invalid_rows():
    raw = pd.DataFrame({
        "State_Name": ["Uttar Pradesh"] * 5,
        "District_Name": ["AGRA", "AGRA", "AGRA", "MATHURA", "MATHURA"],
        "Crop_Year": ["2000", "2000", "2001", "2000", "2001"],
        "Season": ["Rabi       ", "Rabi       ", "Rabi", "Rabi", "Rabi"],
        "Crop": ["Potato"] * 5,
        "Area": ["100", "100", "0", "50", "60"],
        "Production": ["2000", "2000", "10", "", "900"],
    })
    out, report = clean(raw)
    assert report["dropped_exact_duplicates"] == 1
    assert report["dropped_missing_or_nonpositive_area"] == 1
    assert report["dropped_missing_production"] == 1
    assert set(out["season"]) == {"Rabi"}
    assert len(out) == 2


def test_features_use_only_previous_years():
    df = synthetic_clean_frame()
    base = build_features(df, SERIES_KEYS)
    changed = df.copy()
    changed.loc[changed["crop_year"] == 2012, "production"] *= 10
    after = build_features(changed, SERIES_KEYS)
    cols = ["log_yield_mean3", "log_yield_rel_lag1", "log_area_ratio_lag1"]
    # Changing 2012 production must not affect 2012 features, only later years.
    pd.testing.assert_frame_equal(base.loc[base.crop_year <= 2012, cols],
                                  after.loc[after.crop_year <= 2012, cols])
    in_2013 = after.loc[after.crop_year == 2013, cols]
    assert not base.loc[base.crop_year == 2013, cols].equals(in_2013)


def test_missing_previous_year_is_not_filled_from_older_years():
    df = synthetic_clean_frame()
    df = df[df["crop_year"] != 2010]
    f = build_features(df, SERIES_KEYS)
    assert f.loc[f.crop_year == 2011, "yield_lag1"].isna().all()


# --- inference -----------------------------------------------------------------------------

def test_model_loads_and_predicts_positive(model_dir):
    model = SupplyModel.load(model_dir)
    pred = model.predict("Potato", "Rabi", 2015, 10000,
                         [HistoryPoint(2014, 9800, 245000), HistoryPoint(2013, 9500, 230000)])
    assert pred.value > 0
    assert pred.lower <= pred.value <= pred.upper
    assert pred.history_years_used == 2


def test_model_load_failure_on_missing_dir(tmp_path):
    with pytest.raises(ModelLoadError):
        SupplyModel.load(tmp_path / "nope")


def test_model_load_failure_on_corrupt_file(tmp_path, model_dir):
    bad = tmp_path / "bad"
    bad.mkdir()
    (bad / "metadata.json").write_text((model_dir / "metadata.json").read_text())
    (bad / "model.json").write_text("not a model")
    with pytest.raises(ModelLoadError):
        SupplyModel.load(bad)


def test_inference_rejects_history_without_previous_year(model_dir):
    model = SupplyModel.load(model_dir)
    with pytest.raises(InvalidInputError):
        model.predict("Potato", "Rabi", 2015, 10000, [HistoryPoint(2012, 9800, 245000)])


# --- API -----------------------------------------------------------------------------------

def test_health_lists_loaded_model(client):
    assert client.get("/health").json()["loadedModels"] == ["supply-test"]


def test_predict_supply_valid_request(client):
    response = client.post("/v1/predict/supply", json=VALID)
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"modelName", "modelVersion", "dataClassification", "prediction",
                         "evidence", "provenance", "generatedAt"}
    assert body["modelVersion"] == "supply-test"
    assert body["dataClassification"] == "MODEL_PREDICTION"
    assert body["prediction"]["unit"] == "tonnes"
    assert body["prediction"]["value"] > 0
    assert body["prediction"]["period"] == "crop year 2015, Rabi season"
    assert set(body["prediction"]["interval"]) == {
        "lower", "upper", "nominalCoverage", "method", "testEmpiricalCoverage"}
    assert set(body["provenance"]) == {"datasetVersion", "featureVersion", "trainedAt",
                                       "trainingPeriod", "evaluationPeriod",
                                       "trainingDataSource", "spatialGranularity"}
    assert body["provenance"]["spatialGranularity"] == "state x crop x season x crop_year"
    # The synthetic test artifact has no trainingData block, so the source is null, not invented.
    assert body["provenance"]["trainingDataSource"] is None


@pytest.mark.parametrize("patch", [
    {"crop": "Tomato"},                                   # unsupported crop
    {"season": "Monsoon"},                                # unsupported season
    {"areaHectares": -5},                                 # invalid area
    {"history": []},                                      # no history
    {"history": [{"cropYear": 2012, "areaHectares": 1, "productionTonnes": 1}]},  # no t-1
    {"history": [{"cropYear": 2015, "areaHectares": 1, "productionTonnes": 1}]},  # future year
    {"unexpected": 1},                                    # unknown field
])
def test_predict_supply_invalid_input_returns_422(client, patch):
    assert client.post("/v1/predict/supply", json={**VALID, **patch}).status_code == 422


def test_predict_supply_returns_503_without_model(tmp_path):
    client = TestClient(create_app(supply_model_dir=tmp_path / "missing"))
    assert client.post("/v1/predict/supply", json=VALID).status_code == 503


REAL_MODEL_DIR = get_settings().supply_model_dir
real_model_only = pytest.mark.skipif(
    not (REAL_MODEL_DIR / "model.json").is_file(),
    reason=f"real supply artifact not built at {REAL_MODEL_DIR}",
)


@real_model_only
def test_real_artifact_serves_prediction():
    client = TestClient(create_app(supply_model_dir=REAL_MODEL_DIR))
    response = client.post("/v1/predict/supply", json=VALID)
    assert response.status_code == 200
    body = response.json()
    assert body["prediction"]["value"] > 0
    assert body["provenance"]["trainingDataSource"].startswith("Kaggle")
    assert body["provenance"]["spatialGranularity"].startswith("state")


@real_model_only
def test_real_artifact_metadata_is_chronological_and_kaggle_sourced():
    meta = SupplyModel.load(REAL_MODEL_DIR).metadata
    split = meta["split"]
    assert split["train_end"] < split["val_start"] <= split["val_end"] < split["test_start"]
    assert split["test_end"] <= 2019  # 2020 covers one state only
    assert meta["datasetVersion"].startswith("crop_yield-sha256-")
    assert meta["trainingData"]["spatialGranularity"] == "state"
    assert "Coconut" not in meta["features"]["categories"]["crop"]
    # Baselines are always reported next to the model.
    for part in ("validation", "test"):
        assert {"model", "naive_last_year_production", "area_x_last_year_yield",
                "area_x_mean3_yield"} <= set(meta["metrics"][part])
