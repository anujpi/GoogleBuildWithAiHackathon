"""Loader and schema validation for the state-level crop yield dataset (crop_yield.csv).

Catalogue entry: docs/datasets/crop_yield.md.

This is HISTORICAL data (1997-2020). It is not current or real-time.

Preprocessing is deliberately minimal and reproducible:
  1. read the CSV with explicit dtypes
  2. strip leading/trailing whitespace from the text columns (the raw file pads
     values such as "Kharif     " and "Coconut ")

Nothing else is changed. Rows are not dropped or de-duplicated, units are not
converted, and Yield is not recomputed. Those are modelling decisions and belong to
feature engineering once the audit findings are agreed.

`quality_flags()` adds boolean review columns (prefix `flag_`) without changing or removing
any value. The flag rules are audit heuristics documented in docs/datasets/crop_yield.md.
"""

from __future__ import annotations

import hashlib
from dataclasses import asdict, dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd

from agri_ml.config.settings import get_settings
from agri_ml.schemas.common import DataClassification

DATASET_NAME = "crop_yield"
RELATIVE_PATH = Path("raw") / "crop_yield.csv"
# Bump when clean() or quality_flags() changes, so processed outputs stay traceable.
PREPROCESSING_VERSION = "crop-yield-prep-v1"

TEXT_COLUMNS = ["Crop", "Season", "State"]
NUMERIC_COLUMNS = ["Area", "Production", "Annual_Rainfall", "Fertilizer", "Pesticide", "Yield"]
EXPECTED_COLUMNS = [
    "Crop",
    "Crop_Year",
    "Season",
    "State",
    "Area",
    "Production",
    "Annual_Rainfall",
    "Fertilizer",
    "Pesticide",
    "Yield",
]
# Combination that should identify one row: one crop, one season, one state, one year.
KEY_COLUMNS = ["Crop", "Crop_Year", "Season", "State"]

# The raw file is OBSERVED official statistics as republished by a third party. Fertilizer
# and Pesticide look derived (see the catalogue entry), which is why this is documented per
# column there.
DATA_CLASSIFICATION = DataClassification.OBSERVED

_DTYPES = {
    "Crop": "string",
    "Crop_Year": "int64",
    "Season": "string",
    "State": "string",
    **{c: "float64" for c in NUMERIC_COLUMNS},
}


# Audit heuristics for quality_flags(). They mark rows for review; they never drop rows.
# Crop labels that are totals over other crops, so they overlap the individual crop rows.
AGGREGATE_CROPS = frozenset({"Oilseeds total"})
# Supplied Yield differs from Production / Area by more than this factor (either way).
YIELD_MISMATCH_FACTOR = 2.0
# Area or Production differs from its (State, Crop, Season) series median by more than this factor.
SERIES_JUMP_FACTOR = 10.0
SERIES_JUMP_MIN_ROWS = 5
# A crop year is incomplete when it covers fewer than this share of the median states per year.
INCOMPLETE_YEAR_STATE_SHARE = 0.5
SERIES_KEYS = ["State", "Crop", "Season"]


class SchemaError(ValueError):
    """The file does not match the expected crop_yield schema."""


def default_path() -> Path:
    """Where the raw file is expected: <AGRI_ML_DATA_DIR>/raw/crop_yield.csv."""
    return get_settings().data_dir / RELATIVE_PATH


def file_sha256(path: Path) -> str:
    """Hash of the raw file. Its prefix identifies the dataset version."""
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dataset_version(path: Path) -> str:
    return f"{DATASET_NAME}-sha256-{file_sha256(path)[:12]}"


def load_raw(path: Path | None = None) -> pd.DataFrame:
    """Read the CSV exactly as stored, with explicit dtypes."""
    path = Path(path) if path is not None else default_path()
    if not path.is_file():
        raise FileNotFoundError(
            f"crop_yield.csv not found at {path}. Place the file at {default_path()} "
            "or set AGRI_ML_DATA_DIR."
        )
    header = pd.read_csv(path, nrows=0).columns.tolist()
    check_columns(header)
    return pd.read_csv(path, dtype=_DTYPES)


def clean(df: pd.DataFrame) -> pd.DataFrame:
    """Apply the documented preprocessing (whitespace stripping only). Returns a copy."""
    out = df.copy()
    for col in TEXT_COLUMNS:
        out[col] = out[col].str.strip()
    return out


def load(path: Path | None = None) -> pd.DataFrame:
    """Load and clean. This is the entry point other code should use."""
    return clean(load_raw(path))


def check_columns(columns: list[str]) -> None:
    missing = [c for c in EXPECTED_COLUMNS if c not in columns]
    extra = [c for c in columns if c not in EXPECTED_COLUMNS]
    if missing or extra:
        raise SchemaError(f"column mismatch: missing={missing} unexpected={extra}")


@dataclass
class ValidationReport:
    row_count: int
    columns: list[str]
    missing_values: dict[str, int]
    duplicate_rows: int
    duplicate_keys: int
    year_min: int
    year_max: int
    n_states: int
    n_crops: int
    n_seasons: int
    states: list[str]
    crops: list[str]
    seasons: list[str]
    negative_values: dict[str, int]
    zero_values: dict[str, int]
    problems: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return asdict(self)


def validate(df: pd.DataFrame) -> ValidationReport:
    """Schema and integrity checks on a *cleaned* frame. Findings go into `problems`.

    Raises SchemaError only for structural failures (wrong columns). Data-quality
    findings are reported, not raised, so the audit always produces a full report.
    """
    check_columns(df.columns.tolist())
    problems: list[str] = []

    missing = {c: int(n) for c, n in df.isna().sum().items()}
    if any(missing.values()):
        problems.append(f"missing values present: { {c: n for c, n in missing.items() if n} }")

    duplicate_rows = int(df.duplicated().sum())
    if duplicate_rows:
        problems.append(f"{duplicate_rows} fully duplicated rows")

    duplicate_keys = int(df.duplicated(subset=KEY_COLUMNS).sum())
    if duplicate_keys:
        problems.append(f"{duplicate_keys} rows repeat a (Crop, Crop_Year, Season, State) key")

    negative = {c: int((df[c] < 0).sum()) for c in NUMERIC_COLUMNS}
    if any(negative.values()):
        problems.append(f"negative values: { {c: n for c, n in negative.items() if n} }")

    zeros = {c: int((df[c] == 0).sum()) for c in NUMERIC_COLUMNS}

    wrong_dtypes = {c: str(df[c].dtype) for c, want in _DTYPES.items() if str(df[c].dtype) != want}
    if wrong_dtypes:
        problems.append(f"unexpected dtypes: {wrong_dtypes}")

    non_finite = {c: int((~np.isfinite(df[c].astype(float)) & df[c].notna()).sum())
                  for c in NUMERIC_COLUMNS}
    if any(non_finite.values()):
        problems.append(f"infinite values: { {c: n for c, n in non_finite.items() if n} }")

    for col in TEXT_COLUMNS:
        if (df[col] == "").any():
            problems.append(f"empty strings in {col}")

    return ValidationReport(
        row_count=len(df),
        columns=df.columns.tolist(),
        missing_values=missing,
        duplicate_rows=duplicate_rows,
        duplicate_keys=duplicate_keys,
        year_min=int(df["Crop_Year"].min()),
        year_max=int(df["Crop_Year"].max()),
        n_states=int(df["State"].nunique()),
        n_crops=int(df["Crop"].nunique()),
        n_seasons=int(df["Season"].nunique()),
        states=sorted(df["State"].dropna().unique().tolist()),
        crops=sorted(df["Crop"].dropna().unique().tolist()),
        seasons=sorted(df["Season"].dropna().unique().tolist()),
        negative_values=negative,
        zero_values=zeros,
        problems=problems,
    )


def quality_flags(df: pd.DataFrame) -> pd.DataFrame:
    """Return a copy of a *cleaned* frame with boolean `flag_*` review columns added.

    No value is changed and no row is removed. Whether a flagged row is excluded, corrected or
    kept is a modelling decision, made per task and documented there.
    """
    out = df.copy()
    production_over_area = out["Production"] / out["Area"].where(out["Area"] > 0)

    out["flag_zero_production"] = out["Production"] == 0
    # Yield and Production disagree about whether anything was produced.
    out["flag_yield_zero_mismatch"] = (out["Yield"] == 0) != (out["Production"] == 0)
    ratio = out["Yield"] / production_over_area.where(production_over_area > 0)
    out["flag_yield_mismatch"] = (ratio > YIELD_MISMATCH_FACTOR) | (
        ratio < 1 / YIELD_MISMATCH_FACTOR
    )

    groups = [out[k] for k in SERIES_KEYS]
    series_rows = out.groupby(SERIES_KEYS)["Crop_Year"].transform("size")
    jump = pd.Series(False, index=out.index)
    for col in ("Area", "Production"):
        positive = out[col].where(out[col] > 0)
        rel = positive / positive.groupby(groups).transform("median")
        jump |= (rel > SERIES_JUMP_FACTOR) | (rel < 1 / SERIES_JUMP_FACTOR)
    out["flag_series_jump"] = jump & (series_rows >= SERIES_JUMP_MIN_ROWS)

    out["flag_aggregate_crop"] = out["Crop"].isin(AGGREGATE_CROPS)

    states_per_year = out.groupby("Crop_Year")["State"].nunique()
    incomplete = states_per_year[
        states_per_year < INCOMPLETE_YEAR_STATE_SHARE * states_per_year.median()
    ].index
    out["flag_incomplete_year"] = out["Crop_Year"].isin(incomplete)

    flag_cols = [c for c in out.columns if c.startswith("flag_")]
    out[flag_cols] = out[flag_cols].fillna(False).astype(bool)
    out["flag_any"] = out[flag_cols].any(axis=1)
    return out


def season_label_changes(df: pd.DataFrame, min_years: int = 3) -> pd.DataFrame:
    """(State, Crop) pairs where one season label stops being reported before another starts.

    Example: a crop reported as "Whole Year" up to 2003 and as "Rabi" from 2004. A
    (State, Crop, Season) series then breaks at the switch even if the crop was grown throughout.
    """
    rows = []
    for (state, crop), g in df.groupby(["State", "Crop"]):
        years = g.groupby("Season")["Crop_Year"].agg(["min", "max", "nunique"])
        years = years[years["nunique"] >= min_years]
        for old, a in years.iterrows():
            for new, b in years.iterrows():
                if old != new and a["max"] < b["min"]:
                    rows.append({"State": state, "Crop": crop,
                                 "old_season": old, "old_first": int(a["min"]),
                                 "old_last": int(a["max"]),
                                 "new_season": new, "new_first": int(b["min"]),
                                 "new_last": int(b["max"])})
    return pd.DataFrame(rows, columns=["State", "Crop", "old_season", "old_first", "old_last",
                                       "new_season", "new_first", "new_last"])


def series_year_gaps(df: pd.DataFrame) -> pd.DataFrame:
    """Per (State, Crop, Season): first/last year, years present and missing years in between."""
    s = df.groupby(SERIES_KEYS)["Crop_Year"].agg(first="min", last="max", n_years="nunique")
    s["missing_years"] = s["last"] - s["first"] + 1 - s["n_years"]
    return s.reset_index()


# --- supply-model view ------------------------------------------------------------------------

# Crops whose Production is not in the same unit as the other crops (see the dataset doc:
# Coconut values are consistent with a count of nuts; Cotton(lint) may be in bales). Mixing them
# into one production model would compare incomparable numbers.
SUPPLY_EXCLUDED_CROPS = frozenset({"Coconut", "Cotton(lint)"})
SUPPLY_EXCLUDING_FLAGS = ("flag_incomplete_year", "flag_aggregate_crop", "flag_series_jump")
SUPPLY_COLUMNS = {
    "State": "state_name",
    "Crop": "crop",
    "Season": "season",
    "Crop_Year": "crop_year",
    "Area": "area",
    "Production": "production",
}


def to_supply_frame(df: pd.DataFrame) -> tuple[pd.DataFrame, dict[str, int]]:
    """Map a *cleaned* frame to the supply pipeline's columns and apply the documented exclusions.

    One output row = one state x crop x season x crop_year with area and production. The
    supplied Yield, Annual_Rainfall, Fertilizer and Pesticide columns are not carried over.
    Returns the frame and a report of how many rows each exclusion removed (a row can match
    several; each count is independent).
    """
    flagged = quality_flags(df)
    excluded_crop = flagged["Crop"].isin(SUPPLY_EXCLUDED_CROPS)
    drop = excluded_crop.copy()
    report: dict[str, int] = {
        "input_rows": len(flagged),
        "excluded_crop_unit": int(excluded_crop.sum()),
    }
    for flag in SUPPLY_EXCLUDING_FLAGS:
        report[flag] = int(flagged[flag].sum())
        drop |= flagged[flag]
    out = flagged.loc[~drop, list(SUPPLY_COLUMNS)].rename(columns=SUPPLY_COLUMNS)
    out = out.sort_values(["state_name", "crop", "season", "crop_year"]).reset_index(drop=True)
    report["excluded_total"] = int(drop.sum())
    report["supply_rows"] = len(out)
    return out, report
