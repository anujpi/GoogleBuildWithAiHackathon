"""Validate crop_yield.csv and write the cleaned, flagged copy. Does not train anything.

    python scripts/prepare_crop_yield.py                  # reads data/raw/crop_yield.csv
    python scripts/prepare_crop_yield.py --path /some/crop_yield.csv

Steps (all in agri_ml.datasets.crop_yield):
  1. load_raw(): check the 10 expected columns, read with explicit dtypes
  2. clean(): strip whitespace from Crop, Season, State
  3. validate(): stop with exit code 1 on structural or integrity problems
     (missing values, duplicate keys, negative or infinite values, unexpected dtypes)
  4. quality_flags(): add flag_* review columns; no values changed, no rows removed

Output goes to data/processed/crop_yield/<dataset_version>/<preprocessing_version>/:
  crop_yield_clean.csv   cleaned rows plus flag_* columns
  manifest.json          input hash, versions, row counts, flag counts, validation report
An existing output folder is never overwritten.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import UTC, datetime
from pathlib import Path

from agri_ml.config import get_settings
from agri_ml.datasets import crop_yield as cy


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--path", type=Path, default=None)
    parser.add_argument("--out-root", type=Path, default=None,
                        help="default: <AGRI_ML_DATA_DIR>/processed/crop_yield")
    args = parser.parse_args()

    path = args.path or cy.default_path()
    raw = cy.load_raw(path)
    df = cy.clean(raw)
    report = cy.validate(df)
    if report.problems:
        print("validation failed:", *report.problems, sep="\n  ", file=sys.stderr)
        return 1

    flagged = cy.quality_flags(df)
    flag_cols = [c for c in flagged.columns if c.startswith("flag_")]
    version = cy.dataset_version(path)

    out_root = args.out_root or get_settings().data_dir / "processed" / cy.DATASET_NAME
    out_dir = out_root / version / cy.PREPROCESSING_VERSION
    if out_dir.exists():
        print(f"{out_dir} already exists; it was produced from the same input and preprocessing "
              "version. Delete it or bump PREPROCESSING_VERSION to regenerate.", file=sys.stderr)
        return 1
    out_dir.mkdir(parents=True)

    flagged.to_csv(out_dir / "crop_yield_clean.csv", index=False)
    manifest = {
        "dataset": cy.DATASET_NAME,
        "datasetVersion": version,
        "inputFile": str(path),
        "inputSha256": cy.file_sha256(path),
        "preprocessingVersion": cy.PREPROCESSING_VERSION,
        "generatedAt": datetime.now(UTC).isoformat(timespec="seconds"),
        "rows": {"raw": len(raw), "clean": len(flagged), "dropped": len(raw) - len(flagged)},
        "flagCounts": {c: int(flagged[c].sum()) for c in flag_cols},
        "flagRules": {
            "aggregateCrops": sorted(cy.AGGREGATE_CROPS),
            "yieldMismatchFactor": cy.YIELD_MISMATCH_FACTOR,
            "seriesJumpFactor": cy.SERIES_JUMP_FACTOR,
            "seriesJumpMinRows": cy.SERIES_JUMP_MIN_ROWS,
            "incompleteYearStateShare": cy.INCOMPLETE_YEAR_STATE_SHARE,
        },
        "validation": report.to_dict(),
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2))

    print(f"dataset_version : {version}")
    print(f"rows            : {len(flagged)} (none dropped)")
    for c in flag_cols:
        print(f"{c:<26}: {int(flagged[c].sum())}")
    print(f"wrote {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
