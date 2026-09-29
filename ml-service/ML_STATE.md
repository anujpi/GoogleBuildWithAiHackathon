# ML_STATE

Last updated: 2026-09-29. Branch: `anuj-ml-integration` (uncommitted working tree).
The source of truth is **`MASTER_SPEC.md`** (repo root): scope D2, the ML contract §9, and ML
phase **P1** in §19. The implementation is described in `docs/ml-contracts/`.

## Completion states

| # | State | Status |
|---|---|---|
| 1 | Code ready | ✅ |
| 2 | Pipeline ready (download → manifest gate → clean → scope → train → evaluate → select → artifact → report) | ✅ Verified end to end on SYNTHETIC fixtures |
| 3 | Model trained on S01 | ⛔ Blocked by B1 |
| 4 | Model evaluated (real metrics) | ⛔ Blocked by B1 |
| 5 | Real artifact generated | ⛔ Blocked by B1 |
| 6 | Real artifact servable | ⛔ Blocked by B1. Serving is verified with pipeline-built SYNTHETIC artifacts |
| 7 | Backend integrated (contract, error mapping, contract rules) | ✅ Mock-HTTP golden tests, plus live HTTP for health and errors |
| 8 | End to end verified with a real artifact | ⛔ Blocked by B1 |

**B1:** the download needs a registered data.gov.in key in `DATA_GOV_IN_API_KEY`. On 2026-09-29
the S01 API was reachable again: it answered HTTP 400 "key required" without a key, where it had
answered 503/429 the day before. No key is available to this environment.

## Test status (2026-09-29)

- **ML:** 94 passed, 1 skipped (the real-artifact test), `ruff check` clean. A clean install from
  `requirements.lock` on Windows / Python 3.14.7 passes `pip check`, the tests and lint.
- **Backend:** 85 tests, 0 failures, 3 skipped (the live tests).
- **Live, uvicorn + `MlServiceLiveTest`** against a pipeline-built SYNTHETIC artifact:
  - health ✔
  - unsupported crop ✔
  - supply estimate **correctly rejected** (`ML_INVALID_RESPONSE`: history not OBSERVED)

## Finishing P1 once the key exists

See README "Data" and "Train and evaluate". Then copy the generated `evaluation_report.md` tables
into `docs/model-cards/supply.md`.

## Deviations from MASTER_SPEC (need a spec edit)

- **`AREA_OUT_OF_RANGE`** (422): an extra code that refuses to extrapolate beyond the series'
  area range. The backend maps it to `UNSUPPORTED_INPUT`.
- **`AREA_UNAVAILABLE`** (§9.3) is unreachable. The `cropYear-1` row that is required always has
  an area, so the case surfaces as `INSUFFICIENT_HISTORY`.

## Removed

These were removed because they were unused, unverified or obsolete:
- the `crop_yield.csv` loader, audit script, tests and doc
- `check_mlflow.py`
- `docs/dataset-catalogue.md` (the Maharashtra scope, which conflicts with D2)
- `docs/PROGRESS_REPORT.md`
- the hard-coded public sample key in the download script

`ML_TEAM_PLAN.md` and `docs/ML_AUDIT_2026-09-28.md` are kept for history and marked as superseded.
