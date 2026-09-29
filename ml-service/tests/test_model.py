"""Features, baselines, training, selection and the artifact (SYNTHETIC data, see conftest)."""

import json

import numpy as np
import pandas as pd
import pytest

from agri_ml.features.supply import (
    CATEGORICAL_FEATURES,
    FEATURE_VERSION,
    NUMERIC_FEATURES,
    build_features,
)
from agri_ml.inference.supply import ModelLoadError, SupplyModel
from agri_ml.models.supply import BASELINES, predict_production
from agri_ml.training.supply import (
    SERIES_KEYS,
    regression_metrics,
    save_artifact,
    select_served_method,
    train_and_evaluate,
)
from conftest import SPLIT, SYNTHETIC, synthetic_series

GRID = [{"max_depth": 2, "min_child_weight": 1}]

# --- features ------------------------------------------------------------------------------


def test_features_are_deterministic_and_complete(series):
    a, b = build_features(series, SERIES_KEYS), build_features(series, SERIES_KEYS)
    pd.testing.assert_frame_equal(a, b)
    assert set(NUMERIC_FEATURES) <= set(a.columns)
    assert FEATURE_VERSION == "supply-features-v2"


def test_feature_names_are_exactly_the_documented_ones():
    assert NUMERIC_FEATURES == [
        "log_area",
        "log_area_ratio_lag1",
        "log_yield_mean3",
        "log_yield_rel_lag1",
        "log_yield_rel_lag2",
        "log_yield_rel_lag3",
        "n_history_years",
    ]
    assert CATEGORICAL_FEATURES == ["crop", "season"]


def test_features_use_only_previous_years(series):
    base = build_features(series, SERIES_KEYS)
    changed = series.copy()
    changed.loc[changed["crop_year"] == 2012, "production"] *= 10
    after = build_features(changed, SERIES_KEYS)
    cols = ["log_yield_mean3", "log_yield_rel_lag1", "log_area_ratio_lag1"]
    # Changing 2012 production must not affect 2012 features, only later years.
    pd.testing.assert_frame_equal(
        base.loc[base.crop_year <= 2012, cols], after.loc[after.crop_year <= 2012, cols]
    )
    later_base, later_after = (
        base.loc[base.crop_year == 2013, cols],
        after.loc[after.crop_year == 2013, cols],
    )
    assert not later_base.equals(later_after)


def test_missing_previous_year_is_not_filled_from_older_years(series):
    f = build_features(series, SERIES_KEYS)
    gap = f[(f.district_id == "up-varanasi") & (f.crop == "potato") & (f.crop_year == 2010)]
    assert gap["yield_lag1"].isna().all()
    assert gap["n_history_years"].iloc[0] == 2


# --- baselines and metrics -----------------------------------------------------------------


def test_baselines_compute_expected_values():
    f = pd.DataFrame(
        {"area": [10.0], "area_lag1": [8.0], "yield_lag1": [2.0], "yield_mean3": [3.0]}
    )
    got = {m: predict_production(m, f).iloc[0] for m in BASELINES}
    assert got == {
        "NAIVE_LAST_YEAR_PRODUCTION": 16.0,
        "AREA_X_LAST_YEAR_YIELD": 20.0,
        "AREA_X_MEAN_YIELD_3Y": 30.0,
    }


def test_regression_metrics_known_values():
    m = regression_metrics(pd.Series([10.0, 0.0, 30.0]), pd.Series([12.0, 1.0, 27.0]))
    assert m["mae"] == pytest.approx(2.0)
    assert m["rmse"] == pytest.approx(np.sqrt(14 / 3))
    assert m["wape"] == pytest.approx(6 / 40)
    assert m["mape_nonzero"] == pytest.approx((0.2 + 0.1) / 2)
    assert m["n_zero_actual"] == 1


# --- training and selection ----------------------------------------------------------------


@pytest.fixture(scope="module")
def result(series):
    return train_and_evaluate(
        series, SPLIT, dataset_version="synthetic-test", param_grid=GRID, **SYNTHETIC
    )


def test_training_data_provenance_is_recorded(result, series):
    assert result.metadata["data"] == {"source": "TEST_FIXTURE", "classification": "SYNTHETIC"}
    # Real training (scripts/train_supply.py) relies on the S01 defaults.
    default = train_and_evaluate(series, SPLIT, dataset_version="x", param_grid=GRID)
    assert default.metadata["data"] == {
        "source": "DES_S01_DATA_GOV_IN",
        "classification": "OBSERVED",
    }


def test_selection_follows_the_acceptance_rule(result):
    sel = result.metadata["selection"]
    val, test, best = sel["validationWape"], sel["testWape"], sel["bestBaseline"]
    assert best == min(BASELINES, key=val.get)
    accepted = val["MODEL"] <= 0.95 * val[best] and test["MODEL"] <= test[best]
    assert sel["servedMethod"] == ("MODEL" if accepted else best)
    # Reported WAPEs are the measured metrics, not separately computed numbers.
    assert test == {m: result.metadata["metrics"]["test"][m]["wape"] for m in test}


def test_interval_coverage_is_measured_on_test(result):
    interval = result.metadata["interval"]
    q_lo, q_hi = interval["logResidualQuantiles"]
    assert q_lo < 0 < q_hi
    assert 0.0 <= interval["testEmpiricalCoverage"] <= 1.0
    assert interval["appliesTo"] == result.metadata["selection"]["servedMethod"]


def test_metadata_periods_scope_and_spatial_check(result):
    m = result.metadata
    assert (m["trainingPeriod"], m["validationPeriod"], m["testPeriod"]) == (
        "1998-2010",
        "2011-2012",
        "2013-2014",
    )
    assert m["featureVersion"] == FEATURE_VERSION
    assert m["scope"]["lastObservedYear"] == 2014
    assert {c["cropId"] for c in m["scope"]["crops"]} == {"potato", "wheat"}
    assert len(m["spatialEvaluation"]["folds"]) == 5


def test_training_is_reproducible(series, result):
    again = train_and_evaluate(
        series, SPLIT, dataset_version="synthetic-test", param_grid=GRID, **SYNTHETIC
    )
    assert again.metadata["metrics"] == result.metadata["metrics"]
    assert again.metadata["interval"] == result.metadata["interval"]


def test_chronological_split_is_enforced(series):
    with pytest.raises(ValueError):
        train_and_evaluate(series, type(SPLIT)(2012, 2011, 2012, 2013, 2014), "x", param_grid=GRID)


# --- artifact ------------------------------------------------------------------------------


def test_artifact_is_self_contained_and_loads(model_dir):
    names = {p.name for p in model_dir.iterdir()}
    assert {"model.json", "metadata.json", "series.parquet"} <= names
    meta = json.loads((model_dir / "metadata.json").read_text())
    for key in (
        "modelVersion",
        "featureVersion",
        "datasetVersion",
        "trainedAt",
        "trainingPeriod",
        "validationPeriod",
        "testPeriod",
        "metrics",
        "selection",
        "interval",
        "scope",
        "dataThrough",
        "servedMethod",
    ):
        assert meta[key] is not None
    assert meta["dataThrough"] == "2014"
    assert meta["servedMethod"] == meta["selection"]["servedMethod"]
    model = SupplyModel.load(model_dir)
    assert model.version == "supply-test"


def test_artifact_is_never_overwritten(model_dir, result):
    with pytest.raises(FileExistsError):
        save_artifact(result, model_dir, "supply-test", synthetic_series())


def test_missing_artifact_is_model_not_loaded(tmp_path):
    with pytest.raises(ModelLoadError) as exc:
        SupplyModel.load(tmp_path / "nope")
    assert exc.value.code == "MODEL_NOT_LOADED"


def test_corrupt_artifact_is_artifact_load_error(tmp_path, model_dir):
    bad = tmp_path / "bad"
    bad.mkdir()
    for name in ("metadata.json", "series.parquet"):
        (bad / name).write_bytes((model_dir / name).read_bytes())
    (bad / "model.json").write_text("not a model")
    with pytest.raises(ModelLoadError) as exc:
        SupplyModel.load(bad)
    assert exc.value.code == "ARTIFACT_LOAD_ERROR"


# --- evaluation population and acceptance rule ---------------------------------------------


def test_every_method_is_scored_on_the_same_rows(result):
    for part, by_method in result.metadata["metrics"].items():
        counts = {m: v["n"] for m, v in by_method.items()}
        assert len(set(counts.values())) == 1, (part, counts)
    preds = result.scored[[c for c in result.scored.columns if c.startswith("pred_")]]
    assert preds.notna().all().all()


BASE = {"NAIVE_LAST_YEAR_PRODUCTION": 0.30, "AREA_X_LAST_YEAR_YIELD": 0.25,
        "AREA_X_MEAN_YIELD_3Y": 0.20}


@pytest.mark.parametrize(
    ("model_val", "model_test", "served"),
    [
        (0.18, 0.19, "MODEL"),                  # 10% better on validation, better on test
        (0.95 * 0.20, 0.20, "MODEL"),           # exactly 5% better, equal on test
        (0.1901, 0.10, "AREA_X_MEAN_YIELD_3Y"),  # just under 5% better on validation
        (0.10, 0.2001, "AREA_X_MEAN_YIELD_3Y"),  # worse than the baseline on test
    ],
)
def test_acceptance_rule(model_val, model_test, served):
    val = {**BASE, "MODEL": model_val}
    test = {**BASE, "MODEL": model_test}
    assert select_served_method(val, test) == (served, "AREA_X_MEAN_YIELD_3Y")


def test_metadata_records_known_limitations(result):
    assert any("ends in crop year 2014" in n for n in result.metadata["limitations"])


def test_scope_advertises_only_estimable_combinations(series):
    zero = series[series["district_id"] == "up-agra"].assign(production=0.0)
    other = series[series["district_id"] != "up-agra"]
    m = train_and_evaluate(
        pd.concat([other, zero]), SPLIT, dataset_version="x", param_grid=GRID, **SYNTHETIC
    ).metadata
    assert "up-agra" not in {d["districtId"] for d in m["scope"]["districts"]}
    assert "up-agra" not in {s["districtId"] for s in m["scope"]["supplySeries"]}
