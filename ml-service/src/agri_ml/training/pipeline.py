"""The supply training pipeline, stage by stage (MASTER_SPEC §9.6):

manifest check -> load -> validate + normalise + clean -> scope -> features/split/baselines/model/
evaluation/selection (training.supply) -> artifact -> error analysis -> evaluation report.

scripts/train_supply.py runs it on the real S01 download and logs to MLflow. Tests run it with
`synthetic_fixture=True`, which skips the manifest check and labels everything SYNTHETIC, so a
fixture can never produce an artifact that claims to be S01.
"""

import json
from dataclasses import dataclass
from pathlib import Path

from agri_ml.datasets.crop_production import clean, dataset_version, load_raw, validate_manifest
from agri_ml.evaluation.supply_report import render_report
from agri_ml.reference import CROPS, to_scope
from agri_ml.training.supply import Split, error_analysis, save_artifact, train_and_evaluate

# S01 is complete up to 2014; 2015 has only ~2% of a normal year's rows (partial): excluded.
LAST_COMPLETE_YEAR = 2014
SPLIT = Split(train_end=2010, val_start=2011, val_end=2012, test_start=2013, test_end=2014)


@dataclass
class PipelineResult:
    artifact_dir: Path
    metadata: dict


def run_supply_pipeline(
    raw_dir: Path,
    artifacts_dir: Path,
    model_version: str,
    seed: int = 42,
    *,
    synthetic_fixture: bool = False,
    required_crops: tuple[str, ...] = tuple(CROPS.values()),
    param_grid: list[dict] | None = None,
) -> PipelineResult:
    """Build `artifacts_dir/supply/<model_version>/` from the raw S01 files in `raw_dir`.

    Fails (and writes nothing) if the download is incomplete or unregistered, or if any required
    crop has no in-scope rows. It never overwrites an existing artifact version.
    """
    manifest = None if synthetic_fixture else validate_manifest(raw_dir)
    cleaned, clean_report = clean(load_raw(raw_dir))
    series, scope_report = to_scope(cleaned)
    series = series[series["crop_year"] <= LAST_COMPLETE_YEAR].reset_index(drop=True)
    scope_report["rows_after_year_filter"] = len(series)
    missing = sorted(set(required_crops) - set(series["crop"]))
    if missing:
        raise ValueError(f"no in-scope rows for crops {missing}; check the download and cleaning "
                         f"report {clean_report}")

    result = train_and_evaluate(
        series, SPLIT, seed=seed, param_grid=param_grid,
        dataset_version=dataset_version(raw_dir, "synthetic" if synthetic_fixture else "s01"),
        **({"data_source": "TEST_FIXTURE", "data_classification": "SYNTHETIC"}
           if synthetic_fixture else {}),
    )
    selection = result.metadata["selection"]
    analysis = error_analysis(result.scored, selection["servedMethod"], selection["bestBaseline"])

    out_dir = artifacts_dir / "supply" / model_version
    save_artifact(result, out_dir, model_version, series,
                  extra={"cleaningReport": clean_report, "scopeReport": scope_report,
                         "sourceManifest": manifest})
    (out_dir / "error_analysis.json").write_text(json.dumps(analysis, indent=2, default=str))
    result.scored.to_csv(out_dir / "scored.csv", index=False)
    metadata = json.loads((out_dir / "metadata.json").read_text())
    (out_dir / "evaluation_report.md").write_text(render_report(metadata, analysis),
                                                  encoding="utf-8")
    return PipelineResult(artifact_dir=out_dir, metadata=metadata)
