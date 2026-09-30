"""Disease dataset split, inference and API tests.

Plumbing tests use an UNTRAINED network (random weights, fixed seed) saved as a throwaway
artifact, and small images generated here. Both are SYNTHETIC test inputs: they check code
paths only and say nothing about model quality (see docs/model-cards/disease.md for that).
Tests marked real_* use the trained artifact and real PlantVillage test-split images, and are
skipped when those have not been built. No GPU is needed.
"""

import io
import json

import numpy as np
import pandas as pd
import pytest
import torch
from fastapi.testclient import TestClient
from PIL import Image

from agri_ml.api.app import create_app
from agri_ml.config import get_settings
from agri_ml.datasets import plantvillage as pv
from agri_ml.inference.disease import (
    PREPROCESSING,
    DiseaseModel,
    InvalidImageError,
    ModelLoadError,
    build_network,
    decode_image,
)

LABELS = sorted(["Potato___Early_blight", "Potato___Late_blight", "Potato___healthy",
                 "Tomato___healthy"])


def png_bytes(color=(40, 160, 60), size=(64, 48), fmt="PNG") -> bytes:
    """SYNTHETIC solid-colour image; used only to exercise decoding and the API."""
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, format=fmt)
    return buf.getvalue()


@pytest.fixture(scope="module")
def model_dir(tmp_path_factory):
    torch.manual_seed(0)
    d = tmp_path_factory.mktemp("disease") / "disease-test"
    d.mkdir()
    torch.save(build_network(len(LABELS)).state_dict(), d / "model.pt")
    meta = {
        "modelName": "leaf-disease-classifier", "modelVersion": "disease-test",
        "architecture": "mobilenet_v3_large", "featureVersion": "disease-prep-v1",
        "datasetVersion": "synthetic-test", "trainedAt": "2026-01-01T00:00:00+00:00",
        "classes": [{"index": i, **vars(pv.ClassName.parse(lab))} for i, lab in enumerate(LABELS)],
        "preprocessing": PREPROCESSING,
        "lowConfidenceThreshold": None,
        "trainingData": {"source": "SYNTHETIC test artifact", "license": "n/a"},
    }
    (d / "metadata.json").write_text(json.dumps(meta))
    return d


@pytest.fixture
def client(model_dir, tmp_path):
    return TestClient(create_app(supply_model_dir=tmp_path / "no-supply",
                                 disease_model_dir=model_dir))


def post(client, data: bytes, content_type="image/png", name="leaf.png"):
    return client.post("/v1/predict/disease", files={"image": (name, data, content_type)})


# --- class mapping and split -------------------------------------------------------------------

def test_class_label_parsing():
    c = pv.ClassName.parse("Tomato___Spider_mites Two-spotted_spider_mite")
    assert (c.crop, c.disease) == ("Tomato", "Spider_mites Two-spotted_spider_mite")
    with pytest.raises(ValueError):
        pv.ClassName.parse("Tomato_healthy")


def test_class_mapping_is_deterministic(model_dir):
    model = DiseaseModel.load(model_dir)
    assert [c["label"] for c in model.classes] == LABELS
    assert [c["index"] for c in model.classes] == list(range(len(LABELS)))


def test_model_rejects_unordered_class_mapping(tmp_path, model_dir):
    bad = tmp_path / "bad"
    bad.mkdir()
    meta = json.loads((model_dir / "metadata.json").read_text())
    meta["classes"][0]["index"], meta["classes"][1]["index"] = 1, 0
    (bad / "metadata.json").write_text(json.dumps(meta))
    (bad / "model.pt").write_bytes((model_dir / "model.pt").read_bytes())
    with pytest.raises(ModelLoadError):
        DiseaseModel.load(bad)


def test_file_stem_drops_uuid_extension_and_repeat_suffix():
    assert pv.file_stem("raw/color/Tomato___healthy/uuid___GH_HL Leaf 211.1.JPG") == \
        "GH_HL Leaf 211"
    assert pv.file_stem("raw/color/Tomato___healthy/uuid___GH_HL Leaf 211.JPG") == \
        "GH_HL Leaf 211"


def synthetic_index() -> pd.DataFrame:
    """SYNTHETIC split index: 2 classes x 40 leaves, 2 photos per leaf."""
    rng = np.random.default_rng(1)
    rows = []
    for label in ["Potato___healthy", "Tomato___healthy"]:
        for leaf in range(40):
            h = f"{int(rng.integers(0, 2**63)):016x}"
            for k in ("", ".1"):
                rows.append({"path": f"raw/color/{label}/u{leaf}{k}___L {leaf}{k}.JPG",
                             "label": label, "leaf_id": f"fallback_L {leaf}{k}", "dhash": h})
    return pd.DataFrame(rows)


def test_split_is_reproducible_and_keeps_groups_whole():
    idx = synthetic_index()
    a, b = pv.make_split(idx, seed=7), pv.make_split(idx, seed=7)
    assert a["split"].tolist() == b["split"].tolist()
    # Both photos of a leaf share a stem and hash, so they must land in the same split.
    assert (a.groupby("group")["split"].nunique() == 1).all()
    assert a.groupby("group").size().eq(2).all()
    report = pv.split_leakage_report(a)
    assert all(v["sharedGroups"] == 0 and v["sharedDhash"] == 0 for v in report.values())
    assert set(a["split"]) == {"train", "val", "test"}


def test_near_duplicate_hashes_are_grouped():
    idx = synthetic_index().iloc[:4].copy()
    idx["leaf_id"] = ["a", "b", "c", "d"]
    idx["path"] = [f"raw/color/Potato___healthy/u___img{i}.JPG" for i in range(4)]
    base = int(idx["dhash"].iloc[0], 16)
    idx["dhash"] = [f"{base:016x}", f"{base ^ 0b111:016x}", f"{~base & (2**64 - 1):016x}",
                    f"{(~base & (2**64 - 1)) ^ 0xFF:016x}"]
    g = pv.group_ids(idx, near_dup_bits=6)
    assert g[0] == g[1]      # 3 bits apart
    assert g[0] != g[2]      # 64 bits apart
    assert g[2] != g[3]      # 8 bits apart, above the threshold


# --- inference ----------------------------------------------------------------------------------

def test_model_load_failure_on_missing_dir(tmp_path):
    with pytest.raises(ModelLoadError):
        DiseaseModel.load(tmp_path / "nope")


def test_model_load_failure_on_corrupt_weights(tmp_path, model_dir):
    bad = tmp_path / "bad"
    bad.mkdir()
    (bad / "metadata.json").write_text((model_dir / "metadata.json").read_text())
    (bad / "model.pt").write_bytes(b"not a model")
    with pytest.raises(ModelLoadError):
        DiseaseModel.load(bad)


def test_inference_returns_probability_distribution(model_dir):
    ranked = DiseaseModel.load(model_dir).predict_bytes(png_bytes())
    probs = [c.probability for c in ranked]
    assert len(ranked) == len(LABELS)
    assert probs == sorted(probs, reverse=True)
    assert sum(probs) == pytest.approx(1.0, abs=1e-5)
    assert all(0 <= p <= 1 for p in probs)


@pytest.mark.parametrize("data", [b"", b"not an image at all", png_bytes()[:40]])
def test_decode_rejects_invalid_bytes(data):
    with pytest.raises(InvalidImageError):
        decode_image(data)


def test_decode_rejects_format_mismatch():
    with pytest.raises(InvalidImageError):
        decode_image(png_bytes(), expected_format="JPEG")


# --- API ----------------------------------------------------------------------------------------

def test_health_lists_disease_model(client):
    assert client.get("/health").json()["loadedModels"] == ["disease-test"]


def test_predict_disease_response_schema(client):
    response = post(client, png_bytes())
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"modelName", "modelVersion", "dataClassification", "prediction",
                         "confidence", "topClasses", "supportedCrops", "provenance"}
    assert body["dataClassification"] == "MODEL_PREDICTION"
    assert set(body["prediction"]) == {"classLabel", "crop", "disease", "probability"}
    assert body["prediction"] == body["topClasses"][0]
    assert body["prediction"]["classLabel"] in LABELS
    assert body["confidence"]["value"] == body["prediction"]["probability"]
    assert body["confidence"]["calibrated"] is False
    assert body["confidence"]["lowConfidenceThreshold"] is None
    assert len(body["topClasses"]) == 3
    assert body["supportedCrops"] == ["Potato", "Tomato"]
    assert set(body["provenance"]) == {"datasetVersion", "featureVersion", "modelVersion",
                                       "trainedAt", "trainingDataSource", "trainingDataLicense",
                                       "generatedAt"}
    assert body["provenance"]["modelVersion"] == "disease-test"


def test_predict_disease_accepts_jpeg(client):
    assert post(client, png_bytes(fmt="JPEG"), "image/jpeg", "leaf.jpg").status_code == 200


def test_predict_disease_missing_image_returns_422(client):
    assert client.post("/v1/predict/disease").status_code == 422


def test_predict_disease_unsupported_type_returns_415(client):
    assert post(client, b"hello", "text/plain", "leaf.txt").status_code == 415


@pytest.mark.parametrize("data,ctype", [
    (b"not an image at all", "image/png"),     # corrupt
    (png_bytes()[:40], "image/png"),           # truncated
    (b"", "image/jpeg"),                       # empty
    (png_bytes(), "image/jpeg"),               # PNG content declared as JPEG
])
def test_predict_disease_invalid_image_returns_422(client, data, ctype):
    assert post(client, data, ctype).status_code == 422


def test_predict_disease_returns_503_without_model(tmp_path):
    client = TestClient(create_app(supply_model_dir=tmp_path / "x",
                                   disease_model_dir=tmp_path / "missing"))
    assert post(client, png_bytes()).status_code == 503


# --- real artifact ------------------------------------------------------------------------------

REAL_DIR = get_settings().disease_model_dir
real_model_only = pytest.mark.skipif(
    not (REAL_DIR / "model.pt").is_file(), reason=f"real disease artifact not built at {REAL_DIR}"
)


@real_model_only
def test_real_artifact_metadata():
    meta = json.loads((REAL_DIR / "metadata.json").read_text())
    labels = [c["label"] for c in meta["classes"]]
    assert labels == sorted(labels) and len(labels) == 13
    assert {c["crop"] for c in meta["classes"]} == {"Potato", "Tomato"}
    assert meta["trainingData"]["url"] == pv.SOURCE_URL
    assert meta["metrics"]["test"]["n"] == sum(c["test"] for c in meta["splitCounts"].values())
    assert meta["lowConfidenceThreshold"] is None


@real_model_only
def test_real_artifact_predicts_real_test_image():
    meta = json.loads((REAL_DIR / "metadata.json").read_text())
    split_csv = (get_settings().data_dir / "processed" / pv.DATASET_NAME / meta["datasetVersion"]
                 / meta["splitVersion"] / "split.csv")
    if not split_csv.is_file():
        pytest.skip("PlantVillage split not prepared")
    row = pd.read_csv(split_csv).query("split == 'test'").iloc[0]
    data = (pv.raw_dir() / row["path"]).read_bytes()
    client = TestClient(create_app(disease_model_dir=REAL_DIR))
    body = post(client, data, "image/jpeg", "leaf.jpg").json()
    assert body["modelVersion"] == meta["modelVersion"]
    assert body["prediction"]["classLabel"] in {c["label"] for c in meta["classes"]}
