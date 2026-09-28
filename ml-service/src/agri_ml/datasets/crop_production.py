"""S01: data.gov.in district-wise season-wise crop production. Load, validate and clean.

Also reads the DES all-India export of the same data (des_district_season_apy_1997_2020.csv),
which names its columns `State` and `District` instead of `State_Name` and `District_Name`.

One cleaned row = one district x crop x season x crop_year, with area (ha) and production. The
production unit is tonnes except for the crops in NON_TONNE_UNITS.
"""

from pathlib import Path

import numpy as np
import pandas as pd

# Raw column names differ between the API (`area_`, `production_`) and CSV exports
# (`Area`, `Production`). Both normalise to these names.
REQUIRED_COLUMNS = ["state_name", "district_name", "crop_year", "season", "crop", "area", "production"]
KEY_COLUMNS = ["state_name", "district_name", "crop", "season", "crop_year"]
# DES export headers that differ from the S01 API ones, after lower-casing and trimming.
COLUMN_ALIASES = {"state": "state_name", "district": "district_name"}
# Rows that are sums of other rows in the same file (DES publishes an oilseeds total).
AGGREGATE_CROPS = {"Oilseeds Total"}
# Crops whose production is not reported in tonnes (keys are cleaned, title-cased names).
NON_TONNE_UNITS = {
    "Coconut": "nuts",
    "Cotton(Lint)": "bales of 170 kg",
    "Jute": "bales of 180 kg",
    "Mesta": "bales of 180 kg",
    "Sannhamp": "bales of 180 kg",
}


def _normalise_column(name: str) -> str:
    name = name.strip().lower().rstrip("_")
    return COLUMN_ALIASES.get(name, name)


def load_raw(raw_dir: Path) -> pd.DataFrame:
    """Read every raw S01 CSV in `raw_dir` as strings, with normalised column names."""
    files = sorted(raw_dir.glob("*.csv"))
    if not files:
        raise FileNotFoundError(f"no S01 CSV files in {raw_dir}; run scripts/download_s01_crop_production.py")
    frames = [pd.read_csv(f, dtype=str, keep_default_na=False) for f in files]
    df = pd.concat(frames, ignore_index=True)
    df.columns = [_normalise_column(c) for c in df.columns]
    return df


def validate_raw(df: pd.DataFrame) -> None:
    missing = [c for c in REQUIRED_COLUMNS if c not in df.columns]
    if missing:
        raise ValueError(f"S01 raw data is missing columns {missing}; got {list(df.columns)}")


def clean(df: pd.DataFrame) -> tuple[pd.DataFrame, dict[str, int]]:
    """Return the cleaned frame and a report of how many rows each step removed.

    Steps:
    1. Trim whitespace and title-case the text keys (the source pads season labels).
    2. Parse area/production/crop_year as numbers; unparseable or blank values become NaN.
    3. Drop exact duplicate rows, rows with no crop/district/state name, and aggregate rows
       (e.g. "Oilseeds total", which double-counts the individual oilseeds).
    4. Drop rows with missing or non-positive area. Area is the denominator of yield and an input.
    5. Drop rows with missing production. Blank production is *missing*, not zero.
    6. Drop rows with negative production (invalid).
    7. If a key (district, crop, season, year) still repeats with different values, drop all
       copies of it: we cannot tell which one is correct.
    """
    # Normalise here too so callers can pass a raw export without going through load_raw().
    df = df.rename(columns=_normalise_column)
    validate_raw(df)
    report: dict[str, int] = {"raw_rows": len(df)}
    out = df[REQUIRED_COLUMNS].copy()

    for col in ["state_name", "district_name", "season", "crop"]:
        text = out[col].fillna("").astype(str).str.strip()
        out[col] = text.str.replace(r"\s+", " ", regex=True).str.title()
    for col in ["area", "production", "crop_year"]:
        out[col] = pd.to_numeric(out[col].astype(str).str.strip().replace({"": np.nan, "NA": np.nan}),
                                 errors="coerce")

    before = len(out)
    out = out.drop_duplicates()
    report["dropped_exact_duplicates"] = before - len(out)

    before = len(out)
    out = out[out["crop_year"].notna()]
    report["dropped_missing_year"] = before - len(out)

    before = len(out)
    out = out[(out["crop"] != "") & (out["district_name"] != "") & (out["state_name"] != "")]
    report["dropped_missing_name"] = before - len(out)

    before = len(out)
    out = out[~out["crop"].isin(AGGREGATE_CROPS)]
    report["dropped_aggregate_crop_rows"] = before - len(out)

    before = len(out)
    out = out[out["area"].notna() & (out["area"] > 0)]
    report["dropped_missing_or_nonpositive_area"] = before - len(out)

    before = len(out)
    out = out[out["production"].notna()]
    report["dropped_missing_production"] = before - len(out)

    before = len(out)
    out = out[out["production"] >= 0]
    report["dropped_negative_production"] = before - len(out)

    before = len(out)
    out = out[~out.duplicated(KEY_COLUMNS, keep=False)]
    report["dropped_conflicting_key_duplicates"] = before - len(out)

    out["crop_year"] = out["crop_year"].astype(int)
    out = out.sort_values(KEY_COLUMNS).reset_index(drop=True)
    report["clean_rows"] = len(out)
    return out, report
