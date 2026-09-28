"""Data audit / exploratory checks for crop_yield.csv. Does not train anything.

Usage:
    python scripts/audit_crop_yield.py                      # uses data/raw/crop_yield.csv
    python scripts/audit_crop_yield.py --path /some/crop_yield.csv
    python scripts/audit_crop_yield.py --json-out artifacts/audit/crop_yield.json
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd

from agri_ml.datasets import crop_yield as cy


def whitespace_padding(raw: pd.DataFrame) -> dict[str, int]:
    """Rows per text column whose raw value had leading/trailing whitespace."""
    return {c: int((raw[c] != raw[c].str.strip()).sum()) for c in cy.TEXT_COLUMNS}


def yield_consistency(df: pd.DataFrame) -> dict:
    """Compare the supplied Yield against Production / Area."""
    ok = df["Area"] > 0
    ratio = df.loc[ok, "Yield"] / (df.loc[ok, "Production"] / df.loc[ok, "Area"])
    ratio = ratio.replace([float("inf"), float("-inf")], pd.NA).dropna()
    rel = (ratio - 1).abs()
    return {
        "rows_compared": int(len(ratio)),
        "within_1pct": int((rel <= 0.01).sum()),
        "within_10pct": int((rel <= 0.10).sum()),
        "ratio_quantiles": {str(q): float(ratio.quantile(q)) for q in (0.01, 0.25, 0.5, 0.75, 0.99)},
    }


def constant_within(df: pd.DataFrame, value: pd.Series, by: list[str]) -> dict:
    """How many groups have a single (rounded) value, i.e. the column is set at group level."""
    nunique = value.round(4).groupby([df[c] for c in by]).nunique()
    return {"groups": int(len(nunique)), "groups_constant": int((nunique == 1).sum())}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--path", type=Path, default=None)
    parser.add_argument("--json-out", type=Path, default=None)
    args = parser.parse_args()

    path = args.path or cy.default_path()
    raw = cy.load_raw(path)
    df = cy.clean(raw)
    report = cy.validate(df)

    area_pos = df["Area"] > 0
    extra = {
        "file": str(path),
        "dataset_version": cy.dataset_version(path),
        "whitespace_padded_rows": whitespace_padding(raw),
        "distinct_before_strip": {c: int(raw[c].nunique()) for c in cy.TEXT_COLUMNS},
        "rows_per_year": {int(k): int(v) for k, v in df["Crop_Year"].value_counts().sort_index().items()},
        "rows_per_season": {k: int(v) for k, v in df["Season"].value_counts().items()},
        "rows_per_state": {k: int(v) for k, v in df["State"].value_counts().items()},
        "state_year_span": {
            s: [int(g.min()), int(g.max()), int(g.nunique())]
            for s, g in df.groupby("State")["Crop_Year"]
        },
        "numeric_describe": json.loads(df[cy.NUMERIC_COLUMNS].describe().to_json()),
        "yield_vs_production_over_area": yield_consistency(df),
        "rainfall_constant_per_state_year": constant_within(
            df, df["Annual_Rainfall"], ["State", "Crop_Year"]
        ),
        "fertilizer_per_area_constant_per_year": constant_within(
            df[area_pos], df.loc[area_pos, "Fertilizer"] / df.loc[area_pos, "Area"], ["Crop_Year"]
        ),
        "pesticide_per_area_constant_per_year": constant_within(
            df[area_pos], df.loc[area_pos, "Pesticide"] / df.loc[area_pos, "Area"], ["Crop_Year"]
        ),
        "top_median_yield_by_crop": {
            k: float(v)
            for k, v in df.groupby("Crop")["Yield"].median().sort_values(ascending=False).head(8).items()
        },
    }

    print(f"file             : {path}")
    print(f"dataset_version  : {extra['dataset_version']}")
    print(f"rows             : {report.row_count}")
    print(f"columns          : {report.columns}")
    print(f"missing values   : {report.missing_values}")
    print(f"duplicate rows   : {report.duplicate_rows}")
    print(f"duplicate keys   : {report.duplicate_keys}  (Crop, Crop_Year, Season, State)")
    print(f"year range       : {report.year_min}-{report.year_max}")
    print(f"states ({report.n_states})     : {report.states}")
    print(f"crops ({report.n_crops})      : {report.crops}")
    print(f"seasons ({report.n_seasons})     : {report.seasons}")
    print(f"negative values  : {report.negative_values}")
    print(f"zero values      : {report.zero_values}")
    print(f"problems         : {report.problems or 'none'}")
    for key in (
        "whitespace_padded_rows",
        "distinct_before_strip",
        "rows_per_season",
        "yield_vs_production_over_area",
        "rainfall_constant_per_state_year",
        "fertilizer_per_area_constant_per_year",
        "pesticide_per_area_constant_per_year",
        "top_median_yield_by_crop",
    ):
        print(f"{key:<17}: {extra[key]}")
    print(f"rows_per_year    : {extra['rows_per_year']}")
    print(f"state_year_span  : {extra['state_year_span']}  [first, last, n_years]")

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps({**report.to_dict(), **extra}, indent=2))
        print(f"wrote {args.json_out}")


if __name__ == "__main__":
    main()
