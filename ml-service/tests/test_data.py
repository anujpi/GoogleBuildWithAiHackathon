"""Preprocessing and scoping, tested on in-memory frames (no file loading), and the manifest."""

import json

import pandas as pd
import pytest

from agri_ml.datasets.crop_production import (
    S01_RESOURCE_ID,
    clean,
    normalise_columns,
    validate_manifest,
    validate_raw,
)
from agri_ml.reference import district_id, to_scope
from agri_ml.schemas.common import Season

RAW = {
    "State_Name": ["Uttar Pradesh"] * 5,
    "District_Name": ["AGRA", "AGRA", "AGRA", "MATHURA", "MATHURA"],
    "Crop_Year": ["2000", "2000", "2001", "2000", "2001"],
    "Season": ["Rabi       ", "Rabi       ", "Rabi", "Rabi", "Rabi"],
    "Crop": ["Potato"] * 5,
    "Area": ["100", "100", "0", "50", "60"],
    "Production": ["2000", "2000", "10", "", "900"],
}


def test_clean_accepts_raw_capitalised_headers():
    out, report = clean(pd.DataFrame(RAW))
    assert report["dropped_exact_duplicates"] == 1
    assert report["dropped_missing_or_nonpositive_area"] == 1
    assert report["dropped_missing_production"] == 1
    assert set(out["season"]) == {"Rabi"}
    assert list(out.columns) == [
        "state_name",
        "district_name",
        "crop_year",
        "season",
        "crop",
        "area",
        "production",
    ]
    assert len(out) == 2


def test_clean_accepts_api_style_headers():
    api = {k.lower(): v for k, v in RAW.items()}
    api["area_"], api["production_"] = api.pop("area"), api.pop("production")
    assert clean(pd.DataFrame(api))[0].equals(clean(pd.DataFrame(RAW))[0])


def test_normalise_columns_handles_whitespace_case_and_underscores():
    assert list(normalise_columns(pd.DataFrame(columns=[" Area_ ", "State_Name"])).columns) == [
        "area",
        "state_name",
    ]


def test_validate_raw_rejects_missing_columns():
    with pytest.raises(ValueError, match="production"):
        validate_raw(pd.DataFrame({k: v for k, v in RAW.items() if k != "Production"}))


def row(**overrides):
    base = {
        "State_Name": "Uttar Pradesh",
        "District_Name": "AGRA",
        "Crop_Year": "2000",
        "Season": "Rabi",
        "Crop": "Potato",
        "Area": "100",
        "Production": "2500",
    }
    return {**base, **overrides}


@pytest.mark.parametrize(
    ("bad", "step"),
    [
        (row(Crop_Year="abc"), "dropped_missing_key"),
        (row(District_Name="  "), "dropped_missing_key"),
        (row(Area="NA"), "dropped_missing_or_nonpositive_area"),
        (row(Area="-3"), "dropped_missing_or_nonpositive_area"),
        (row(Production="n/a"), "dropped_missing_production"),
        (row(Production="-1"), "dropped_negative_production"),
        (row(Production="2500000"), "dropped_implausible_yield"),  # 25,000 t/ha: a unit error
    ],
)
def test_clean_drops_invalid_rows(bad, step):
    good = [row(District_Name=d) for d in ("MATHURA", "KANPUR", "MEERUT")]
    out, report = clean(pd.DataFrame([*good, bad]))
    assert report[step] == 1
    assert len(out) == 3


def test_clean_keeps_zero_production_as_reported_zero():
    out, _ = clean(pd.DataFrame([row(), row(District_Name="MATHURA", Production="0")]))
    assert (out["production"] == 0).sum() == 1


def test_clean_drops_every_copy_of_a_conflicting_key():
    out, report = clean(pd.DataFrame([row(), row(Production="2600"), row(District_Name="MATHURA")]))
    assert report["dropped_conflicting_key_duplicates"] == 2
    assert out["district_name"].tolist() == ["Mathura"]


def test_clean_is_independent_of_row_order():
    df = pd.DataFrame(RAW)
    assert clean(df)[0].equals(clean(df.iloc[::-1])[0])


def test_to_scope_maps_ids_and_drops_out_of_scope():
    rows = [
        row(),
        row(Crop="Tomato"),
        row(State_Name="Karnataka"),
        row(Season="Monsoon"),
        row(District_Name="KANPUR NAGAR", Season="Whole Year"),
    ]
    series, report = to_scope(clean(pd.DataFrame(rows))[0])
    assert report["dropped_out_of_scope_state"] == 1
    assert report["dropped_out_of_scope_crop"] == 1
    assert report["dropped_out_of_scope_season"] == 1
    assert series[["district_id", "crop", "season"]].values.tolist() == [
        ["up-agra", "potato", Season.RABI.value],
        ["up-kanpur-nagar", "potato", Season.WHOLE_YEAR.value],
    ]


def test_district_id_is_a_contract_slug():
    assert district_id("up", "Sant Kabeer Nagar") == "up-sant-kabeer-nagar"


@pytest.mark.parametrize("year", ["1850", "2200", "2000.5"])
def test_clean_drops_impossible_crop_years(year):
    good = [row(District_Name=d) for d in ("MATHURA", "KANPUR", "MEERUT")]
    out, report = clean(pd.DataFrame([*good, row(Crop_Year=year)]))
    assert report["dropped_invalid_year"] == 1
    assert len(out) == 3


# --- manifest ------------------------------------------------------------------------------


def write_download(tmp_path, **manifest_overrides):
    (tmp_path / "up_potato.csv").write_text("state_name,district_name\n")
    manifest = {
        "resourceId": S01_RESOURCE_ID,
        "keyType": "REGISTERED",
        "files": {"up_potato.csv": {"rows": 10, "apiTotal": 10}},
        **manifest_overrides,
    }
    (tmp_path / "manifest.json").write_text(json.dumps(manifest))
    return tmp_path


def test_manifest_of_a_complete_registered_download_is_accepted(tmp_path):
    assert validate_manifest(write_download(tmp_path))["resourceId"] == S01_RESOURCE_ID


@pytest.mark.parametrize(
    ("overrides", "problem"),
    [
        ({"resourceId": "other"}, "not S01"),
        ({"keyType": "SAMPLE"}, "registered API key"),
        ({"files": {"up_potato.csv": {"rows": 9, "apiTotal": 10}}}, "9 rows of 10"),
        ({"files": {"gone.csv": {"rows": 1, "apiTotal": 1}}}, "missing"),
        ({"files": {}}, "no files"),
    ],
)
def test_unusable_downloads_are_rejected(tmp_path, overrides, problem):
    with pytest.raises(ValueError, match=problem):
        validate_manifest(write_download(tmp_path, **overrides))


def test_unlisted_csv_is_rejected(tmp_path):
    write_download(tmp_path)
    (tmp_path / "extra.csv").write_text("x\n")
    with pytest.raises(ValueError, match="extra.csv"):
        validate_manifest(tmp_path)


def test_missing_manifest_names_the_download_script(tmp_path):
    with pytest.raises(FileNotFoundError, match="download_s01_crop_production"):
        validate_manifest(tmp_path)
