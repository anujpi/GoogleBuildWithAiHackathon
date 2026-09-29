"""The training pipeline end to end, and the CLI scripts' refusals (SYNTHETIC data)."""

import json
import runpy
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from agri_ml.api.app import create_app
from agri_ml.config import get_settings
from agri_ml.inference.supply import SupplyModel
from agri_ml.training.pipeline import run_supply_pipeline
from conftest import synthetic_raw

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
GRID = [{"max_depth": 2, "min_child_weight": 1}]


@pytest.fixture
def raw_dir(tmp_path):
    d = tmp_path / "raw"
    d.mkdir()
    synthetic_raw().to_csv(d / "synthetic.csv", index=False)
    return d


def test_synthetic_run_builds_a_servable_artifact_labelled_synthetic(raw_dir, tmp_path):
    result = run_supply_pipeline(
        raw_dir, tmp_path / "artifacts", "pipeline-test", synthetic_fixture=True,
        required_crops=("potato", "wheat"), param_grid=GRID,
    )
    names = {p.name for p in result.artifact_dir.iterdir()}
    assert names == {"model.json", "metadata.json", "series.parquet", "scored.csv",
                     "error_analysis.json", "evaluation_report.md"}
    meta = result.metadata
    assert meta["data"] == {"source": "TEST_FIXTURE", "classification": "SYNTHETIC"}
    assert meta["sourceManifest"] is None
    assert meta["datasetVersion"].startswith("synthetic-")
    report = (result.artifact_dir / "evaluation_report.md").read_text(encoding="utf-8")
    # The report is generated from the measured metrics, not typed.
    assert f"{meta['selection']['testWape'][meta['servedMethod']]:.4f}" in report
    analysis = json.loads((result.artifact_dir / "error_analysis.json").read_text())
    worst = analysis["worst10Predictions"]
    assert worst and all(f"pred_{meta['servedMethod']}" in row for row in worst)
    body = TestClient(create_app(supply_model_dir=result.artifact_dir)).post(
        "/v1/predict/supply",
        json={"districtId": "up-agra", "cropId": "potato", "season": "RABI", "cropYear": 2015},
    ).json()
    assert body["history"]["provenance"]["dataClassification"] == "SYNTHETIC"
    assert SupplyModel.load(result.artifact_dir).version == "pipeline-test"


def test_real_run_refuses_data_without_a_valid_manifest(raw_dir, tmp_path):
    with pytest.raises(FileNotFoundError, match="manifest"):
        run_supply_pipeline(raw_dir, tmp_path / "artifacts", "v", param_grid=GRID)
    assert not (tmp_path / "artifacts").exists()


def test_missing_required_crop_fails_loudly(raw_dir, tmp_path):
    with pytest.raises(ValueError, match="maize"):
        run_supply_pipeline(raw_dir, tmp_path / "artifacts", "v", synthetic_fixture=True,
                            param_grid=GRID)


def test_an_existing_version_is_never_overwritten(raw_dir, tmp_path):
    args = (raw_dir, tmp_path / "artifacts", "v")
    kwargs = {"synthetic_fixture": True, "required_crops": ("potato",), "param_grid": GRID}
    run_supply_pipeline(*args, **kwargs)
    with pytest.raises(FileExistsError):
        run_supply_pipeline(*args, **kwargs)


# --- CLI scripts ---------------------------------------------------------------------------


@pytest.fixture
def empty_data_dir(tmp_path, monkeypatch):
    monkeypatch.setenv("AGRI_ML_DATA_DIR", str(tmp_path / "data"))
    monkeypatch.setenv("AGRI_ML_ARTIFACTS_DIR", str(tmp_path / "artifacts"))
    get_settings.cache_clear()
    yield tmp_path
    get_settings.cache_clear()


def run_script(name, monkeypatch, *args):
    monkeypatch.setattr("sys.argv", [name, *args])
    with pytest.raises(SystemExit) as exc:
        runpy.run_path(str(SCRIPTS / name), run_name="__main__")
    return str(exc.value.code)


def test_download_requires_a_registered_key(empty_data_dir, monkeypatch):
    monkeypatch.delenv("DATA_GOV_IN_API_KEY", raising=False)
    assert "DATA_GOV_IN_API_KEY is not set" in run_script(
        "download_s01_crop_production.py", monkeypatch)
    assert not (empty_data_dir / "data" / "raw" / "s01_crop_production" / "manifest.json").exists()


def test_training_without_real_data_aborts_clearly(empty_data_dir, monkeypatch):
    message = run_script("train_supply.py", monkeypatch)
    assert message.startswith("training aborted") and "download_s01_crop_production" in message
    assert not (empty_data_dir / "artifacts").exists()


def test_training_refuses_an_unregistered_download(empty_data_dir, monkeypatch):
    raw = empty_data_dir / "data" / "raw" / "s01_crop_production"
    raw.mkdir(parents=True)
    synthetic_raw().to_csv(raw / "up.csv", index=False)
    (raw / "manifest.json").write_text(json.dumps({
        "resourceId": "35be999b-0208-4354-b557-f6ca9a5355de", "keyType": "SAMPLE",
        "files": {"up.csv": {"rows": 1, "apiTotal": 1}}}))
    assert "registered API key" in run_script("train_supply.py", monkeypatch)
