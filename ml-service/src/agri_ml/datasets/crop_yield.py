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
"""

from __future__ import annotations

import hashlib
from dataclasses import asdict, dataclass, field
from pathlib import Path

import pandas as pd

from agri_ml.config.settings import get_settings
from agri_ml.schemas.common import DataClassification

DATASET_NAME = "crop_yield"
RELATIVE_PATH = Path("raw") / "crop_yield.csv"

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
# and Pesticide look derived (see the catalogue entry), which is why this is documented per column there.
DATA_CLASSIFICATION = DataClassification.OBSERVED

_DTYPES = {
    "Crop": "string",
    "Crop_Year": "int64",
    "Season": "string",
    "State": "string",
    **{c: "float64" for c in NUMERIC_COLUMNS},
}


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
