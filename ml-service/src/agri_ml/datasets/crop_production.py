"""S01: data.gov.in district-wise season-wise crop production. Load, validate and clean.

Catalogue entry: docs/datasets/s01_up.md. One cleaned row = one district x crop x season x
crop_year, with area (hectares) and production (tonnes).
"""

import hashlib
import json
from pathlib import Path

import numpy as np
import pandas as pd

# The API names columns `area_`/`production_`, CSV exports use `Area`/`Production`, and headers may
# carry stray whitespace. `normalise_columns` maps all of them to these names.
REQUIRED_COLUMNS = [
    "state_name",
    "district_name",
    "crop_year",
    "season",
    "crop",
    "area",
    "production",
]
KEY_COLUMNS = ["state_name", "district_name", "crop", "season", "crop_year"]
TEXT_COLUMNS = ["state_name", "district_name", "season", "crop"]
NUMERIC_COLUMNS = ["area", "production", "crop_year"]
S01_RESOURCE_ID = "35be999b-0208-4354-b557-f6ca9a5355de"
# Crop years outside this range are not S01 years (the resource covers 1997-2015).
VALID_YEARS = (1950, 2100)
# A reported yield more than this factor away from its crop's median yield is treated as a unit or
# entry error (e.g. production in kg). This is a data-quality rule, not an agronomic claim.
IMPLAUSIBLE_YIELD_FACTOR = 10.0


def normalise_columns(df: pd.DataFrame) -> pd.DataFrame:
    """Return a copy with column names stripped, lower-cased and without trailing underscores."""
    out = df.copy()
    out.columns = [str(c).strip().lower().rstrip("_") for c in out.columns]
    return out


def load_raw(raw_dir: Path) -> pd.DataFrame:
    """Read every raw S01 CSV in `raw_dir` as strings, exactly as stored (headers untouched)."""
    files = sorted(raw_dir.glob("*.csv"))
    if not files:
        raise FileNotFoundError(
            f"no S01 CSV files in {raw_dir}; run scripts/download_s01_crop_production.py"
        )
    frames = [normalise_columns(pd.read_csv(f, dtype=str, keep_default_na=False)) for f in files]
    return pd.concat(frames, ignore_index=True)


def dataset_version(raw_dir: Path, prefix: str = "s01") -> str:
    """Content hash of the raw CSVs (names and bytes), so any change gives a new version."""
    digest = hashlib.sha256()
    for path in sorted(raw_dir.glob("*.csv")):
        digest.update(path.name.encode())
        digest.update(path.read_bytes())
    return f"{prefix}-{digest.hexdigest()[:12]}"


def validate_manifest(raw_dir: Path) -> dict:
    """Check that `raw_dir` holds a complete, registered-key S01 download and return its manifest.

    Training refuses anything else, so a partial pull, a hand-placed file or a fixture can never
    become a served artifact labelled as S01.
    """
    path = raw_dir / "manifest.json"
    if not path.is_file():
        raise FileNotFoundError(
            f"no S01 manifest in {raw_dir}; run scripts/download_s01_crop_production.py"
        )
    manifest = json.loads(path.read_text())
    problems = []
    if manifest.get("resourceId") != S01_RESOURCE_ID:
        problems.append(f"resourceId is {manifest.get('resourceId')!r}, not S01")
    if manifest.get("keyType") != "REGISTERED":
        problems.append("the download did not use a registered API key")
    files = manifest.get("files") or {}
    if not files:
        problems.append("manifest lists no files")
    for name, info in files.items():
        if not (raw_dir / name).is_file():
            problems.append(f"{name} is listed but missing")
        elif info.get("rows") != info.get("apiTotal") or not info.get("rows"):
            problems.append(f"{name}: {info.get('rows')} rows of {info.get('apiTotal')}")
    unlisted = sorted(p.name for p in raw_dir.glob("*.csv") if p.name not in files)
    if unlisted:
        problems.append(f"CSV files not in the manifest: {unlisted}")
    if problems:
        raise ValueError("S01 download is not usable: " + "; ".join(problems))
    return manifest


def validate_raw(df: pd.DataFrame) -> None:
    """Raise ValueError if required columns are missing. Accepts any header style."""
    columns = normalise_columns(df.head(0)).columns
    missing = [c for c in REQUIRED_COLUMNS if c not in columns]
    if missing:
        raise ValueError(f"S01 raw data is missing columns {missing}; got {list(df.columns)}")


def clean(df: pd.DataFrame) -> tuple[pd.DataFrame, dict[str, int]]:
    """Clean a raw S01 frame. Returns the cleaned frame and rows removed per step.

    Input: raw S01 rows with any header style (`Area`, `area_`, ` State_Name `...), values as
    strings or numbers. Nothing is assumed to have been normalised before this call.

    Steps, in order:
    1. Normalise column names; keep only the required columns.
    2. Trim whitespace and title-case the text keys (the source pads season labels).
    3. Parse area/production/crop_year as numbers; blank, "NA" or unparseable values become NaN.
    4. Drop exact duplicate rows.
    5. Drop rows with a missing crop year or a blank state/district/crop/season.
    6. Drop rows whose crop year is not a whole year within VALID_YEARS.
    7. Drop rows with missing or non-positive area (area is the yield denominator and an input).
    8. Drop rows with missing production. Blank production is *missing*, not zero.
    9. Drop rows with negative production (invalid).
    10. Drop rows whose positive yield is more than IMPLAUSIBLE_YIELD_FACTOR away from the crop's
       median yield (unit/entry errors).
    11. If a (state, district, crop, season, year) key still repeats with different values, drop
        every copy: we cannot tell which one is correct.
    Output is sorted by key, so the result does not depend on input row order.
    """
    validate_raw(df)
    out = normalise_columns(df)[REQUIRED_COLUMNS].copy()
    report: dict[str, int] = {"raw_rows": len(out)}

    for col in TEXT_COLUMNS:
        out[col] = out[col].astype(str).str.strip().str.replace(r"\s+", " ", regex=True).str.title()
    for col in NUMERIC_COLUMNS:
        text = out[col].astype(str).str.strip().replace({"": np.nan, "NA": np.nan})
        out[col] = pd.to_numeric(text, errors="coerce")

    def drop(name: str, keep: pd.Series) -> None:
        nonlocal out
        report[name] = int((~keep).sum())
        out = out[keep]

    drop("dropped_exact_duplicates", ~out.duplicated())
    blank_text = out[TEXT_COLUMNS].isin(["", "Nan", "None"]).any(axis=1)
    drop("dropped_missing_key", out["crop_year"].notna() & ~blank_text)
    year = out["crop_year"]
    drop("dropped_invalid_year", (year == year.round()) & year.between(*VALID_YEARS))
    drop("dropped_missing_or_nonpositive_area", out["area"].notna() & (out["area"] > 0))
    drop("dropped_missing_production", out["production"].notna())
    drop("dropped_negative_production", out["production"] >= 0)

    yield_ = out["production"] / out["area"]
    crop_median = yield_.where(yield_ > 0).groupby(out["crop"]).transform("median")
    ratio = yield_ / crop_median
    implausible = (yield_ > 0) & (
        (ratio > IMPLAUSIBLE_YIELD_FACTOR) | (ratio < 1 / IMPLAUSIBLE_YIELD_FACTOR)
    )
    drop("dropped_implausible_yield", ~implausible)
    drop("dropped_conflicting_key_duplicates", ~out.duplicated(KEY_COLUMNS, keep=False))

    out["crop_year"] = out["crop_year"].astype(int)
    out = out.sort_values(KEY_COLUMNS).reset_index(drop=True)
    report["clean_rows"] = len(out)
    return out, report
