"""Train, evaluate and save the supply (district production) model, logging to MLflow.

    python scripts/download_s01_crop_production.py   # once
    python scripts/train_supply.py --model-version supply-xgb-v1

Reads data/raw/s01_crop_production/, writes data/processed/s01_clean.csv and
artifacts/supply/<model-version>/ (model.json, metadata.json, error_analysis.json, scored.csv).
"""

import argparse
import hashlib
import json

import mlflow

from agri_ml.config import get_settings
from agri_ml.datasets.crop_production import clean, load_raw
from agri_ml.training.supply import Split, error_analysis, save_artifact, train_and_evaluate

EXPERIMENT_NAME = "supply-forecasting"
# S01 is complete up to 2014; 2015 has only ~2% of a normal year's rows (partial), so it is excluded.
LAST_COMPLETE_YEAR = 2014
SPLIT = Split(train_end=2010, val_start=2011, val_end=2012, test_start=2013, test_end=2014)


def dataset_version(raw_dir) -> str:
    digest = hashlib.sha256()
    for path in sorted(raw_dir.glob("*.csv")):
        digest.update(path.name.encode())
        digest.update(path.read_bytes())
    return f"s01-{digest.hexdigest()[:12]}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-version", default="supply-xgb-v1")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    settings = get_settings()
    raw_dir = settings.data_dir / "raw" / "s01_crop_production"
    raw = load_raw(raw_dir)
    cleaned, clean_report = clean(raw)
    cleaned = cleaned[cleaned["crop_year"] <= LAST_COMPLETE_YEAR]
    clean_report["rows_after_year_filter"] = len(cleaned)
    processed = settings.data_dir / "processed"
    processed.mkdir(parents=True, exist_ok=True)
    cleaned.to_csv(processed / "s01_clean.csv", index=False)

    version = dataset_version(raw_dir)
    manifest = json.loads((raw_dir / "manifest.json").read_text())
    result = train_and_evaluate(cleaned, SPLIT, dataset_version=version, seed=args.seed)
    analysis = error_analysis(result.scored)

    out_dir = settings.artifacts_dir / "supply" / args.model_version
    save_artifact(result, out_dir, args.model_version,
                  extra={"cleaningReport": clean_report, "sourceManifest": manifest,
                         "scope": {"states": sorted(cleaned["state_name"].unique()),
                                   "crops": sorted(cleaned["crop"].unique())}})
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
