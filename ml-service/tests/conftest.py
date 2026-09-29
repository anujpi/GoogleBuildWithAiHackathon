"""Shared fixtures. Every row generated here is SYNTHETIC test data in the raw S01 shape.

It exercises code paths only and says nothing about model quality (see docs/model-cards/supply.md).
"""

import numpy as np
import pandas as pd
import pytest

from agri_ml.datasets.crop_production import clean
from agri_ml.reference import to_scope
from agri_ml.training.supply import Split, save_artifact, train_and_evaluate

SPLIT = Split(train_end=2010, val_start=2011, val_end=2012, test_start=2013, test_end=2014)
SYNTHETIC = {"data_source": "TEST_FIXTURE", "data_classification": "SYNTHETIC"}
DISTRICTS = ["AGRA", "MATHURA", "KANPUR NAGAR", "LUCKNOW", "VARANASI", "MEERUT"]


def synthetic_raw() -> pd.DataFrame:
    """Raw-style rows (capitalised headers, padded seasons, string values), 1997-2014.

    Gaps on purpose: MEERUT has no wheat (unknown series) and VARANASI potato misses 2009.
    """
    rng = np.random.default_rng(0)
    rows = []
    for district in DISTRICTS:
        for crop, base_yield in [("Potato", 25.0), ("Wheat", 3.0)]:
            if district == "MEERUT" and crop == "Wheat":
                continue
            area = rng.uniform(5_000, 20_000)
            for year in range(1997, 2015):
                if district == "VARANASI" and crop == "Potato" and year == 2009:
                    continue
                a = area * rng.uniform(0.9, 1.1)
                rows.append(
                    {
                        "State_Name": "Uttar Pradesh",
                        "District_Name": district,
                        "Crop_Year": str(year),
                        "Season": "Rabi       ",
                        "Crop": crop,
                        "Area": f"{a:.1f}",
                        "Production": f"{a * base_yield * rng.uniform(0.8, 1.2):.1f}",
                    }
                )
    return pd.DataFrame(rows)


def synthetic_series() -> pd.DataFrame:
    series, _ = to_scope(clean(synthetic_raw())[0])
    return series


@pytest.fixture(scope="session")
def series() -> pd.DataFrame:
    return synthetic_series()


@pytest.fixture(scope="session")
def model_dir(tmp_path_factory, series):
    result = train_and_evaluate(
        series,
        SPLIT,
        dataset_version="synthetic-test",
        param_grid=[{"max_depth": 2, "min_child_weight": 1}],
        **SYNTHETIC,
    )
    return save_artifact(
        result, tmp_path_factory.mktemp("art") / "supply-test", "supply-test", series
    )
