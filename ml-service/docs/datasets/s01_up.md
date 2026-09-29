# Dataset: S01, district-wise season-wise crop production (Uttar Pradesh scope)

The only dataset used by the supply model. Loader and cleaning: `src/agri_ml/datasets/crop_production.py`.
Scope mapping: `src/agri_ml/reference.py`. Download: `scripts/download_s01_crop_production.py`.
General source audit: `DATASET_CATALOGUE.md` (S01).

| Field | Value |
|---|---|
| Dataset name | District-wise, season-wise crop production statistics |
| Source (publisher) | Ministry of Agriculture & Farmers Welfare, Directorate of Economics & Statistics, via data.gov.in |
| URL / identifier | https://www.data.gov.in/resource/district-wise-season-wise-crop-production-statistics-1997. API resource `35be999b-0208-4354-b557-f6ca9a5355de`. The resource page names its source file as `https://aps.dac.gov.in/APY/apy.csv` (timed out on 2026-09-28) |
| Access method | data.gov.in REST API, filtered by state and crop. It needs a **registered** key in `DATA_GOV_IN_API_KEY`. The script uses no shared or sample key, and training refuses a manifest without `keyType: REGISTERED` |
| Access date | **Not downloaded yet.** On 2026-09-28 the API answered 503/429 to every probe, which used the public sample key; no registered key is available yet. The manifest written by the download script records the real access time |
| License / usage terms | Government Open Data License – India (GODL), with attribution |
| Data classification | OBSERVED (reported official statistics). Yield = production / area is derived from OBSERVED values |
| Geographic coverage (used) | Uttar Pradesh, every district present in the data |
| Crops (used) | Potato, Wheat, Onion, Maize (contract ids `potato`, `wheat`, `onion`, `maize`) |
| Spatial granularity | District (as reported; renamed or split districts are separate series) |
| Temporal coverage | Crop years 1997–2014. 2015 is partial (about 2% of a normal year's rows), so training drops it. No rows after 2015 |
| Temporal granularity | Crop year × season |
| Update frequency | None in practice (resource last updated 2021-07-13) |
| Version | `s01-<first 12 hex of sha256 over the raw CSV names and bytes>` (`crop_production.dataset_version`) |

Expected row counts for UP come from the API `total` field in the 2026-09-24 source audit. They
have not been checked against a local download yet:

| Crop | Potato | Wheat | Onion | Maize | Tomato |
|---|---|---|---|---|---|
| Rows | 1,276 | 1,275 | 1,736 | 2,552 | **0** (so tomato can't be supported) |

## What one row represents

One district × crop × season × crop year, with the reported area (ha) and production (t). After
cleaning, that key is unique.

## Target

Production (tonnes) of crop year t. Yield (t/ha) is derived from it.

## Fields

| Column (after normalisation) | Meaning | Unit | Type |
|---|---|---|---|
| state_name, district_name | Location as reported | — | text |
| crop_year | S01 crop-year label | year | int |
| season | Kharif / Rabi / Whole Year / Summer / Autumn / Winter (as present) | — | text |
| crop | Crop name | — | text |
| area | Cultivated area | hectares | float |
| production | Production | tonnes, for the four crops in scope (coconut and cotton, which use other units, are out of scope) | float |

## Missing values and data quality

These are handled by `clean()`, which counts each drop in the cleaning report stored in the
artifact's `metadata.json`:
- blank or unparseable numbers become NaN
- exact duplicates are dropped
- rows with a missing key are dropped
- rows with area ≤ 0 or missing are dropped
- rows with missing production are dropped (blank means missing, not zero)
- rows with negative production are dropped
- rows whose yield is more than 10× away from the crop's median yield are dropped as unit or entry errors
- keys with conflicting duplicates are dropped entirely

Reported zero production is kept as a reported zero.

## Known biases and gaps

- **Ends in 2014.** The newest possible estimate is crop year 2015. Nothing here describes current seasons.
- District renames and splits break series (a new district starts with no history).
- Season labelling is as reported. It hasn't been cross-checked against other sources.
- Units per crop are taken from the dataset description, not independently verified.

## Leakage risks

Year-t production and yield are never features. Year-t area is: the model conditions on the
cultivated area. In the backtest that area is the **final reported** area, while a real forecast
would only have sown or planned area, so backtest accuracy is optimistic about that input.

## Train / validation / test usage

The split is by crop year: train ≤ 2010, validation 2011–2012, test 2013–2014 (`scripts/train_supply.py`).
