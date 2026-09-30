"""Verify the PlantVillage download, extract the MVP classes and write the train/val/test split.

    python scripts/prepare_plantvillage.py

Expects (see docs/datasets/plantvillage.md for the download commands):
  data/raw/plantvillage/data.zip                       from HF mohanty/PlantVillage @ HF_REVISION
  data/raw/plantvillage/splits/color_{train,test}.txt  same revision
  data/raw/plantvillage/leaf_grouping/leaf-map.json    same revision

Steps (all in agri_ml.datasets.plantvillage):
  1. check the zip's size and sha256 against the values Hugging Face reports for the revision
  2. read_index(): Potato + Tomato rows of the publisher's color split files, with leaf_id
  3. extract(): only those images, into data/raw/plantvillage/raw/color/<class>/
  4. decode every image (fails on corrupt files) and compute a dHash
  5. make_split(): group by leaf_id / filename stem / near-identical dHash within a class,
     then assign whole groups per class to test (20%), validation (15%) and train (seeded)

Output: data/processed/plantvillage/<dataset_version>/<SPLIT_VERSION>/{split.csv,manifest.json}.
An existing output folder is never overwritten. No image is modified.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import UTC, datetime

from agri_ml.config import get_settings
from agri_ml.datasets import plantvillage as pv

# Reported by Hugging Face for data.zip at HF_REVISION (x-linked-size / x-linked-etag headers).
EXPECTED_ZIP_BYTES = 2_184_723_441
EXPECTED_ZIP_SHA256 = "fba30c6a7965e49be94b47a62f8aff6cfb1c35c27f475f22092b56db41745e84"


def sha256(path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    root = pv.raw_dir()
    zip_path = root / "data.zip"
    size = zip_path.stat().st_size
    if size != EXPECTED_ZIP_BYTES:
        print(f"data.zip is {size} bytes, expected {EXPECTED_ZIP_BYTES}", file=sys.stderr)
        return 1
    digest = sha256(zip_path)
    if digest != EXPECTED_ZIP_SHA256:
        print(f"data.zip sha256 {digest} != expected {EXPECTED_ZIP_SHA256}", file=sys.stderr)
        return 1

    version = pv.dataset_version(root)
    out_dir = get_settings().data_dir / "processed" / pv.DATASET_NAME / version / pv.SPLIT_VERSION
    if out_dir.exists():
        print(f"{out_dir} already exists. Delete it or bump SPLIT_VERSION to regenerate.",
              file=sys.stderr)
        return 1

    index = pv.read_index(root)
    n = pv.extract(zip_path, index["path"].tolist(), root)
    stats = [pv.image_stats(root / p) for p in index["path"]]
    index["width"] = [s["width"] for s in stats]
    index["height"] = [s["height"] for s in stats]
    index["mode"] = [s["mode"] for s in stats]
    index["dhash"] = [pv.dhash(root / p) for p in index["path"]]

    # Identical hashes under different labels would mean label noise, not just duplication.
    labels_per_hash = index.groupby("dhash")["label"].nunique()
    cross_label = index[index["dhash"].isin(labels_per_hash[labels_per_hash > 1].index)]
    dup_groups = index.groupby("dhash").size()

    split = pv.make_split(index, seed=args.seed)
    out_dir.mkdir(parents=True)
    split.to_csv(out_dir / "split.csv", index=False)
    manifest = {
        "dataset": pv.DATASET_NAME,
        "datasetVersion": version,
        "splitVersion": pv.SPLIT_VERSION,
        "source": pv.SOURCE_URL,
        "revision": pv.HF_REVISION,
        "license": pv.LICENSE,
        "citation": pv.CITATION,
        "config": pv.CONFIG,
        "crops": list(pv.MVP_CROPS),
        "dataClassification": str(pv.DATA_CLASSIFICATION),
        "zip": {"bytes": size, "sha256": digest},
        "seed": args.seed,
        "testFraction": pv.TEST_FRACTION,
        "valFraction": pv.VAL_FRACTION,
        "nearDuplicateBits": pv.NEAR_DUP_BITS,
        "publisherSplitUsed": False,
        "generatedAt": datetime.now(UTC).isoformat(timespec="seconds"),
        "imagesExtracted": n,
        "imageSizes": index.groupby(["width", "height", "mode"]).size()
                           .rename("n").reset_index().to_dict("records"),
        "duplicates": {
            "dhashGroupsWithMoreThanOneImage": int((dup_groups > 1).sum()),
            "imagesInThoseGroups": int(dup_groups[dup_groups > 1].sum()),
            "imagesWithHashSharedAcrossLabels": len(cross_label),
            "crossLabelExamples": cross_label.sort_values("dhash")[["dhash", "label", "path"]]
                                             .head(20).to_dict("records"),
        },
        "groups": {"distinct": int(split["group"].nunique()),
                   "largest": int(split.groupby("group").size().max()),
                   "perLabel": split.groupby("label")["group"].nunique().to_dict()},
        "leafIds": {"distinct": int(index["leaf_id"].nunique()),
                    "fallbackIds": int(index["leaf_id"].str.startswith("fallback_").sum())},
        "counts": pv.counts(split),
        "totals": split["split"].value_counts().to_dict(),
        "leakage": pv.split_leakage_report(split),
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, default=int))

    print(f"dataset_version : {version}")
    print(f"output          : {out_dir}")
    print(json.dumps({k: manifest[k] for k in ("totals", "duplicates", "leafIds", "leakage")},
                     indent=2, default=int))
    return 0


if __name__ == "__main__":
    sys.exit(main())
