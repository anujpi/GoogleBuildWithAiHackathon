"""Train and evaluate the leaf-disease classifier (transfer learning) and its baselines.

Baselines (CLAUDE.md baseline-first rule):
  - majority class
  - multinomial logistic regression on a 3x16-bin RGB colour histogram. It is also a probe for
    the colour/background shortcuts PlantVillage is known for.
Model: torchvision MobileNetV3-Large pretrained on ImageNet, all layers fine-tuned. The epoch
with the best validation macro-F1 is kept. Test data is only scored once, after selection.
"""

from __future__ import annotations

import random
import time
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from PIL import Image
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_recall_fscore_support,
)
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from torch import nn
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms

from agri_ml.inference.disease import PREPROCESSING, build_network, eval_transform

MODEL_NAME = "leaf-disease-classifier"
# Probability thresholds at which validation/test accuracy vs. coverage is reported.
REPORT_THRESHOLDS = [0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99]


@dataclass
class TrainConfig:
    epochs: int = 6
    batch_size: int = 64
    lr: float = 5e-4
    weight_decay: float = 1e-4
    num_workers: int = 4
    seed: int = 42
    device: str = "auto"
    max_epoch_seconds: float = 1800.0  # abort if one epoch is slower than this (CPU guard)


@dataclass
class TrainResult:
    network: nn.Module
    history: list[dict]
    best_epoch: int
    device: str
    val_probs: np.ndarray
    test_probs: np.ndarray
    extra: dict = field(default_factory=dict)


def seed_everything(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)


def pick_device(name: str) -> torch.device:
    if name != "auto":
        return torch.device(name)
    if torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def train_transform(p: dict = PREPROCESSING) -> transforms.Compose:
    # Mild, label-preserving augmentation only; no colour jitter so disease colour cues survive.
    return transforms.Compose([
        transforms.RandomResizedCrop(p["centerCrop"], scale=(0.7, 1.0)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomVerticalFlip(),
        transforms.ToTensor(),
        transforms.Normalize(p["mean"], p["std"]),
    ])


class LeafDataset(Dataset):
    def __init__(self, root: Path, paths: list[str], targets: list[int], transform):
        self.root, self.paths, self.targets, self.transform = root, paths, targets, transform

    def __len__(self) -> int:
        return len(self.paths)

    def __getitem__(self, i: int):
        with Image.open(self.root / self.paths[i]) as im:
            x = self.transform(im.convert("RGB"))
        return x, self.targets[i]


def _seed_worker(worker_id: int) -> None:
    s = torch.initial_seed() % 2**32
    np.random.seed(s)
    random.seed(s)


def _loader(ds: Dataset, cfg: TrainConfig, shuffle: bool) -> DataLoader:
    g = torch.Generator()
    g.manual_seed(cfg.seed)
    return DataLoader(ds, batch_size=cfg.batch_size, shuffle=shuffle, generator=g,
                      num_workers=cfg.num_workers, worker_init_fn=_seed_worker,
                      persistent_workers=cfg.num_workers > 0)


@torch.inference_mode()
def predict_probs(net: nn.Module, loader: DataLoader, device: torch.device) -> np.ndarray:
    net.eval()
    out = [torch.softmax(net(x.to(device)), dim=1).cpu() for x, _ in loader]
    return torch.cat(out).numpy()


def classification_metrics(y: np.ndarray, probs: np.ndarray, labels: list[str]) -> dict:
    pred = probs.argmax(1)
    idx = list(range(len(labels)))
    p, r, f, s = precision_recall_fscore_support(y, pred, labels=idx, zero_division=0)
    conf = probs.max(1)
    correct = pred == y
    # Expected calibration error, 15 equal-width confidence bins.
    bins = np.linspace(0, 1, 16)
    ece = 0.0
    for lo, hi in zip(bins[:-1], bins[1:], strict=True):
        m = (conf > lo) & (conf <= hi)
        if m.any():
            ece += m.mean() * abs(correct[m].mean() - conf[m].mean())
    coverage = []
    for t in REPORT_THRESHOLDS:
        m = conf >= t
        coverage.append({"threshold": t, "coverage": float(m.mean()),
                         "accuracyOnCovered": float(correct[m].mean()) if m.any() else None,
                         "n": int(m.sum())})
    return {
        "n": int(len(y)),
        "accuracy": float(accuracy_score(y, pred)),
        "macroF1": float(f1_score(y, pred, average="macro", labels=idx, zero_division=0)),
        "weightedF1": float(f1_score(y, pred, average="weighted", labels=idx, zero_division=0)),
        "expectedCalibrationError": float(ece),
        "perClass": [{"label": lab, "precision": float(p[i]), "recall": float(r[i]),
                      "f1": float(f[i]), "support": int(s[i])} for i, lab in enumerate(labels)],
        "confusionMatrix": confusion_matrix(y, pred, labels=idx).tolist(),
        "confidenceCoverage": coverage,
    }


def colour_histogram(path: Path, bins: int = 16) -> np.ndarray:
    with Image.open(path) as im:
        a = np.asarray(im.convert("RGB").resize((128, 128)))
    return np.concatenate([np.histogram(a[..., c], bins=bins, range=(0, 256))[0]
                           for c in range(3)]).astype(np.float32) / (128 * 128)


def baselines(root: Path, split: pd.DataFrame, labels: list[str], seed: int) -> dict:
    """Majority class and colour-histogram logistic regression, scored on val and test."""
    feats = {s: np.stack([colour_histogram(root / p) for p in split.loc[split.split == s, "path"]])
             for s in ("train", "val", "test")}
    ys = {s: split.loc[split.split == s, "target"].to_numpy() for s in ("train", "val", "test")}
    majority = np.bincount(ys["train"], minlength=len(labels)).argmax()
    clf = make_pipeline(StandardScaler(), LogisticRegression(max_iter=2000, random_state=seed))
    clf.fit(feats["train"], ys["train"])
    out = {}
    for s in ("val", "test"):
        onehot = np.zeros((len(ys[s]), len(labels)))
        onehot[:, majority] = 1.0
        maj = classification_metrics(ys[s], onehot, labels)
        hist = classification_metrics(ys[s], clf.predict_proba(feats[s]), labels)
        out[s] = {
            "majorityClass": {"label": labels[majority], "accuracy": maj["accuracy"],
                              "macroF1": maj["macroF1"]},
            "colourHistogramLogReg": {k: hist[k] for k in ("accuracy", "macroF1", "perClass")},
        }
    return out


def train(root: Path, split: pd.DataFrame, n_classes: int, cfg: TrainConfig,
          log=print) -> TrainResult:
    seed_everything(cfg.seed)
    device = pick_device(cfg.device)
    parts = {s: split[split.split == s] for s in ("train", "val", "test")}

    def ds(s, tf):
        return LeafDataset(root, parts[s]["path"].tolist(), parts[s]["target"].tolist(), tf)

    train_dl = _loader(ds("train", train_transform()), cfg, shuffle=True)
    val_dl = _loader(ds("val", eval_transform()), cfg, shuffle=False)
    test_dl = _loader(ds("test", eval_transform()), cfg, shuffle=False)

    net = build_network(n_classes, pretrained=True).to(device)
    opt = torch.optim.AdamW(net.parameters(), lr=cfg.lr, weight_decay=cfg.weight_decay)
    steps = cfg.epochs * len(train_dl)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=cfg.lr, total_steps=steps,
                                                pct_start=0.15)
    loss_fn = nn.CrossEntropyLoss()
    y_val = parts["val"]["target"].to_numpy()

    history, best_f1, best_state, best_epoch = [], -1.0, None, -1
    for epoch in range(1, cfg.epochs + 1):
        t0 = time.time()
        net.train()
        total, n = 0.0, 0
        for x, y in train_dl:
            x, y = x.to(device), y.to(device)
            opt.zero_grad(set_to_none=True)
            loss = loss_fn(net(x), y)
            loss.backward()
            opt.step()
            sched.step()
            total += loss.item() * len(y)
            n += len(y)
        val_probs = predict_probs(net, val_dl, device)
        vm = classification_metrics(y_val, val_probs, [str(i) for i in range(n_classes)])
        secs = time.time() - t0
        row = {"epoch": epoch, "trainLoss": total / n, "valAccuracy": vm["accuracy"],
               "valMacroF1": vm["macroF1"], "seconds": round(secs, 1)}
        history.append(row)
        log(row)
        if vm["macroF1"] > best_f1:
            best_f1, best_epoch = vm["macroF1"], epoch
            best_state = {k: v.detach().cpu().clone() for k, v in net.state_dict().items()}
        if secs > cfg.max_epoch_seconds:
            raise RuntimeError(f"epoch {epoch} took {secs:.0f}s on {device}, over the "
                               f"{cfg.max_epoch_seconds:.0f}s limit; stopping")

    net.load_state_dict(best_state)
    val_probs = predict_probs(net, val_dl, device)
    test_probs = predict_probs(net, test_dl, device)
    return TrainResult(net.cpu().eval(), history, best_epoch, str(device), val_probs, test_probs)
