from typing import Annotated

from fastapi import Depends, FastAPI

from agri_ml import __version__
from agri_ml.config import Settings, get_settings
from agri_ml.schemas.health import HealthResponse

SERVICE_NAME = "agri-ml"


def create_app() -> FastAPI:
    app = FastAPI(
        title="Agricultural Intelligence ML Service",
        version=__version__,
        description="Structured predictions for the Spring Boot backend. No advisory text.",
    )

    @app.get("/health", response_model=HealthResponse, tags=["ops"])
    def health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
        return HealthResponse(
            status="UP",
            service=SERVICE_NAME,
            version=__version__,
            environment=settings.environment,
            loaded_models=[],
        )

    return app


app = create_app()
