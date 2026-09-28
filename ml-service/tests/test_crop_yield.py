"""Schema/preprocessing tests for agri_ml.datasets.crop_yield.

The rows below are SYNTHETIC test fixtures, not observations from the real file. They exercise
code paths only. The tests at the bottom check the real file and are skipped when it is absent.
"""

import importlib.util
import json
import sys
from pathlib import Path

import pandas as pd
import pytest

from agri_ml.datasets import crop_yield as cy

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"

HEADER = ",".join(cy.EXPECTED_COLUMNS)
ROWS = [
    "CropA ,2000,Kharif     ,StateX,100,50,1000.0,9000.0,30.0,0.5",
    "CropB,2001,Whole Year ,StateY,200,400,1100.0,18000.0,60.0,2.0",
]


def write_csv(tmp_path, rows, header=HEADER):
    path = tmp_path / "crop_yield.csv"
    path.write_text("\n".join([header, *rows]) + "\n")
    return path


def test_load_strips_whitespace_and_sets_dtypes(tmp_path):
    df = cy.load(write_csv(tmp_path, ROWS))
    assert df["Crop"].tolist() == ["CropA", "CropB"]
    assert df["Season"].tolist() == ["Kharif", "Whole Year"]
    assert df["Crop_Year"].dtype == "int64"
    assert df["Yield"].dtype == "float64"


def test_load_raw_keeps_values_untouched(tmp_path):
    raw = cy.load_raw(write_csv(tmp_path, ROWS))
    assert raw.loc[0, "Season"] == "Kharif     "


def test_missing_file_names_expected_location(tmp_path):
    with pytest.raises(FileNotFoundError, match="crop_yield.csv"):
        cy.load(tmp_path / "nope.csv")


def test_wrong_columns_rejected(tmp_path):
    bad_header = HEADER.replace("Pesticide", "Pesticides")
    with pytest.raises(cy.SchemaError, match="Pesticide"):
        cy.load(write_csv(tmp_path, ROWS, header=bad_header))


def test_validate_clean_frame(tmp_path):
    report = cy.validate(cy.load(write_csv(tmp_path, ROWS)))
    assert report.row_count == 2
    assert report.problems == []
    assert (report.year_min, report.year_max) == (2000, 2001)
    assert report.seasons == ["Kharif", "Whole Year"]


def test_validate_reports_duplicates_and_negatives(tmp_path):
    rows = [*ROWS, ROWS[0], "CropC,2002,Rabi,StateX,-5,1,1.0,1.0,1.0,1.0"]
    report = cy.validate(cy.load(write_csv(tmp_path, rows)))
    assert report.duplicate_rows == 1
    assert report.duplicate_keys == 1
    assert report.negative_values["Area"] == 1
    assert len(report.problems) == 3


def test_validate_reports_missing_values():
    values = ["A", 2000, "Rabi", "S", 1.0, float("nan"), 1.0, 1.0, 1.0, 1.0]
    df = pd.DataFrame([dict(zip(cy.EXPECTED_COLUMNS, values, strict=True))])
    report = cy.validate(df)
    assert report.missing_values["Production"] == 1
    assert report.problems


# --- quality flags (hand-written fixture rows) -----------------------------------------------


def series_rows(state="S", crop="C", season="Rabi", years=range(2000, 2006),
                area=100.0, prod=200.0):
    return [
        {"Crop": crop, "Crop_Year": y, "Season": season, "State": state, "Area": area,
         "Production": prod, "Annual_Rainfall": 1.0, "Fertilizer": 1.0, "Pesticide": 1.0,
         "Yield": prod / area}
        for y in years
    ]


def frame(rows):
    return pd.DataFrame(rows).astype({"Crop_Year": "int64", "Area": "float64",
                                      "Production": "float64", "Yield": "float64"})


def test_quality_flags_keep_every_row_and_value():
    df = frame(series_rows())
    flagged = cy.quality_flags(df)
    assert len(flagged) == len(df)
    pd.testing.assert_frame_equal(flagged[df.columns], df)
    assert not flagged["flag_any"].any()


def test_flags_zero_production_and_yield_zero_mismatch():
    rows = series_rows()
    rows[0].update(Production=0.0, Yield=0.4)  # zero output but positive yield
    rows[1].update(Production=0.0, Yield=0.0)  # consistent zero
    rows[2].update(Yield=0.0)                  # positive output but zero yield
    f = cy.quality_flags(frame(rows))
    assert f["flag_zero_production"].tolist()[:3] == [True, True, False]
    assert f["flag_yield_zero_mismatch"].tolist()[:3] == [True, False, True]


def test_flags_yield_mismatch_beyond_factor():
    rows = series_rows()
    rows[0]["Yield"] = 2.0 * cy.YIELD_MISMATCH_FACTOR * 1.01  # production/area is 2.0
    rows[1]["Yield"] = 2.0 * 1.5                               # inside tolerance
    f = cy.quality_flags(frame(rows))
    assert f["flag_yield_mismatch"].tolist()[:2] == [True, False]


def test_flags_series_jump_only_for_long_enough_series():
    long = series_rows(years=range(2000, 2006))
    long[0]["Area"] = 100.0 * cy.SERIES_JUMP_FACTOR * 2
    short = series_rows(crop="D", years=range(2000, 2003))
    short[0]["Area"] = 100.0 * cy.SERIES_JUMP_FACTOR * 2
    f = cy.quality_flags(frame(long + short))
    assert f.loc[0, "flag_series_jump"]
    assert f["flag_series_jump"].sum() == 1  # the short series is below SERIES_JUMP_MIN_ROWS


def test_flags_aggregate_crop_and_incomplete_year():
    rows = []
    for state in ["A", "B", "C", "D"]:
        rows += series_rows(state=state, years=range(2000, 2004))
    rows += series_rows(state="A", crop="Oilseeds total", years=[2004])  # only 1 of 4 states
    f = cy.quality_flags(frame(rows))
    assert f["flag_aggregate_crop"].sum() == 1
    assert f.loc[f["Crop_Year"] == 2004, "flag_incomplete_year"].all()
    assert not f.loc[f["Crop_Year"] < 2004, "flag_incomplete_year"].any()


def test_validate_reports_infinite_values(tmp_path):
    df = cy.load(write_csv(tmp_path, ROWS))
    df.loc[0, "Area"] = float("inf")
    assert any("infinite" in p for p in cy.validate(df).problems)


def test_season_label_changes_and_gaps():
    rows = (series_rows(season="Whole Year", years=range(1997, 2001))
            + series_rows(season="Rabi", years=[2001, 2002, 2004]))
    df = frame(rows)
    changes = cy.season_label_changes(df)
    assert changes[["old_season", "old_last", "new_season", "new_first"]].values.tolist() == [
        ["Whole Year", 2000, "Rabi", 2001]
    ]
    gaps = cy.series_year_gaps(df).set_index("Season")
    assert gaps.loc["Rabi", "missing_years"] == 1
    assert gaps.loc["Whole Year", "missing_years"] == 0


# --- prepare script ---------------------------------------------------------------------------


def load_prepare_script():
    path = SCRIPTS / "prepare_crop_yield.py"
    spec = importlib.util.spec_from_file_location("prepare_crop_yield", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_prepare_writes_versioned_output_and_refuses_overwrite(tmp_path, monkeypatch):
    src = write_csv(tmp_path, ROWS)
    out_root = tmp_path / "out"
    prepare = load_prepare_script()
    monkeypatch.setattr(sys, "argv", ["prepare", "--path", str(src), "--out-root", str(out_root)])
    assert prepare.main() == 0
    out_dir = out_root / cy.dataset_version(src) / cy.PREPROCESSING_VERSION
    manifest = json.loads((out_dir / "manifest.json").read_text())
    assert manifest["rows"] == {"raw": 2, "clean": 2, "dropped": 0}
    written = pd.read_csv(out_dir / "crop_yield_clean.csv")
    assert written["Season"].tolist() == ["Kharif", "Whole Year"]
    assert "flag_any" in written.columns
    assert prepare.main() == 1  # same input + version: not overwritten


def test_prepare_fails_on_integrity_problems(tmp_path, monkeypatch):
    src = write_csv(tmp_path, [*ROWS, ROWS[0]])  # duplicate key
    prepare = load_prepare_script()
    argv = ["prepare", "--path", str(src), "--out-root", str(tmp_path / "o")]
    monkeypatch.setattr(sys, "argv", argv)
    assert prepare.main() == 1
    assert not (tmp_path / "o").exists()


# --- the real file (skipped when it is not present locally) -----------------------------------

REAL = cy.default_path()
real_only = pytest.mark.skipif(not REAL.is_file(), reason=f"real crop_yield.csv not at {REAL}")


@pytest.fixture(scope="module")
def real_df():
    return cy.load(REAL)


@real_only
def test_real_file_identity_and_shape(real_df):
    assert cy.dataset_version(REAL) == "crop_yield-sha256-ab9bc356b1f8"
    assert real_df.shape == (19689, 10)
    assert real_df.columns.tolist() == cy.EXPECTED_COLUMNS


@real_only
def test_real_file_passes_validation(real_df):
    report = cy.validate(real_df)
    assert report.problems == []
    assert (report.year_min, report.year_max) == (1997, 2020)
    assert (report.n_states, report.n_crops, report.n_seasons) == (30, 55, 6)
    assert report.duplicate_rows == 0 and report.duplicate_keys == 0


@real_only
def test_real_file_known_findings(real_df):
    # 2020 is covered by a single state, so it is not a usable test year.
    assert real_df.loc[real_df["Crop_Year"] == 2020, "State"].unique().tolist() == ["Uttarakhand"]
    # Fertilizer / Area is one value per year for all states and crops (derived, not measured).
    rate = (real_df["Fertilizer"] / real_df["Area"]).round(4)
    per_year = rate.groupby(real_df["Crop_Year"]).nunique()
    assert (per_year == 1).all()
    # UP potato switches season label from Whole Year to Rabi in 2004.
    up = cy.season_label_changes(real_df)
    potato = up[(up["State"] == "Uttar Pradesh") & (up["Crop"] == "Potato")]
    assert potato[["old_season", "old_last", "new_season", "new_first"]].values.tolist() == [
        ["Whole Year", 2003, "Rabi", 2004]
    ]


# --- supply-model view ------------------------------------------------------------------------


def test_to_supply_frame_maps_columns_and_applies_exclusions():
    rows = []
    for state in ["A", "B", "C", "D"]:
        rows += series_rows(state=state, years=range(2000, 2004))
    rows += series_rows(state="A", crop="Coconut", years=range(2000, 2004))
    rows += series_rows(state="A", crop="Oilseeds total", years=range(2000, 2004))
    rows += series_rows(state="A", years=[2004])  # year covered by 1 of 4 states
    out, report = cy.to_supply_frame(frame(rows))
    assert out.columns.tolist() == ["state_name", "crop", "season", "crop_year", "area",
                                    "production"]
    assert set(out["crop"]) == {"C"}
    assert out["crop_year"].max() == 2003
    assert report["excluded_crop_unit"] == 4
    assert report["flag_aggregate_crop"] == 4
    assert report["flag_incomplete_year"] == 1
    assert report["supply_rows"] == 16


def test_to_supply_frame_does_not_alter_values():
    df = frame(series_rows())
    out, _ = cy.to_supply_frame(df)
    assert out["area"].tolist() == df["Area"].tolist()
    assert out["production"].tolist() == df["Production"].tolist()


@real_only
def test_real_supply_frame_counts(real_df):
    out, report = cy.to_supply_frame(real_df)
    assert report["excluded_total"] == 1194
    assert len(out) == 19689 - 1194
    assert out["crop_year"].max() == 2019
    assert not out.duplicated(["state_name", "crop", "season", "crop_year"]).any()
