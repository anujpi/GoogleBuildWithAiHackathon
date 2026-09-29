"""Render the supply evaluation report from artifact metadata, so its numbers cannot drift."""

METRICS = ("n", "mae", "rmse", "wape", "mape_nonzero")


def _table(header: list[str], rows: list[list]) -> str:
    def fmt(v):
        return f"{v:.4f}" if isinstance(v, float) else str(v)

    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    lines += ["| " + " | ".join(fmt(v) for v in r) + " |" for r in rows]
    return "\n".join(lines)


def render_report(meta: dict, analysis: dict) -> str:
    sel, interval, scope = meta["selection"], meta["interval"], meta["scope"]
    spatial, rows = meta["spatialEvaluation"], meta["rows"]
    metrics = _table(
        ["split", "method", "n", "MAE", "RMSE", "WAPE", "MAPE (actual>0)"],
        [
            [part, m, *(meta["metrics"][part][m][k] for k in METRICS)]
            for part in ("validation", "test")
            for m in meta["metrics"]["test"]
        ],
    )
    spatial_table = _table(
        ["fold", "districts", "n", "WAPE model", f"WAPE {spatial['baseline']}"],
        [
            [f["fold"], f["districts"], f["n"], f["wapeModel"], f["wapeBaseline"]]
            for f in spatial["folds"]
        ],
    )

    def breakdown(key: str) -> str:
        return _table(
            [key, "n", "WAPE served", "WAPE baseline"],
            [
                [r[key], r["n"], r["wape_served"], r["wape_baseline"]]
                for r in analysis["byCrop" if key == "crop" else "bySeason"]
            ],
        )

    states = ", ".join(s["label"] for s in scope["states"])
    crops = ", ".join(c["cropId"] for c in scope["crops"])
    features = ", ".join(meta["features"]["numeric"] + meta["features"]["categorical"])
    worst = ", ".join(r["district_id"] for r in analysis["worstDistrictsByServedWape"])
    return f"""# Supply model evaluation: {meta["modelVersion"]}

Generated from `metadata.json` and `error_analysis.json` of this artifact. Do not edit by hand.

## Data
- Dataset: data.gov.in S01 district-wise season-wise crop production, `{meta["datasetVersion"]}`
- Geography: {states}; {len(scope["districts"])} districts
- Crops: {crops}; seasons: {", ".join(scope["seasons"])}
- Crop years in data: {scope["firstYear"]}-{scope["lastObservedYear"]}
- Eligible rows (last year's reported area and positive yield exist):
  train {rows["train"]}, validation {rows["validation"]}, test {rows["test"]}

## Periods
- Training: {meta["trainingPeriod"]}; validation: {meta["validationPeriod"]}; \
test: {meta["testPeriod"]}

## Model
- {meta["modelType"]}; feature version `{meta["featureVersion"]}`
- Features: {features}. No weather, soil, market or location feature.
- Hyperparameters: `{meta["hyperparameters"]}`

## Metrics (production, tonnes; WAPE is primary)
{metrics}

## Selection
- Rule: {sel["rule"]}
- Best baseline (validation): **{sel["bestBaseline"]}**
- Served method: **{sel["servedMethod"]}** (`{meta["servedModelName"]}`)

## Interval
- {interval["method"]} for {interval["appliesTo"]}; nominal {interval["nominalCoverage"]:.2f},
  measured test coverage **{interval["testEmpiricalCoverage"]:.4f}**
- One width for every series (not conditional on crop, district or history length).

## Spatial check ({spatial["method"]})
{spatial_table}

## Error analysis (test period, served vs best baseline)
{breakdown("crop")}

{breakdown("season")}

Worst districts by served WAPE: {worst}

## What this model has not learned
- Weather, soil, irrigation, prices or any signal after {scope["lastObservedYear"]}.
- Anything about districts, crops or seasons outside the scope above.
- Farm-level yields: it is fitted to district totals.
"""
