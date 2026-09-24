from fastapi.testclient import TestClient

from agri_ml import __version__
from agri_ml.api.app import create_app


def test_health_returns_up_with_camel_case_fields():
    client = TestClient(create_app())

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "agri-ml",
        "version": __version__,
        "environment": "local",
        "loadedModels": [],
    }


def test_unknown_route_returns_404():
    client = TestClient(create_app())

    assert client.get("/v1/predict/supply").status_code == 404
