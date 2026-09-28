"""Train, evaluate and save the state-level supply (production) model, logging to MLflow.

    python scripts/train_supply.py --model-version supply-xgb-v1

Training data: the Kaggle crop_yield.csv at data/raw/crop_yield.csv (docs/datasets/crop_yield.md).
Writes artifacts/supply/<model-version>/ (model.json, metadata.json, error_analysis.json,
scored.csv). An existing model version is never overwritten.
"""

import argparse
import json

import mlflow

from agri_ml.config import get_settings
from agri_ml.datasets import crop_yield as cy
from agri_ml.training.supply import Split, error_analysis, save_artifact, train_and_evaluate

EXPERIMENT_NAME = "supply-forecasting"
# 2020 covers one state only (flag_incomplete_year), so the last usable year is 2019.
SPLIT = Split(train_end=2013, val_start=2014, val_end=2016, test_start=2017, test_end=2019)
TRAINING_DATA = {
    "source": "Kaggle: Agricultural Crop Yield in Indian States Dataset (akshatgupta7)",
    "url": "https://www.kaggle.com/datasets/akshatgupta7/crop-yield-in-indian-states-dataset",
    "license": "CC-BY-SA-4.0",
    "dataClassification": "OBSERVED (official statistics republished by a third party)",
    "spatialGranularity": "state",
    "catalogue": "docs/datasets/crop_yield.md",
    "units": "Area hectares and Production metric tons as stated by the publisher; "
             "not verified against the upstream government source",
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-version", default="supply-xgb-v1")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    settings = get_settings()
    path = cy.default_path()
    cleaned = cy.load(path)
    report = cy.validate(cleaned)
    if report.problems:
        raise SystemExit(f"crop_yield validation failed: {report.problems}")
    supply, clean_report = cy.to_supply_frame(cleaned)
    if supply["crop_year"].max() > SPLIT.test_end:
        raise SystemExit(f"supply data reaches {supply['crop_year'].max()}, past the test period")

    version = cy.dataset_version(path)
    result = train_and_evaluate(supply, SPLIT, dataset_version=version, seed=args.seed)
    analysis = error_analysis(result.scored)

    out_dir = settings.artifacts_dir / "supply" / args.model_version
    save_artifact(result, out_dir, args.model_version,
                  extra={"trainingData": {**TRAINING_DATA, "preprocessingVersion":
                                          cy.PREPROCESSING_VERSION},
                         "cleaningReport": clean_report,
                         "excludedCrops": sorted(cy.SUPPLY_EXCLUDED_CROPS),
                         "scope": {"states": sorted(supply["state_name"].unique()),
                                   "crops": sorted(supply["crop"].unique())}})
    (out_dir / "error_analysis.json").write_text(json.dumps(analysis, indent=2, default=str))
    result.scored.to_csv(out_dir / "scored.csv", index=False)

    mlflow.set_tracking_uri(settings.mlflow_tracking_uri)
    if mlflow.get_experiment_by_name(EXPERIMENT_NAME) is None:
        mlflow.create_experiment(EXPERIMENT_NAME, artifact_location=settings.mlflow_artifact_root)
    mlflow.set_experiment(EXPERIMENT_NAME)
    meta = result.metadata
    with mlflow.start_run(run_name=args.model_version) as run:
        mlflow.set_tags({"modelVersion": args.model_version, "modelType": "xgboost",
                         "datasetVersion": version, "featureVersion": meta["featureVersion"]})
        mlflow.log_params({**{k: v for k, v in meta["hyperparameters"].items()},
                           "features": ",".join(meta["features"]["numeric"]
                                                + meta["features"]["categorical"]),
                           "trainingPeriod": meta["trainingPeriod"],
                           "validationPeriod": meta["validationPeriod"],
                           "testPeriod": meta["testPeriod"]})
        for part, by_method in meta["metrics"].items():
            for method, values in by_method.items():
                for name, value in values.items():
                    mlflow.log_metric(f"{part}.{method}.{name}", value)
        mlflow.log_metric("test.interval_coverage", meta["interval"]["testEmpiricalCoverage"])
        mlflow.log_artifacts(str(out_dir), artifact_path="model_artifact")

    print(json.dumps({"cleaning": clean_report, "rows": meta["rows"],
                      "metrics": meta["metrics"], "interval": meta["interval"],
                      "hyperparameters": meta["hyperparameters"]}, indent=2))
    print(f"saved {out_dir}\nmlflow run {run.info.run_id}")


if __name__ == "__main__":
    main()
