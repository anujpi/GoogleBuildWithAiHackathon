"""Train, evaluate and save the Potato/Tomato leaf-disease classifier, logging to MLflow.

    python scripts/train_disease.py --model-version disease-mnv3-v1

Needs the split written by scripts/prepare_plantvillage.py (docs/datasets/plantvillage.md).
Writes artifacts/disease/<model-version>/:
  model.pt               state dict (MobileNetV3-Large, 13 outputs)
  metadata.json          class mapping, preprocessing, versions, provenance, metrics
  error_analysis.json    most confident test errors, confusion pairs
  test_predictions.csv   per-image test probabilities of the true and predicted class
An existing model version is never overwritten.
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
from datetime import UTC, datetime

import mlflow
import numpy as np
import pandas as pd
import PIL
import torch
import torchvision

from agri_ml.config import get_settings
from agri_ml.datasets import plantvillage as pv
from agri_ml.inference.disease import ARCHITECTURE, PREPROCESSING
from agri_ml.training import disease as td

EXPERIMENT_NAME = "disease-classification"
FEATURE_VERSION = "disease-prep-v1"  # PREPROCESSING in agri_ml.inference.disease


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-version", default="disease-mnv3-v1")
    parser.add_argument("--epochs", type=int, default=td.TrainConfig.epochs)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="auto")
    args = parser.parse_args()

    settings = get_settings()
    root = pv.raw_dir()
    version = pv.dataset_version(root)
    split_dir = settings.data_dir / "processed" / pv.DATASET_NAME / version / pv.SPLIT_VERSION
    out_dir = settings.artifacts_dir / "disease" / args.model_version
    if out_dir.exists():
        print(f"{out_dir} already exists; choose a new --model-version.", file=sys.stderr)
        return 1
    split = pd.read_csv(split_dir / "split.csv", dtype={"dhash": str})
    split_manifest = json.loads((split_dir / "manifest.json").read_text())

    # Deterministic class mapping: sorted publisher labels -> 0..n-1.
    labels = sorted(split["label"].unique())
    classes = [{"index": i, **vars(pv.ClassName.parse(lab))} for i, lab in enumerate(labels)]
    split["target"] = split["label"].map({lab: i for i, lab in enumerate(labels)})

    print("baselines ...")
    base = td.baselines(root, split, labels, args.seed)
    print(json.dumps({s: {k: {m: v[m] for m in ("accuracy", "macroF1")} for k, v in b.items()}
                      for s, b in base.items()}, indent=1))

    cfg = td.TrainConfig(epochs=args.epochs, seed=args.seed, device=args.device)
    result = td.train(root, split, len(labels), cfg)

    y = {s: split.loc[split.split == s, "target"].to_numpy() for s in ("val", "test")}
    metrics = {"val": td.classification_metrics(y["val"], result.val_probs, labels),
               "test": td.classification_metrics(y["test"], result.test_probs, labels)}

    test = split[split.split == "test"].reset_index(drop=True)
    probs = result.test_probs
    pred = probs.argmax(1)
    test_pred = test[["path", "label", "leaf_id", "group"]].assign(
        predicted=[labels[i] for i in pred],
        probPredicted=probs.max(1),
        probTrue=probs[np.arange(len(test)), test["target"].to_numpy()],
    )
    errors = test_pred[test_pred.label != test_pred.predicted]
    pairs = errors.groupby(["label", "predicted"]).size().sort_values(ascending=False)
    analysis = {
        "testErrors": len(errors),
        "confusionPairs": [{"true": t, "predicted": p, "n": int(n)} for (t, p), n in pairs.items()],
        "mostConfidentErrors": errors.sort_values("probPredicted", ascending=False)
                                     .head(25).to_dict("records"),
        "cropConfusions": int((errors.label.str.split("___").str[0]
                               != errors.predicted.str.split("___").str[0]).sum()),
    }

    trained_at = datetime.now(UTC).isoformat(timespec="seconds")
    metadata = {
        "modelName": td.MODEL_NAME,
        "modelVersion": args.model_version,
        "modelType": f"torchvision {ARCHITECTURE}, ImageNet (IMAGENET1K_V2) pretrained, "
                     "all layers fine-tuned, 13-way softmax head",
        "architecture": ARCHITECTURE,
        "featureVersion": FEATURE_VERSION,
        "datasetVersion": version,
        "splitVersion": pv.SPLIT_VERSION,
        "trainedAt": trained_at,
        "classes": classes,
        "preprocessing": PREPROCESSING,
        "trainAugmentation": "RandomResizedCrop(224, scale 0.7-1.0), horizontal + vertical flip",
        "hyperparameters": {"epochs": cfg.epochs, "batchSize": cfg.batch_size, "lr": cfg.lr,
                            "weightDecay": cfg.weight_decay, "optimizer": "AdamW",
                            "schedule": "OneCycleLR pct_start=0.15", "loss": "cross-entropy",
                            "selection": "best validation macro-F1 epoch"},
        "seed": cfg.seed,
        "device": result.device,
        "bestEpoch": result.best_epoch,
        "history": result.history,
        "splitCounts": split_manifest["counts"],
        "splitTotals": split_manifest["totals"],
        "trainingData": {
            "source": f"Hugging Face {pv.HF_REPO} (PlantVillage, Mohanty et al. 2016)",
            "url": pv.SOURCE_URL,
            "revision": pv.HF_REVISION,
            "config": pv.CONFIG,
            "license": pv.LICENSE,
            "attribution": pv.CITATION,
            "dataClassification": "OBSERVED (leaf photographs, mostly controlled background; "
                                  "labels from the publisher)",
            "scope": "Potato and Tomato classes only",
            "catalogue": "docs/datasets/plantvillage.md",
        },
        "metrics": metrics,
        "baselines": base,
        "lowConfidenceThreshold": None,
        "lowConfidenceNote": "No threshold is applied by the ML service. Validation data is "
                             "in-distribution lab imagery, so a threshold tuned on it does not "
                             "transfer to field photos; see docs/model-cards/disease.md.",
        "environment": {"python": platform.python_version(), "torch": torch.__version__,
                        "torchvision": torchvision.__version__, "pillow": PIL.__version__},
    }

    out_dir.mkdir(parents=True)
    torch.save(result.network.state_dict(), out_dir / "model.pt")
    (out_dir / "error_analysis.json").write_text(json.dumps(analysis, indent=2, default=str))
    test_pred.to_csv(out_dir / "test_predictions.csv", index=False)

    mlflow.set_tracking_uri(settings.mlflow_tracking_uri)
    if mlflow.get_experiment_by_name(EXPERIMENT_NAME) is None:
        mlflow.create_experiment(EXPERIMENT_NAME, artifact_location=settings.mlflow_artifact_root)
    mlflow.set_experiment(EXPERIMENT_NAME)
    with mlflow.start_run(run_name=args.model_version) as run:
        mlflow.set_tags({"modelVersion": args.model_version, "modelType": ARCHITECTURE,
                         "datasetVersion": version, "featureVersion": FEATURE_VERSION,
                         "splitVersion": pv.SPLIT_VERSION})
        mlflow.log_params({**metadata["hyperparameters"], "seed": cfg.seed,
                           "device": result.device, "numClasses": len(labels)})
        for h in result.history:
            mlflow.log_metrics({k: v for k, v in h.items() if k != "epoch"}, step=h["epoch"])
        for part in ("val", "test"):
            for k in ("accuracy", "macroF1", "weightedF1", "expectedCalibrationError"):
                mlflow.log_metric(f"{part}_{k}", metrics[part][k])
        metadata["mlflowRunId"] = run.info.run_id
        (out_dir / "metadata.json").write_text(json.dumps(metadata, indent=2))
        mlflow.log_artifacts(str(out_dir), artifact_path="model_artifact")

    t = metrics["test"]
    print(f"saved {out_dir}  (MLflow run {metadata['mlflowRunId']})")
    print(f"TEST n={t['n']} accuracy={t['accuracy']:.4f} macroF1={t['macroF1']:.4f} "
          f"ECE={t['expectedCalibrationError']:.4f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
