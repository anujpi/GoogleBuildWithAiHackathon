import logging
from datetime import UTC, datetime
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request

from agri_ml import __version__
from agri_ml.config import Settings, get_settings
from agri_ml.inference.supply import HistoryPoint, InvalidInputError, ModelLoadError, SupplyModel
from agri_ml.schemas.common import DataClassification
from agri_ml.schemas.health import HealthResponse
from agri_ml.schemas.supply import (
    PredictionInterval,
    SupplyEvidence,
    SupplyPredictionRequest,
    SupplyPredictionResponse,
    SupplyPredictionValue,
    SupplyProvenance,
)

SERVICE_NAME = "agri-ml"
log = logging.getLogger(__name__)


def _load_supply_model(model_dir: Path) -> SupplyModel | None:
    try:
        return SupplyModel.load(model_dir)
    except ModelLoadError as exc:
        # The service still starts; /health lists no model and predictions return 503.
        log.warning("%s", exc)
        return None


def get_supply_model(request: Request) -> SupplyModel:
    model = request.app.state.supply_model
    if model is None:
        raise HTTPException(status_code=503, detail="supply model is not loaded")
    return model


def create_app(supply_model_dir: Path | None = None) -> FastAPI:
    app = FastAPI(
        title="Agricultural Intelligence ML Service",
        version=__version__,
        description="Structured predictions for the Spring Boot backend. No advisory text.",
    )
    app.state.supply_model = _load_supply_model(
        supply_model_dir or get_settings().supply_model_dir
    )

    @app.get("/health", response_model=HealthResponse, tags=["ops"])
    def health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
        model = app.state.supply_model
        return HealthResponse(
            status="UP",
            service=SERVICE_NAME,
            version=__version__,
            environment=settings.environment,
            loaded_models=[model.version] if model else [],
        )

    @app.post(
        "/v1/predict/supply",
        response_model=SupplyPredictionResponse,
        response_model_by_alias=True,
        tags=["predict"],
    )
    def predict_supply(
        body: SupplyPredictionRequest,
        model: Annotated[SupplyModel, Depends(get_supply_model)],
    ) -> SupplyPredictionResponse:
        history = [
            HistoryPoint(h.crop_year, h.area_hectares, h.production_tonnes) for h in body.history
        ]
        try:
            result = model.predict(
                body.crop, body.season, body.crop_year, body.area_hectares, history
            )
        except InvalidInputError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

        meta = model.metadata
        interval = None
        if result.lower is not None:
            interval = PredictionInterval(
                lower=result.lower,
                upper=result.upper,
                nominal_coverage=meta["interval"]["nominalCoverage"],
                method=meta["interval"]["method"],
                test_empirical_coverage=meta["interval"]["testEmpiricalCoverage"],
            )
        return SupplyPredictionResponse(
            model_name=meta["modelName"],
            model_version=model.version,
            data_classification=DataClassification.MODEL_PREDICTION,
            prediction=SupplyPredictionValue(
                value=result.value,
                unit=meta["target"]["unit"],
                period=f"crop year {body.crop_year}, {body.season} season",
                interval=interval,
            ),
            evidence=SupplyEvidence(
                baseline_value=result.baseline_value,
                baseline_method="area x mean reported yield of up to 3 previous crop years",
                history_years_used=result.history_years_used,
            ),
            provenance=SupplyProvenance(
                dataset_version=meta["datasetVersion"],
                feature_version=meta["featureVersion"],
                trained_at=meta["trainedAt"],
                training_period=meta["trainingPeriod"],
                evaluation_period=meta["testPeriod"],
                training_data_source=meta.get("trainingData", {}).get("source"),
                spatial_granularity=meta["target"].get("granularity"),
            ),
            generated_at=datetime.now(UTC),
        )

    return app


app = create_app()
