"""Schema/preprocessing tests for agri_ml.datasets.crop_yield.

The rows below are SYNTHETIC test fixtures, not observations from the real file.
"""

import pandas as pd
import pytest

from agri_ml.datasets import crop_yield as cy

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
    df = pd.DataFrame([dict(zip(cy.EXPECTED_COLUMNS, ["A", 2000, "Rabi", "S", 1.0, float("nan"), 1.0,
                                                      1.0, 1.0, 1.0], strict=True))])
    report = cy.validate(df)
    assert report.missing_values["Production"] == 1
    assert report.problems
