# Dataset: crop_yield (state-level crop yield, India, 1997–2020)

**HISTORICAL data.** It is not current, live or real-time data, and it must not be presented as such.
Loader: `src/agri_ml/datasets/crop_yield.py`. Audit: `scripts/audit_crop_yield.py`.

| Field | Value |
|---|---|
| Dataset name | `crop_yield.csv` |
| Source (publisher) | **UNVERIFIED.** The team obtained it as a downloaded CSV (a third-party compilation, believed to be from Kaggle). The uploader, page and upstream provenance still need to be confirmed and recorded here. Upstream values look like Ministry of Agriculture (DES) crop statistics aggregated to state level (see "Provenance notes"). |
| URL / identifier | **UNVERIFIED.** Record the exact dataset page URL. File identity: SHA-256 prefix in "Version" below. |
| Access method | Manual download of a single CSV |
| Access date | **UNVERIFIED.** The file was present in a team member's Downloads folder on 2026-09-28. Record the real download date. |
| License / usage terms | **UNKNOWN.** Read it from the dataset page before any redistribution or product use. If the upstream is data.gov.in, GODL-India attribution also applies. |
| Data classification | Crop, Crop_Year, Season, State, Area, Production and Yield: **OBSERVED** (official statistics as republished by a third party). Annual_Rainfall: **OBSERVED** at state level, origin UNVERIFIED. Fertilizer and Pesticide: **ESTIMATED** (they appear to be derived from Area; see below). |
| Geographic coverage | India, 30 states/UTs (count from the audit below) |
| Spatial granularity | State. **No district information.** |
| Temporal coverage | Crop years 1997–2020. Coverage is uneven by state (see the audit). |
| Temporal granularity | Crop year × season |
| Update frequency | None (static file) |
| Version | `crop_yield-sha256-<prefix>`, computed by `crop_yield.dataset_version()` (value in the audit results below) |

## What one row represents
One crop × one season × one state × one crop year, with totals for that combination.

- **Crop_Year** is not documented as either a calendar year or an agricultural year. Treat it as an
  opaque year label and don't align it to calendar weather without confirming.
- **Season** values include both the Kharif/Rabi/Whole Year system and the Autumn/Summer/Winter
  system, which some states use (for example Assam and Odisha rice). **Seasons are therefore not
  comparable across states.**
- A crop can have several rows for the same state-year, one per season. Summing seasons into an
  annual total double-counts nothing, but "Whole Year" rows must not be added to seasonal rows as
  if they were a separate harvest without checking.
- After whitespace stripping, the (Crop, Crop_Year, Season, State) key is unique. The audit
  checks this.

## Target(s)
Candidate targets: `Production` (supply) and `Yield` (yield / suitability evidence). Nothing is trained yet.

## Features

Units: the dataset file itself states no units. The only units recorded here are ones that are
**UNVERIFIED** until the dataset page's column descriptions are recorded. Don't use them in a
contract until then.

| Column | Meaning | Unit | Type | Missing % |
|---|---|---|---|---|
| Crop | Crop name (55 distinct) | n/a | text | 0 |
| Crop_Year | Crop year label | year | int | 0 |
| Season | Kharif, Rabi, Whole Year, Autumn, Summer, Winter | n/a | text | 0 |
| State | State/UT name | n/a | text | 0 |
| Area | Cropped area | UNVERIFIED (hectares is likely, since upstream DES data uses ha) | float | 0 |
| Production | Production quantity | UNVERIFIED (tonnes for most crops). **Not a single unit:** Coconut is almost certainly a count of nuts (Assam 1997 = 126,905,000 from 19,656 area units), and cotton may be in bales. | float | 0 |
| Annual_Rainfall | Annual rainfall for the state-year. The same value repeats for every crop/season in a state-year. | UNVERIFIED (values of about 300–4,000 suggest mm) | float | 0 |
| Fertilizer | Fertilizer quantity | UNVERIFIED | float | 0 |
| Pesticide | Pesticide quantity | UNVERIFIED | float | 0 |
| Yield | Supplied yield | UNVERIFIED (Production unit ÷ Area unit) | float | 0 |

## Missing values
None in the raw file: 0 NaN in every column (see the audit). **Zeros are present** in several numeric
columns and are not the same as "not grown". See the audit counts.

## Known biases and gaps
1. **Fertilizer and Pesticide appear to be derived from Area, not measured.** `Fertilizer / Area` is
   one constant across **all states and crops within a year** (1997 ≈ 95.17, 1998 ≈ 98.8, 2018 ≈ 162.2).
   `Pesticide / Area` behaves the same way (1997 ≈ 0.31). In effect they are `Area × national per-year rate`.
   They carry no state- or crop-specific information and are near-perfectly collinear with Area.
   **Classified ESTIMATED. Don't present them as observed input use.**
2. **Yield ≠ Production / Area for many rows.** Example: Arecanut, Assam, 1997: 56,708 / 73,814 =
   0.768, but Yield = 0.796. A plausible explanation (UNVERIFIED) is that Yield is an average of
   district-level yields rather than the area-weighted ratio. The audit reports how many rows agree.
3. **Mixed production units** (Coconut in nuts, possibly cotton in bales). Those crops' Yield values are
   not comparable to others'. For example, Coconut yield is about 5,000 against about 1 for grains.
4. **Implausible values exist.** Example: Rice, Jammu and Kashmir, 1997: Area 275,746, Production 5,488
   (yield about 0.02). Outliers need review, not silent removal.
5. **Uneven coverage.** Some states have few years or crops. Horticulture is thin. States that
   were split or reorganised (e.g. Telangana 2014, J&K/Ladakh 2019) have discontinuities.
6. **State-level only.** It can't support district or farm predictions directly.
7. Rainfall is a single annual state value, with no seasonal or within-state variation.

## Leakage risks
- **Temporal:** splits must be by `Crop_Year` (past → future), never random.
- **Target encoding:** Yield is (approximately) Production ÷ Area. Using Production to predict Yield,
  or Yield and Area to predict Production, leaks the target.
- **Fertilizer/Pesticide encode Area.** Using them with Area adds no information but looks like it does.
  If Area is excluded for a scenario, they reintroduce it.
- **Area is only known after the season.** For forecasting it must come from a prior year or a sowing
  estimate, not from the target row.
- **Spatial:** neighbouring states share weather and policy. Consider leave-state-out evaluation for
  "unseen region" claims.

## Preprocessing
Reproducible via `agri_ml.datasets.crop_yield.load()`:
1. Read with explicit dtypes (the loader rejects the file if the columns differ from the expected 10).
2. Strip leading/trailing whitespace from `Crop`, `Season`, `State`. The raw file pads values, e.g.
   `"Kharif     "` and `"Coconut "`.

Nothing else happens. Crop-name normalisation, unit harmonisation, outlier handling and aggregation
are open decisions for feature engineering.

## Train / validation / test usage
Not yet defined. Proposal for the first baseline (to agree before use): time split by Crop_Year,
e.g. train ≤ 2014, validation 2015–2017, test 2018–2020, plus checks on per-state coverage in the
test years.

## Provenance notes
- The columns Crop, Crop_Year, Season, State, Area and Production match the structure of the
  data.gov.in district-wise crop production dataset (catalogue **S01**), aggregated to state level.
  The same season labels, crop spellings (`Rapeseed &Mustard`, `Other  Rabi pulses`) and nut units for
  Coconut appear there. S01 as verified ends in 2014/2015, while this file reaches 2020, so the
  upstream version for 2015–2020 is **UNVERIFIED**.
- The origin of Annual_Rainfall, Fertilizer and Pesticide is **UNVERIFIED**.

## Audit results
Filled in from `python scripts/audit_crop_yield.py` (see below).

## Usage notes
- Always say "historical (1997–2020)" when these numbers reach any API or UI.
- Before this data feeds a contract: confirm the source URL, license and units, and resolve the
  Coconut and cotton units.
