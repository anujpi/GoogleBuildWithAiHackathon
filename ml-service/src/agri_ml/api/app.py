import logging
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from agri_ml import __version__
from agri_ml.config import Settings, get_settings
from agri_ml.inference.supply import ModelLoadError, SupplyError, SupplyModel
from agri_ml.schemas.common import ErrorResponse
from agri_ml.schemas.health import HealthResponse, ModelReadiness, ModelStatus
from agri_ml.schemas.reference import ScopeResponse
from agri_ml.schemas.supply import SupplyRequest, SupplyResponse

SERVICE_NAME = "agri-ml"
log = logging.getLogger(__name__)

ERROR_RESPONSES = {code: {"model": ErrorResponse} for code in (404, 422, 500, 503)}


def error(status: int, code: str, message: str, details: dict | None = None) -> JSONResponse:
    body = {"error": {"code": code, "message": message, "details": details or {}}}
    return JSONResponse(status_code=status, content=body)


def create_app(supply_model_dir: Path | None = None) -> FastAPI:
    app = FastAPI(
        title="Agricultural Intelligence ML Service",
        version=__version__,
        description="Structured predictions for the Spring Boot backend. No advisory text. "
        "Contract: docs/ml-contracts/.",
    )
    try:
        app.state.supply_model = SupplyModel.load(
            supply_model_dir or get_settings().supply_model_dir
        )
        app.state.supply_error = None
    except ModelLoadError as exc:
        # The service still starts; /health reports NOT_READY and predictions answer 503.
        log.warning("%s", exc)
        app.state.supply_model, app.state.supply_error = None, exc

    def supply_model() -> SupplyModel:
        if app.state.supply_model is None:
            exc = app.state.supply_error
            raise SupplyError(exc.code, 503, "supply model is not available")
        return app.state.supply_model

    @app.exception_handler(SupplyError)
    def supply_error(_: Request, exc: SupplyError) -> JSONResponse:
        return error(exc.status, exc.code, exc.message, exc.details)

    @app.exception_handler(RequestValidationError)
    def invalid_request(_: Request, exc: RequestValidationError) -> JSONResponse:
        problems = [
            {"field": ".".join(str(p) for p in e["loc"][1:]), "message": e["msg"]}
            for e in exc.errors()
        ]
        # MASTER_SPEC §9.3: schema violations are 422 VALIDATION_ERROR.
        return error(422, "VALIDATION_ERROR", "request validation failed", {"errors": problems})

    @app.exception_handler(StarletteHTTPException)
    def http_error(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = {404: "NOT_FOUND", 405: "METHOD_NOT_ALLOWED"}.get(exc.status_code, "HTTP_ERROR")
        return error(exc.status_code, code, str(exc.detail))

    @app.exception_handler(Exception)
    def internal_error(_: Request, exc: Exception) -> JSONResponse:
        log.exception("unhandled error")
        return error(500, "INTERNAL_ERROR", "unexpected error")

    @app.get("/health", response_model=HealthResponse, tags=["ops"])
    def health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
        model, exc = app.state.supply_model, app.state.supply_error
        status = ModelStatus(
            capability="SUPPLY",
            status=ModelReadiness.READY if model else ModelReadiness.NOT_READY,
            model_version=model.version if model else None,
            dataset_version=model.metadata["datasetVersion"] if model else None,
            feature_version=model.metadata["featureVersion"] if model else None,
            reason=None if model else exc.code,
        )
        return HealthResponse(
            status="UP",
            service=SERVICE_NAME,
            version=__version__,
            environment=settings.environment,
            models=[status],
        )

    @app.get(
        "/v1/reference/scope",
        response_model=ScopeResponse,
        responses=ERROR_RESPONSES,
        tags=["reference"],
    )
    def scope() -> ScopeResponse:
        model = supply_model()
        s = model.scope
        return ScopeResponse.model_validate(
            {
                "states": s["states"],
                "districts": s["districts"],
                "crops": s["crops"],
                "seasons": s["seasons"],
                "supplySeries": s["supplySeries"],
                "marketSeries": [],
                "datasets": [
                    {
                        "source": model.data_source,
                        "datasetVersion": model.metadata["datasetVersion"],
                        "dataThrough": model.data_through,
                    }
                ],
            }
        )

    @app.post(
        "/v1/predict/supply",
        response_model=SupplyResponse,
        responses=ERROR_RESPONSES,
        tags=["predict"],
    )
    def predict_supply(body: SupplyRequest) -> SupplyResponse:
        return supply_model().predict(body)

    return app


app = create_app()
