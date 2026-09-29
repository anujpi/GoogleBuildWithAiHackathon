"""Canonical ids for the supply scope: one place that maps S01 labels to contract ids.

Contract (docs/ml-contracts/README.md):
- stateId:    lower-case slug, e.g. "up"
- districtId: "<stateId>-<slug of the S01 district name>", e.g. "up-agra"
- cropId:     lower-case slug, e.g. "potato"
- season:     the Season enum (KHARIF, RABI, ...)

Anything not listed here is out of scope and is dropped (and counted) during scoping.
"""

import re

import pandas as pd

from agri_ml.schemas.common import Season

# Final product scope (Master Specification, decision 2): Uttar Pradesh; potato/wheat/onion/maize.
STATES = {"Uttar Pradesh": "up"}
CROPS = {"Potato": "potato", "Wheat": "wheat", "Onion": "onion", "Maize": "maize"}
# S01 season labels after cleaning (whitespace-trimmed, title-cased).
SEASONS = {
    "Kharif": Season.KHARIF,
    "Rabi": Season.RABI,
    "Summer": Season.SUMMER,
    "Whole Year": Season.WHOLE_YEAR,
    "Autumn": Season.AUTUMN,
    "Winter": Season.WINTER,
}

STATE_LABELS = {v: k for k, v in STATES.items()}
CROP_LABELS = {v: k for k, v in CROPS.items()}

ID_PATTERN = r"^[a-z0-9][a-z0-9-]{0,49}$"


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def district_id(state_id: str, district_name: str) -> str:
    return f"{state_id}-{slug(district_name)}"[:50]


def to_scope(clean: pd.DataFrame) -> tuple[pd.DataFrame, dict[str, int]]:
    """Keep in-scope rows of a *cleaned* S01 frame and attach contract ids.

    Input: output of `datasets.crop_production.clean` (columns state_name, district_name,
    crop_year, season, crop, area, production). Output columns: state_id, district_id, district,
    crop (cropId), season (Season value), crop_year, area, production. The model's categorical
    features are `crop` and `season`, so they carry contract ids, not S01 labels.
    """
    report = {"rows_in": len(clean)}
    state = clean["state_name"].map(STATES)
    crop = clean["crop"].map(CROPS)
    season = clean["season"].map(SEASONS)
    # Counted in order: a row dropped for its state is not counted again for its crop or season.
    keep = pd.Series(True, index=clean.index)
    for name, mapped in (("state", state), ("crop", crop), ("season", season)):
        report[f"dropped_out_of_scope_{name}"] = int((keep & mapped.isna()).sum())
        keep &= mapped.notna()
    out = pd.DataFrame(
        {
            "state_id": state[keep],
            "district_id": [
                district_id(s, d)
                for s, d in zip(state[keep], clean.loc[keep, "district_name"], strict=True)
            ],
            "district": clean.loc[keep, "district_name"],
            "crop": crop[keep],
            "season": season[keep].map(str),
            "crop_year": clean.loc[keep, "crop_year"],
            "area": clean.loc[keep, "area"],
            "production": clean.loc[keep, "production"],
        }
    )
    out = out.sort_values(["district_id", "crop", "season", "crop_year"]).reset_index(drop=True)
    report["rows_out"] = len(out)
    return out, report


def build_scope(series: pd.DataFrame) -> dict:
    """Supported combinations, derived from the scoped series. Stored in the model artifact.

    Only what can be estimated is advertised: a series needs at least one year with positive
    reported area and production (that year's successor is then estimable), and a state,
    district, crop or season is listed only if at least one such series uses it.
    """
    positive = series[(series["area"] > 0) & (series["production"] > 0)]
    per_series = positive.groupby(["district_id", "crop", "season"])["crop_year"].agg(
        ["min", "max", "nunique"]
    )
    districts = positive.drop_duplicates("district_id").sort_values("district_id")
    return {
        "firstYear": int(series["crop_year"].min()),
        "lastObservedYear": int(series["crop_year"].max()),
        "states": [
            {"stateId": s, "label": STATE_LABELS[s]} for s in sorted(positive["state_id"].unique())
        ],
        "districts": [
            {"districtId": r.district_id, "stateId": r.state_id, "label": r.district}
            for r in districts.itertuples()
        ],
        "crops": [
            {"cropId": c, "label": CROP_LABELS[c]} for c in sorted(positive["crop"].unique())
        ],
        "seasons": sorted(positive["season"].unique()),
        "supplySeries": [
            {
                "districtId": d,
                "cropId": c,
                "season": s,
                "firstYear": int(r["min"]),
                "lastYear": int(r["max"]),
                "yearsObserved": int(r["nunique"]),
            }
            for (d, c, s), r in per_series.iterrows()
        ],
    }
