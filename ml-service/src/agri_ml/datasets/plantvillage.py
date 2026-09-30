"""PlantVillage (Hugging Face `mohanty/PlantVillage`) leaf images for the disease MVP.

Catalogue entry: docs/datasets/plantvillage.md.

These are OBSERVED photographs of single leaves, taken mostly against a plain, controlled
background. They are not field imagery. Labels are the publisher's class folders.

Scope for the MVP: the `color` configuration, Potato and Tomato classes only (13 classes).

Split design (leakage control), SPLIT_VERSION pv-split-v2:
  The publisher's own train/test split is NOT used. Its leaf map misses many repeat photos of
  the same leaf (e.g. "GH_HL Leaf 211.JPG" in train and "GH_HL Leaf 211.1.JPG" in test), and
  it has no grouping at all for Tomato Target_Spot and Tomato_mosaic_virus. Instead, images are
  merged into groups (union-find) when, within the same class, they share
    1. the publisher's `leaf_id`,
    2. a filename stem (filename after the uuid prefix, without extension or a ".N" suffix), or
    3. a 64-bit dHash within NEAR_DUP_BITS bits (identical or near-identical photos).
  Every group is assigned whole to one split: per class, shuffled with a fixed seed, groups
  fill test to TEST_FRACTION and validation to VAL_FRACTION of the class's images; the rest is
  train. This avoids leakage where practical; repeat photos with no shared id, stem or close
  hash can still cross splits (see docs/datasets/plantvillage.md).
Nothing is silently dropped.
"""

from __future__ import annotations

import hashlib
import json
import re
import zipfile
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd
from PIL import Image

from agri_ml.config.settings import get_settings
from agri_ml.schemas.common import DataClassification

DATASET_NAME = "plantvillage"
HF_REPO = "mohanty/PlantVillage"
# Pinned repository revision; files are downloaded from this commit only.
HF_REVISION = "9e97599868962bd0079b8db4b7f1efa9185fa1e7"
SOURCE_URL = f"https://huggingface.co/datasets/{HF_REPO}"
LICENSE = "CC BY-SA 3.0 (per the Hugging Face dataset card)"
CITATION = (
    "Mohanty, S. P., Hughes, D. P., & Salathé, M. (2016). Using deep learning for image-based "
    "plant disease detection. Frontiers in Plant Science, 7. doi:10.3389/fpls.2016.01419"
)
CONFIG = "color"
MVP_CROPS = ("Potato", "Tomato")
# Bump when the split or duplicate rules change, so split manifests stay traceable.
SPLIT_VERSION = "pv-split-v2"
TEST_FRACTION = 0.20
VAL_FRACTION = 0.15
# dHash Hamming distance at or below which two same-class images are grouped. Chosen from the
# measured group sizes: 6 bits keeps the largest group at 76 images, 8 bits chains classes
# into groups of 500+ (docs/datasets/plantvillage.md).
NEAR_DUP_BITS = 6
DATA_CLASSIFICATION = DataClassification.OBSERVED

RAW_SUBDIR = Path("raw") / "plantvillage"
SPLIT_FILES = {"train": "splits/color_train.txt", "test": "splits/color_test.txt"}
LEAF_MAP = "leaf_grouping/leaf-map.json"


def raw_dir() -> Path:
    return get_settings().data_dir / RAW_SUBDIR


@dataclass(frozen=True)
class ClassName:
    label: str  # publisher folder name, e.g. "Potato___Late_blight"
    crop: str
    disease: str

    @classmethod
    def parse(cls, label: str) -> ClassName:
        crop, sep, disease = label.partition("___")
        if not sep or not crop or not disease:
            raise ValueError(f"not a PlantVillage class label: {label!r}")
        return cls(label, crop, disease)


def leaf_id(rel_path: str, leaf_map: dict[str, list[str]]) -> str:
    """Physical-leaf id, ported unchanged from the publisher's `plant_village.py` loader."""
    parts = rel_path.split("/")
    class_name, file_name = parts[2], parts[3]
    ident = file_name.replace("_final_masked", "")
    if "___" in ident:
        ident = ident.split("___")[-1]
    ident = ident.split("copy")[0]
    for ext in (".jpg", ".JPG", ".png", ".PNG"):
        ident = ident.replace(ext, "")
    ident = ident.strip()
    suggestions = leaf_map.get(ident.lower().strip())
    if suggestions is None:
        return f"fallback_{ident}"
    if len(suggestions) == 1:
        return suggestions[0]
    for s in suggestions:
        if class_name in s:
            return s
    return f"fallback_{ident}"


def read_index(root: Path) -> pd.DataFrame:
    """One row per MVP image listed in the publisher's split files (no image reads)."""
    leaf_map = json.loads((root / LEAF_MAP).read_text())
    rows = []
    for split, rel in SPLIT_FILES.items():
        for line in (root / rel).read_text().splitlines():
            path = line.strip()
            if not path:
                continue
            parts = path.split("/")
            if len(parts) != 4 or parts[1] != CONFIG:
                raise ValueError(f"unexpected path in {rel}: {path!r}")
            cls = ClassName.parse(parts[2])
            if cls.crop not in MVP_CROPS:
                continue
            rows.append({"path": path, "label": cls.label, "crop": cls.crop,
                         "disease": cls.disease, "publisher_split": split,
                         "leaf_id": leaf_id(path, leaf_map)})
    df = pd.DataFrame(rows)
    if df["path"].duplicated().any():
        raise ValueError("an image is listed more than once in the publisher split files")
    return df


def extract(zip_path: Path, paths: list[str], dest: Path) -> int:
    """Extract only the listed members (e.g. raw/color/Potato___healthy/x.JPG) into dest."""
    wanted = set(paths)
    n = 0
    with zipfile.ZipFile(zip_path) as zf:
        names = set(zf.namelist())
        missing = wanted - names
        if missing:
            raise FileNotFoundError(f"{len(missing)} listed images are not in the zip, "
                                    f"e.g. {sorted(missing)[:3]}")
        for name in sorted(wanted):
            target = dest / name
            if not target.exists():
                zf.extract(name, dest)
            n += 1
    return n


def dhash(path: Path, size: int = 8) -> str:
    """64-bit difference hash; identical values flag exact or near-exact duplicate images."""
    with Image.open(path) as im:
        g = np.asarray(im.convert("L").resize((size + 1, size), Image.Resampling.LANCZOS),
                       dtype=np.int16)
    bits = (g[:, 1:] > g[:, :-1]).flatten()
    return f"{int(''.join('1' if b else '0' for b in bits), 2):016x}"


def image_stats(path: Path) -> dict:
    with Image.open(path) as im:
        im.verify()
    with Image.open(path) as im:
        return {"width": im.width, "height": im.height, "mode": im.mode}


def file_stem(rel_path: str) -> str:
    """Filename after the uuid prefix, without extension or a trailing ".N" repeat suffix."""
    name = rel_path.split("/")[-1].split("___")[-1]
    return re.sub(r"(\.\d+)?\.(jpe?g|png)$", "", name, flags=re.IGNORECASE).strip()


_POPCOUNT = np.array([bin(i).count("1") for i in range(256)], dtype=np.uint8)


def hamming(a: np.uint64, b: np.ndarray) -> np.ndarray:
    return _POPCOUNT[(b ^ a).view(np.uint8).reshape(-1, 8)].sum(axis=1)


def group_ids(df: pd.DataFrame, near_dup_bits: int = NEAR_DUP_BITS) -> np.ndarray:
    """Union-find group per row over shared leaf_id, filename stem or close dHash (same class)."""
    parent = np.arange(len(df))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a: int, b: int) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    labels = df["label"].to_numpy()
    for key in (df["leaf_id"], df["path"].map(file_stem)):
        first: dict[tuple[str, str], int] = {}
        for i, k in enumerate(key):
            j = first.setdefault((labels[i], k), i)
            if j != i:
                union(i, j)
    hashes = np.array([int(h, 16) for h in df["dhash"]], dtype=np.uint64)
    for idx in pd.Series(range(len(df))).groupby(labels).indices.values():
        hh = hashes[idx]
        for a in range(len(idx) - 1):
            for c in np.flatnonzero(hamming(hh[a], hh[a + 1:]) <= near_dup_bits):
                union(idx[a], idx[a + 1 + c])
    return np.array([find(i) for i in range(len(df))])


def make_split(df: pd.DataFrame, seed: int = 42, test_fraction: float = TEST_FRACTION,
               val_fraction: float = VAL_FRACTION) -> pd.DataFrame:
    """Assign train/val/test to every row of an index that has a `dhash` column."""
    out = df.copy().reset_index(drop=True)
    out["group"] = group_ids(out)
    out["split"] = "train"
    rng = np.random.default_rng(seed)
    for label in sorted(out["label"].unique()):
        rows = out[out["label"] == label]
        sizes = rows.groupby("group").size()
        order = sizes.index.to_numpy()[rng.permutation(len(sizes))]
        total, filled = len(rows), 0
        for g in order:
            if filled < test_fraction * total:
                out.loc[out["group"] == g, "split"] = "test"
            elif filled < (test_fraction + val_fraction) * total:
                out.loc[out["group"] == g, "split"] = "val"
            else:
                break
            filled += sizes[g]
    return out


def split_leakage_report(split: pd.DataFrame) -> dict:
    used = split[split["split"].isin(["train", "val", "test"])]
    by = {s: used[used["split"] == s] for s in ("train", "val", "test")}
    report = {}
    for a, b in (("train", "val"), ("train", "test"), ("val", "test")):
        report[f"{a}_{b}"] = {
            "sharedGroups": len(set(by[a]["group"]) & set(by[b]["group"])),
            "sharedLeafIds": len(set(by[a]["leaf_id"]) & set(by[b]["leaf_id"])),
            "sharedDhash": len(set(by[a]["dhash"]) & set(by[b]["dhash"])),
        }
    return report


def counts(split: pd.DataFrame) -> dict:
    c = Counter(zip(split["label"], split["split"], strict=True))
    labels = sorted(split["label"].unique())
    return {lab: {s: c.get((lab, s), 0) for s in ("train", "val", "test")}
            for lab in labels}


def dataset_version(root: Path) -> str:
    """Pinned revision plus a hash of the split files and leaf map that define the MVP subset."""
    h = hashlib.sha256()
    for rel in (SPLIT_FILES["train"], SPLIT_FILES["test"], LEAF_MAP):
        h.update((root / rel).read_bytes())
    return f"plantvillage-{CONFIG}-{HF_REVISION[:8]}-{h.hexdigest()[:12]}"
