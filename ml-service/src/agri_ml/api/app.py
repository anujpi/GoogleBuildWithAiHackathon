import logging
from datetime import UTC, datetime
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, File, HTTPException, Request, UploadFile

from agri_ml import __version__
from agri_ml.config import Settings, get_settings
from agri_ml.inference import disease as disease_inf
from agri_ml.inference.disease import DiseaseModel, InvalidImageError
from agri_ml.inference.supply import HistoryPoint, InvalidInputError, ModelLoadError, SupplyModel
from agri_ml.models import anomaly as anomaly_engine
from agri_ml.models import crop_suitability as suit
from agri_ml.models import demand as demand_model
from agri_ml.schemas.common import DataClassification
from agri_ml.schemas.disease import (
    DiseaseClass,
    DiseaseConfidence,
    DiseasePredictionResponse,
    DiseaseProvenance,
)
from agri_ml.schemas.health import HealthResponse
from agri_ml.schemas.intelligence import (
    AnomalyRequest,
    AnomalyResponse,
    DemandRequest,
    DemandResponse,
    Provenance,
    SuitabilityRequest,
    SuitabilityResponse,
)
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

# Uploads accepted by POST /v1/predict/disease. The decoded format is checked too, so a renamed
# or mislabelled file is still rejected.
DISEASE_CONTENT_TYPES = {"image/jpeg": "JPEG", "image/png": "PNG"}
DISEASE_MAX_UPLOAD_BYTES = 10 * 1024 * 1024
DISEASE_TOP_K = 3


def _load_supply_model(model_dir: Path) -> SupplyModel | None:
    try:
        return SupplyModel.load(model_dir)
    except ModelLoadError as exc:
        # The service still starts; /health lists no model and predictions return 503.
        log.warning("%s", exc)
        return None


def _load_disease_model(model_dir: Path) -> DiseaseModel | None:
    try:
        return DiseaseModel.load(model_dir)
    except disease_inf.ModelLoadError as exc:
        log.warning("%s", exc)
        return None


def get_disease_model(request: Request) -> DiseaseModel:
    model = request.app.state.disease_model
    if model is None:
        raise HTTPException(status_code=503, detail="disease model is not loaded")
    return model


def get_supply_model(request: Request) -> SupplyModel:
    model = request.app.state.supply_model
    if model is None:
        raise HTTPException(status_code=503, detail="supply model is not loaded")
    return model


def create_app(supply_model_dir: Path | None = None,
               disease_model_dir: Path | None = None) -> FastAPI:
    app = FastAPI(
        title="Agricultural Intelligence ML Service",
        version=__version__,
        description="Structured predictions for the Spring Boot backend. No advisory text.",
    )
    app.state.supply_model = _load_supply_model(
        supply_model_dir or get_settings().supply_model_dir
    )
    app.state.disease_model = _load_disease_model(
        disease_model_dir or get_settings().disease_model_dir
    )

    @app.get("/health", response_model=HealthResponse, tags=["ops"])
    def health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
        models = [m for m in (app.state.supply_model, app.state.disease_model) if m]
        return HealthResponse(
            status="UP",
            service=SERVICE_NAME,
            version=__version__,
            environment=settings.environment,
            loaded_models=[m.version for m in models],
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

    @app.post(
        "/v1/predict/disease",
        response_model=DiseasePredictionResponse,
        response_model_by_alias=True,
        tags=["predict"],
    )
    async def predict_disease(
        model: Annotated[DiseaseModel, Depends(get_disease_model)],
        image: Annotated[UploadFile, File(description="Leaf photo, JPEG or PNG, max 10 MB")],
    ) -> DiseasePredictionResponse:
        if image.content_type not in DISEASE_CONTENT_TYPES:
            raise HTTPException(
                status_code=415,
                detail=f"unsupported content type {image.content_type!r}; "
                f"supported: {sorted(DISEASE_CONTENT_TYPES)}",
            )
        data = await image.read(DISEASE_MAX_UPLOAD_BYTES + 1)
        if len(data) > DISEASE_MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail="image is larger than 10 MB")
        try:
            decoded = disease_inf.decode_image(data, DISEASE_CONTENT_TYPES[image.content_type])
            ranked = model.predict(decoded)
        except InvalidImageError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

        meta = model.metadata
        classes = [
            DiseaseClass(class_label=c.label, crop=c.crop, disease=c.disease,
                         probability=c.probability)
            for c in ranked
        ]
        top = classes[0]
        return DiseasePredictionResponse(
            model_name=meta["modelName"],
            model_version=model.version,
            data_classification=DataClassification.MODEL_PREDICTION,
            prediction=top,
            confidence=DiseaseConfidence(
                value=top.probability,
                method="softmax probability of the predicted class",
                calibrated=False,
                low_confidence_threshold=meta.get("lowConfidenceThreshold"),
            ),
            top_classes=classes[:DISEASE_TOP_K],
            supported_crops=sorted({c["crop"] for c in meta["classes"]}),
            provenance=DiseaseProvenance(
                dataset_version=meta["datasetVersion"],
                feature_version=meta["featureVersion"],
                model_version=model.version,
                trained_at=meta["trainedAt"],
                training_data_source=meta["trainingData"]["source"],
                training_data_license=meta["trainingData"]["license"],
                generated_at=datetime.now(UTC),
            ),
        )

    @app.post(
        "/v1/predict/crop-suitability",
        response_model=SuitabilityResponse,
        response_model_by_alias=True,
        tags=["predict"],
    )
    def predict_crop_suitability(
        body: SuitabilityRequest, settings: Annotated[Settings, Depends(get_settings)]
    ) -> SuitabilityResponse:
        try:
            data = suit.load_data(settings.crop_yield_csv)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        try:
            cands, n, state, season = suit.score(data, body.state, body.season, body.soil_ph,
                                                 body.irrigated, body.top_n)
        except suit.UnknownRegionError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        return SuitabilityResponse(
            region=state,
            season=season,
            score_type="weighted evidence index in [0,1]; not a probability, not calibrated",
            candidates=cands,
            crops_considered=n,
            limitations=suit.LIMITATIONS,
            provenance=Provenance(
                method=suit.METHOD,
                method_version=suit.METHOD_VERSION,
                data_sources=["Kaggle 'Agricultural Crop Yield in Indian States' (akshatgupta7), "
                              "CC BY-SA 4.0, state x crop x season 1997-2020",
                              "indicative crop pH / water-need reference table (MVP, cf. FAO "
                              "ECOCROP)"],
                dataset_version=data.dataset_version,
                data_classification=DataClassification.ESTIMATED,
                generated_at=datetime.now(UTC),
            ),
        )

    @app.post(
        "/v1/predict/demand",
        response_model=DemandResponse,
        response_model_by_alias=True,
        tags=["predict"],
    )
    def predict_demand(
        body: DemandRequest, settings: Annotated[Settings, Depends(get_settings)]
    ) -> DemandResponse:
        try:
            data = demand_model.load_data(settings.reference_data_dir)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        try:
            result = demand_model.forecast(data, body.crop, body.target_year, body.state)
        except demand_model.UnsupportedCropError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        return DemandResponse(
            **result,
            provenance=Provenance(
                method="statistical baseline (naive vs. linear trend, chosen by backtest)",
                method_version=demand_model.METHOD_VERSION,
                data_sources=[demand_model.FAOSTAT_SOURCE]
                + ([demand_model.CENSUS_SOURCE] if body.state else []),
                data_classification=DataClassification(result["data_classification"]),
                generated_at=datetime.now(UTC),
            ),
        )

    @app.post(
        "/v1/predict/anomaly",
        response_model=AnomalyResponse,
        response_model_by_alias=True,
        tags=["predict"],
    )
    def predict_anomaly(body: AnomalyRequest) -> AnomalyResponse:
        result = anomaly_engine.evaluate([p.value for p in body.series], body.window,
                                         body.threshold)
        return AnomalyResponse(
            metric=body.metric,
            unit=body.unit,
            period=body.series[-1].period,
            **result,
            provenance=Provenance(
                method="robust z-score (median/MAD)",
                method_version=anomaly_engine.METHOD_VERSION,
                data_sources=["series supplied by the caller"],
                data_classification=DataClassification.ESTIMATED,
                generated_at=datetime.now(UTC),
            ),
        )

    return app


app = create_app()
