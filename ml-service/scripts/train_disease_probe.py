"""CPU fallback: linear-probe transfer learning for the Potato/Tomato leaf-disease classifier.

    python scripts/train_disease_probe.py --model-version disease-mnv3-probe-v1

Why this exists: full fine-tuning (scripts/train_disease.py) on Apple MPS stalled repeatedly on the
8 GB development machine under memory pressure. This script needs far less memory and no GPU:

  1. Frozen torchvision MobileNetV3-Large (ImageNet IMAGENET1K_V2) turns each image into the
     1280-d vector that feeds its final classifier layer (eval preprocessing only, no augmentation).
  2. A multinomial logistic regression is fitted on train features. C is chosen on validation
     macro-F1; test is scored once, afterwards.
  3. The fitted weights are copied into classifier[-1] of the same architecture, so the saved
     model.pt loads with agri_ml.inference.disease.DiseaseModel unchanged, and its softmax equals
     the logistic regression's predict_proba.

It writes the same artifact layout and metadata as train_disease.py. It also uses the same split and
baselines. An existing model version is never overwritten.
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
import time
from datetime import UTC, datetime

import mlflow
import numpy as np
import pandas as pd
import PIL
import torch
import torchvision
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score
from torch.utils.data import DataLoader

from agri_ml.config import get_settings
from agri_ml.datasets import plantvillage as pv
from agri_ml.inference.disease import ARCHITECTURE, PREPROCESSING, build_network, eval_transform
from agri_ml.training import disease as td

EXPERIMENT_NAME = "disease-classification"
FEATURE_VERSION = "disease-prep-v1"
C_GRID = [0.1, 0.3, 1.0, 3.0]


@torch.inference_mode()
def features(net: torch.nn.Module, loader: DataLoader) -> np.ndarray:
    # Everything up to (not including) the last Linear: avgpool -> Linear(960,1280) -> Hardswish.
    head = torch.nn.Sequential(*list(net.classifier.children())[:-1]).eval()
    out = []
    for i, (x, _) in enumerate(loader):
        f = net.avgpool(net.features(x)).flatten(1)
        out.append(head(f).numpy())
        if i % 20 == 0:
            print(f"  batch {i}/{len(loader)}", flush=True)
    return np.concatenate(out)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-version", default="disease-mnv3-probe-v1")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--threads", type=int, default=6)
    parser.add_argument("--limit", type=int, default=0,
                        help="smoke test only: keep N images per split (no feature cache)")
    args = parser.parse_args()
    torch.set_num_threads(args.threads)
    td.seed_everything(args.seed)

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
    if args.limit:
        split = split.groupby("split").sample(n=args.limit, random_state=args.seed)
    labels = sorted(split["label"].unique())
    classes = [{"index": i, **vars(pv.ClassName.parse(lab))} for i, lab in enumerate(labels)]
    split["target"] = split["label"].map({lab: i for i, lab in enumerate(labels)})

    print("baselines ...", flush=True)
    base = td.baselines(root, split, labels, args.seed)

    net = build_network(len(labels), pretrained=True).eval()
    # Frozen-backbone features are deterministic for a given split and preprocessing, so each
    # split's features are cached next to the split (git-ignored data/) once computed.
    cache_stem = f"features-{ARCHITECTURE}-imagenet1k_v2-{FEATURE_VERSION}"
    ys = {s: split.loc[split.split == s, "target"].to_numpy() for s in ("train", "val", "test")}
    t0 = time.time()
    feats = {}
    for s in ("train", "val", "test"):
        cache = split_dir / f"{cache_stem}-{s}.npy"
        if cache.is_file() and not args.limit:
            print(f"using cached features {cache.name}", flush=True)
            feats[s] = np.load(cache)
            continue
        part = split[split.split == s]
        ds = td.LeafDataset(root, part["path"].tolist(), part["target"].tolist(), eval_transform())
        loader = DataLoader(ds, batch_size=64, shuffle=False, num_workers=2)
        print(f"features {s} ({len(part)} images) ...", flush=True)
        feats[s] = features(net, loader)
        if not args.limit:
            np.save(cache, feats[s])
    feature_seconds = round(time.time() - t0, 1)

    history, best = [], None
    for c in C_GRID:
        clf = LogisticRegression(C=c, max_iter=3000, random_state=args.seed)
        clf.fit(feats["train"], ys["train"])
        f1 = f1_score(ys["val"], clf.predict(feats["val"]), average="macro")
        history.append({"C": c, "valMacroF1": float(f1)})
        print(history[-1], flush=True)
        if best is None or f1 > best[0]:
            best = (f1, c, clf)
    _, best_c, clf = best

    metrics = {s: td.classification_metrics(ys[s], clf.predict_proba(feats[s]), labels)
               for s in ("val", "test")}

    # Copy the probe into the network's last layer: softmax(W x + b) == clf.predict_proba(x).
    last = net.classifier[-1]
    with torch.no_grad():
        last.weight.copy_(torch.tensor(clf.coef_, dtype=torch.float32))
        last.bias.copy_(torch.tensor(clf.intercept_, dtype=torch.float32))
    with torch.no_grad():
        check = torch.softmax(last(torch.tensor(feats["test"][:64])), 1).numpy()
    assert np.allclose(check, clf.predict_proba(feats["test"][:64]), atol=1e-4)

    test = split[split.split == "test"].reset_index(drop=True)
    probs = clf.predict_proba(feats["test"])
    pred = probs.argmax(1)
    test_pred = test[["path", "label", "leaf_id", "group"]].assign(
        predicted=[labels[i] for i in pred], probPredicted=probs.max(1),
        probTrue=probs[np.arange(len(test)), test["target"].to_numpy()])
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

    metadata = {
        "modelName": td.MODEL_NAME,
        "modelVersion": args.model_version,
        "modelType": f"torchvision {ARCHITECTURE}, ImageNet (IMAGENET1K_V2) backbone frozen; "
                     "linear probe (multinomial logistic regression) as the final layer",
        "architecture": ARCHITECTURE,
        "featureVersion": FEATURE_VERSION,
        "datasetVersion": version,
        "splitVersion": pv.SPLIT_VERSION,
        "trainedAt": datetime.now(UTC).isoformat(timespec="seconds"),
        "classes": classes,
        "preprocessing": PREPROCESSING,
        "trainAugmentation": "none (features from eval preprocessing)",
        "hyperparameters": {"method": "linear probe", "C": best_c, "cGrid": C_GRID,
                            "selection": "best validation macro-F1", "solver": "lbfgs"},
        "seed": args.seed,
        "device": "cpu",
        "history": history,
        "featureExtractionSeconds": feature_seconds,
        "splitCounts": split_manifest["counts"],
        "splitTotals": split_manifest["totals"],
        "trainingData": {
            "source": f"Hugging Face {pv.HF_REPO} (PlantVillage, Mohanty et al. 2016)",
            "url": pv.SOURCE_URL, "revision": pv.HF_REVISION, "config": pv.CONFIG,
            "license": pv.LICENSE, "attribution": pv.CITATION,
            "dataClassification": "OBSERVED (leaf photographs, mostly controlled background; "
                                  "labels from the publisher)",
            "scope": "Potato and Tomato classes only",
            "catalogue": "docs/datasets/plantvillage.md",
        },
        "metrics": metrics,
        "baselines": base,
        "lowConfidenceThreshold": None,
        "lowConfidenceNote": "No threshold is applied by the ML service; "
                             "see docs/model-cards/disease.md.",
        "environment": {"python": platform.python_version(), "torch": torch.__version__,
                        "torchvision": torchvision.__version__, "pillow": PIL.__version__},
    }

    out_dir.mkdir(parents=True)
    torch.save(net.state_dict(), out_dir / "model.pt")
    (out_dir / "error_analysis.json").write_text(json.dumps(analysis, indent=2, default=str))
    test_pred.to_csv(out_dir / "test_predictions.csv", index=False)
    mlflow.set_tracking_uri(settings.mlflow_tracking_uri)
    if mlflow.get_experiment_by_name(EXPERIMENT_NAME) is None:
        mlflow.create_experiment(EXPERIMENT_NAME, artifact_location=settings.mlflow_artifact_root)
    mlflow.set_experiment(EXPERIMENT_NAME)
    with mlflow.start_run(run_name=args.model_version) as run:
        mlflow.set_tags({"modelVersion": args.model_version, "modelType": "linear-probe",
                         "datasetVersion": version, "featureVersion": FEATURE_VERSION})
        mlflow.log_params({"C": best_c, "seed": args.seed})
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
