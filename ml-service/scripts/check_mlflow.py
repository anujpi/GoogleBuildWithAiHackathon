"""Verify MLflow tracking works: create an experiment and log one empty run.

This is an environment check only. It logs no data, no metrics and no model.

    python scripts/check_mlflow.py
"""

import platform

import mlflow

from agri_ml.config import get_settings

EXPERIMENT_NAME = "foundation-smoke-check"


def main() -> None:
    settings = get_settings()
    mlflow.set_tracking_uri(settings.mlflow_tracking_uri)
    if mlflow.get_experiment_by_name(EXPERIMENT_NAME) is None:
        mlflow.create_experiment(EXPERIMENT_NAME, artifact_location=settings.mlflow_artifact_root)
    mlflow.set_experiment(EXPERIMENT_NAME)

    with mlflow.start_run(run_name="environment-check") as run:
        mlflow.set_tags({"purpose": "environment-check", "python": platform.python_version()})

    print(f"tracking uri : {settings.mlflow_tracking_uri}")
    print(f"experiment   : {EXPERIMENT_NAME}")
    print(f"run id       : {run.info.run_id}")


if __name__ == "__main__":
    main()
