"""Train, evaluate and save the supply (district production) model, logging to MLflow.

    python scripts/download_s01_crop_production.py        # once, needs DATA_GOV_IN_API_KEY
    python scripts/train_supply.py --model-version supply-xgb-v1

Reads data/raw/s01_crop_production/ (must be a complete registered-key download) and writes
artifacts/supply/<model-version>/: model.json, metadata.json, series.parquet, scored.csv,
error_analysis.json, evaluation_report.md. Stages: agri_ml.training.pipeline.
"""

import argparse
import json
import sys

import mlflow

from agri_ml.config import get_settings
from agri_ml.training.pipeline import run_supply_pipeline

EXPERIMENT_NAME = "supply-forecasting"


def log_to_mlflow(settings, metadata: dict) -> str:
    mlflow.set_tracking_uri(settings.mlflow_tracking_uri)
    if mlflow.get_experiment_by_name(EXPERIMENT_NAME) is None:
        mlflow.create_experiment(EXPERIMENT_NAME, artifact_location=settings.mlflow_artifact_root)
    mlflow.set_experiment(EXPERIMENT_NAME)
    with mlflow.start_run(run_name=metadata["modelVersion"]) as run:
        mlflow.set_tags({
            "modelVersion": metadata["modelVersion"],
            "datasetVersion": metadata["datasetVersion"],
            "featureVersion": metadata["featureVersion"],
            "servedMethod": metadata["servedMethod"],
        })
        mlflow.log_params({
            **metadata["hyperparameters"],
            "seed": metadata["seed"],
            "trainingPeriod": metadata["trainingPeriod"],
            "validationPeriod": metadata["validationPeriod"],
            "testPeriod": metadata["testPeriod"],
        })
        for part, by_method in metadata["metrics"].items():
            for method, values in by_method.items():
                for name, value in values.items():
                    mlflow.log_metric(f"{part}.{method}.{name}", value)
        mlflow.log_metric("test.interval_coverage", metadata["interval"]["testEmpiricalCoverage"])
        return run.info.run_id


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-version", default="supply-xgb-v1")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    settings = get_settings()
    raw_dir = settings.data_dir / "raw" / "s01_crop_production"
    try:
        result = run_supply_pipeline(raw_dir, settings.artifacts_dir, args.model_version, args.seed)
    except (FileNotFoundError, FileExistsError, ValueError) as exc:
        sys.exit(f"training aborted: {exc}")

    run_id = log_to_mlflow(settings, result.metadata)
    meta = result.metadata
    print(json.dumps({"cleaning": meta["cleaningReport"], "scope": meta["scopeReport"],
                      "rows": meta["rows"], "selection": meta["selection"],
                      "interval": meta["interval"]}, indent=2))
    print(f"saved {result.artifact_dir}\nmlflow run {run_id}")


if __name__ == "__main__":
    main()
