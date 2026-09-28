# Dataset: crop_yield (state-level crop statistics, India, 1997–2020)

**This is HISTORICAL data.** It is not current, live or real-time, and it must never be presented
that way.

- Loader and validation: `src/agri_ml/datasets/crop_yield.py`
- Audit: `scripts/audit_crop_yield.py`
- Preprocessing: `scripts/prepare_crop_yield.py`
- Tests: `tests/test_crop_yield.py`

This page keeps three kinds of statement apart:

- **CSV**: facts measured from the file itself by the audit script. These are reproducible.
- **Publisher**: what the Kaggle dataset page states. Not independently verified.
- **UNVERIFIED**: anything that neither of the above establishes.

| Field | Value |
|---|---|
| Dataset name | Agricultural Crop Yield in Indian States Dataset (file `crop_yield.csv`) |
| Publisher | Kaggle user `akshatgupta7`; collaborators `abhaydewedi`, `akshatsainii` |
| URL / identifier | https://www.kaggle.com/datasets/akshatgupta7/crop-yield-in-indian-states-dataset (Kaggle dataset id 3525502) |
| File identity | 1,620,945 bytes; SHA-256 `ab9bc356b1f8107d490376cec24450a0e2906322ad25788ab22fb32537ab1f8f` |
| Match to publisher | Kaggle's file listing shows one file, `crop_yield.csv`, at **1,620,945 bytes**, created 2023-07-17. The local copy has exactly the same size and a matching modification date (2023-07-17). Kaggle publishes no checksum, so this is strong evidence the files are the same, not proof. |
| Access method | Manual download of one CSV. The local copy came from a team member's `~/Downloads/` and is copied to `data/raw/crop_yield.csv` (git-ignored). |
| Access date | Metadata read from the Kaggle API on 2026-09-28. The original download date is UNVERIFIED. |
| License | **Publisher:** CC-BY-SA-4.0 (Kaggle metadata). It requires attribution and share-alike for redistributed adaptations. Upstream sources carry their own terms (e.g. GODL-India for data.gov.in). |
| Upstream sources (publisher-listed) | 1. data.gov.in district-wise season-wise crop production statistics (our catalogue S01) · 2. FAOSTAT · 3. data.gov.in rainfall in India · 4. environicsindia.in · 5. an IMD Pune e-book. The publisher doesn't say which column comes from which source. |
| Data classification | Crop, Crop_Year, Season, State, Area, Production: **OBSERVED** (official statistics republished by a third party). Annual_Rainfall: **OBSERVED**, one state-level value (origin UNVERIFIED). Fertilizer, Pesticide: **ESTIMATED** (the CSV shows them to be Area × one per-year rate; see findings). Yield: **as supplied**; don't treat it as Production ÷ Area (see findings). |
| Geographic coverage | CSV: 30 states/UTs |
| Spatial granularity | CSV: state. No district column. |
| Temporal coverage | CSV: Crop_Year 1997–2020. **2020 has 37 rows, all from Uttarakhand**, so the effective coverage is 1997–2019. |
| Temporal granularity | CSV: crop year × season |
| Update frequency | Publisher: "never" |
| Version | `crop_yield-sha256-ab9bc356b1f8` (from `crop_yield.dataset_version()`) |
| Preprocessing version | `crop-yield-prep-v1` |

## What one row represents
One crop × one season × one state × one crop year. After whitespace stripping, the key
(Crop, Crop_Year, Season, State) is unique in the CSV: 0 duplicate keys and 0 duplicate rows.

- **Crop_Year:** the publisher describes it as "the year in which the crop was grown". The CSV
  doesn't show whether this is a calendar year or an agricultural year (for example July–June).
  Treat it as an opaque label, and don't align it with calendar-year weather until that's confirmed.
- **Season:** the CSV mixes two labelling systems:
  - Kharif / Rabi / Whole Year
  - Autumn / Summer / Winter

  Some states use both, and 6 labels appear in total. Seasons are not comparable across states.
- **Several rows per crop-year:** a crop can appear under several seasons in the same state-year.
  In **48** state × crop × year groups, a "Whole Year" row sits alongside other seasons. Whether
  those rows overlap is UNVERIFIED, so don't sum them blindly.

## Columns: what the CSV contains vs. what the publisher states

Types are measured by the audit. "Inferred" is what pandas reads with no type hints; the loader
then forces the "loaded" type.

| Column | CSV: inferred → loaded type | CSV: observed range and notes | Publisher: meaning and unit | Unit status |
|---|---|---|---|---|
| Crop | str → string | 55 distinct values. 172 raw values were padded (all `"Coconut "`). | "name of the crop cultivated" | n/a |
| Crop_Year | int64 → int64 | 1997–2020 | "year in which the crop was grown" | calendar or agricultural year UNVERIFIED |
| Season | str → string | 6 labels. **Every** raw value is right-padded (e.g. `"Kharif     "`). | "specific cropping season" | n/a |
| State | str → string | 30 values, no padding | "Indian state where the crop was cultivated" | n/a |
| Area | float64 → float64 | min 0.5, median 9,317, max 50,808,100. 235 values have fractions. | "total land area … under cultivation", **hectares** | stated by publisher only; UNVERIFIED |
| Production | int64 → float64 | min 0, median 13,804, max 6,326,000,000. All whole numbers. | "quantity of crop production", **metric tons** | stated by publisher; **the CSV contradicts a single unit** (Coconut, see findings) |
| Annual_Rainfall | float64 → float64 | min 301.3, median 1,247.6, max 6,552.7. One value per state-year (657 of 657 groups). | "annual rainfall received in the crop-growing region", **mm** | stated by publisher only; UNVERIFIED |
| Fertilizer | float64 → float64 | min 54.17, median 1,234,957, max 4.8 × 10⁹. Equals Area × one rate per year. | "total amount of fertilizer used for the crop", **kilograms** | stated by publisher; **the CSV shows it isn't crop-specific** |
| Pesticide | float64 → float64 | min 0.09, median 2,421.9, max 1.6 × 10⁷. Equals Area × one rate per year. | "total amount of pesticide used for the crop", **kilograms** | stated by publisher; **the CSV shows it isn't crop-specific** |
| Yield | float64 → float64 | min 0, median 1.03, max 21,105 | "calculated crop yield (production per unit area)" | derived; **the CSV often disagrees with Production ÷ Area** |

No unit above has been confirmed against the upstream government source. Keep units out of any
API contract or UI until they are confirmed.

## Audit results (real file, `crop_yield-sha256-ab9bc356b1f8`)

Reproduce with `python scripts/audit_crop_yield.py --json-out artifacts/audit/crop_yield.json`.

| Check | Result |
|---|---|
| Rows × columns | **19,689 × 10** |
| Columns | Crop, Crop_Year, Season, State, Area, Production, Annual_Rainfall, Fertilizer, Pesticide, Yield |
| Missing values (NaN) | **0** in every column |
| Empty strings after stripping | 0 |
| Fully duplicated rows | **0** |
| Duplicate (Crop, Crop_Year, Season, State) keys | **0** |
| Negative values | 0 in every numeric column |
| Infinite values | 0 |
| Zeros | Production 112, Yield 112. Area, Rainfall, Fertilizer and Pesticide have none. |
| Year range | 1997–2020 (2020: Uttarakhand only, 37 rows) |
| States per year | 21 (1997), 25–29 (1998–2012), 30 (2013–2018), 29 (2019), **1 (2020)** |
| Rows per year | 410 (1997) rising to about 1,070 (2017–2019), 37 (2020) |
| Series (State × Crop × Season) | 1,684 series. **681 have missing years** inside their span. 220 have a single year. |

**States (30):** Andhra Pradesh, Arunachal Pradesh, Assam, Bihar, Chhattisgarh, Delhi, Goa,
Gujarat, Haryana, Himachal Pradesh, Jammu and Kashmir, Jharkhand, Karnataka, Kerala, Madhya
Pradesh, Maharashtra, Manipur, Meghalaya, Mizoram, Nagaland, Odisha, Puducherry, Punjab, Sikkim,
Tamil Nadu, Telangana, Tripura, Uttar Pradesh, Uttarakhand, West Bengal.

Coverage differs by state:
- These start late: Chhattisgarh 2000, Uttarakhand 2000, Jharkhand 2002, Telangana **2013**.
- Manipur ends in 2018.
- Jammu and Kashmir has no 1998.

**Crops (55):** Arecanut, Arhar/Tur, Bajra, Banana, Barley, Black pepper, Cardamom, Cashewnut,
Castor seed, Coconut, Coriander, Cotton(lint), Cowpea(Lobia), Dry chillies, Garlic, Ginger,
Gram, Groundnut, Guar seed, Horse-gram, Jowar, Jute, Khesari, Linseed, Maize, Masoor, Mesta,
Moong(Green Gram), Moth, Niger seed, Oilseeds total, Onion, `Other  Rabi pulses` (two spaces, as
in the file), Other Cereals, Other Kharif pulses, Other Summer Pulses, Peas & beans (Pulses),
Potato, Ragi, Rapeseed &Mustard, Rice, Safflower, Sannhamp, Sesamum, Small millets, Soyabean,
Sugarcane, Sunflower, Sweet potato, Tapioca, Tobacco, Turmeric, Urad, Wheat, `other oilseeds`
(lower case, as in the file).

**Seasons (6), with row counts:** Kharif 8,232 · Rabi 5,742 · Whole Year 3,717 · Summer 1,195 ·
Autumn 414 · Winter 389.

## Data-quality findings

Each finding below is measured from the CSV. Where it contradicts the publisher, that's stated.

1. **2020 is effectively missing.** Only Uttarakhand (37 rows) has 2020 data. The publisher's
   "1997 till 2020" holds for one state only. Don't use 2020 as a validation or test year.
2. **Fertilizer and Pesticide are not crop-specific measurements.** `Fertilizer / Area` takes
   exactly one value per year across all states and crops (24 of 24 years), e.g. 95.17 in 1997,
   166.11 in 2010 and 171.76 in 2019. `Pesticide / Area` behaves the same way (0.31 in 1997,
   0.37 in 2019). In effect they are `Area × a national per-year rate`. This contradicts the
   publisher's "used for the crop". They are classified **ESTIMATED**. They add no information
   beyond Area and year, and they must not be shown as observed input use.
3. **Annual_Rainfall is one state-year value.** It repeats across every crop and season in a
   state-year (657 of 657 groups), so it isn't specific to a "crop-growing region" as the
   publisher says. There is no seasonal or within-state variation.
4. **Yield ≠ Production ÷ Area for most rows.** Of the 19,577 rows with Production > 0:
   - only 4,047 (20.7%) agree within 1%
   - 13,402 (68.5%) agree within 10%
   - 257 differ by more than 2×

   The median ratio is 0.996 and the 1%–99% range is 0.55–1.75. So Yield wasn't calculated
   from these two columns in the way the publisher implies. One possibility (UNVERIFIED) is an
   average of district yields.

   Separately, 8 rows contradict themselves:
   - 4 have Production = 0 but Yield = 0.4. All have Area ≤ 1: Tobacco J&K 2012 and 2014,
     Horse-gram J&K 2016, Masoor Mizoram 2004.
   - 4 have Production > 0 but Yield = 0: Cardamom and Sunflower in West Bengal 1997, and
     Cotton(lint) Kharif and Rabi in Tamil Nadu 2017.
5. **Production mixes units.** Coconut Production reaches 6.3 × 10⁹ (Kerala 2005), and its
   median Yield (8,466) is about 160× the next-highest crop's (Sugarcane, 53). Metric tons,
   as the publisher states, isn't plausible for Coconut. The upstream S01 dataset reports
   coconut as a count of nuts, but that is UNVERIFIED for this file. Cotton(lint) may also use
   a different unit (bales, UNVERIFIED). Coconut and cotton Production and Yield can't be
   compared with other crops.
6. **Extreme or implausible rows.** These are flagged or listed for review, not removed.
   - *Niger seed, West Bengal, 1997, Rabi:* Area 50,808,100, about 5× the next-largest Area in
     the whole file (Wheat, Madhya Pradesh 2019: 10,216,517). West Bengal's other niger seed rows
     for 1997–2001 are 6,362–7,587. This is a single-row series, so no automatic flag catches it.
   - *Rice, Jammu and Kashmir, 1997, Kharif:* Area 275,746 with Production 5,488 (Yield 0.017).
   - *Moth, Uttar Pradesh, 1997, Kharif:* Area 933,194, about 3,100× its series median.
   - *Linseed and Moong, Uttarakhand, 2002:* about 2,500× their series medians.
   - *Peas & beans and Masoor, Madhya Pradesh, 2003:* Areas of 5 and 17, far below their series.
   - 474 rows have Area < 10; 2 rows have Area < 1.
7. **Zero production.** There are 112 rows with Production = 0, spread over many crops (Jute 15,
   Sannhamp 7, Horse-gram 7, Onion 7, Coconut 7, …). Haryana (27), Chhattisgarh (22) and Kerala
   (21) have the most. Zero may mean "none produced" or "not reported". This is UNVERIFIED, so
   don't read it as either.
8. **Season labels switch within a series.** In **111** State × Crop pairs, one season label
   stops before another starts. So a (State, Crop, Season) series breaks even when the crop was
   grown throughout. Uttar Pradesh examples:
   - **Potato:** Whole Year 1997–2003, then Rabi 2004–2019
   - Turmeric, Sannhamp, Tobacco, Sunflower, Banana, Dry chillies and Guar seed also switch

   UP **Onion** isn't a clean switch, so `season_label_changes()` doesn't list it. Its labels
   overlap: Whole Year 1998–2005 (5 years), Rabi 2002–2019 (16), Summer 2002–2019 (14) and a
   single Kharif row in 1997. It still needs a season decision before use.
9. **The Andhra Pradesh / Telangana split.** Telangana starts in 2013. Andhra Pradesh's total
   Area drops from about 12.0M (2012) to about 7.2M (2013). Andhra Pradesh before and after 2013
   is not the same region.
10. **An aggregate crop overlaps individual crops.** `Oilseeds total` has 29 rows: Arunachal
    Pradesh 1997–2015, Andhra Pradesh 2015–2019 and Gujarat 1998. It totals other oilseed rows,
    so summing all crops double-counts.
11. **Crop names aren't normalised.** Examples: `Other  Rabi pulses` (double space, 355 rows),
    `other oilseeds` (lower case) and `Rapeseed &Mustard`. The loader keeps them unchanged.

## Quality flags (`quality_flags()`, `crop-yield-prep-v1`)

The flags are **audit heuristics for review**. They never change a value or remove a row.
Whether to exclude a flagged row is a decision for each task, recorded in that task's model card.
The thresholds are constants in `src/agri_ml/datasets/crop_yield.py`.

| Flag | Rule | Rows flagged (real file) |
|---|---|---|
| `flag_zero_production` | Production == 0 | 112 |
| `flag_yield_zero_mismatch` | Exactly one of Yield and Production is 0 | 8 |
| `flag_yield_mismatch` | Yield ÷ (Production ÷ Area) > 2 or < 0.5 | 257 |
| `flag_series_jump` | Area or Production > 10× or < 0.1× its State × Crop × Season median, in series with ≥ 5 rows | 505 |
| `flag_aggregate_crop` | Crop is `Oilseeds total` | 29 |
| `flag_incomplete_year` | The year covers < 50% of the median number of states per year (only 2020) | 37 |
| `flag_any` | Any of the above | **902 (4.6%)** |

For the likely MVP crops in Uttar Pradesh (Potato, Wheat, Onion, Rice), only one row is flagged:
Onion 2002, Whole Year, Area 61 (`flag_series_jump`).

## Missing values
There are no NaN values anywhere in the CSV. Gaps show up instead as **missing rows**:
- 681 of 1,684 series skip years
- some states start late or end early
- 2020 is almost empty

Zeros (112 in Production) aren't the same as missing, and the file doesn't tell us which they are.

## Leakage risks
- **Temporal:** split by Crop_Year (past → future), never randomly. 2020 is unusable as a test year.
- **Target encoding:** Yield is roughly Production ÷ Area. Don't use Production to predict Yield,
  or Yield and Area to predict Production.
- **Fertilizer and Pesticide encode Area and year.** Using them adds no signal, but it looks as
  if it does. If Area is excluded, they bring it back in.
- **Area is known only after the season.** A forecast must use a prior year's area or a sowing
  estimate, never the target row's own Area.
- **Rainfall timing:** annual rainfall for crop year t may include months after harvest, and the
  Crop_Year alignment is UNVERIFIED. Treat it as possibly unavailable at forecast time.
- **Spatial:** neighbouring states share weather and policy. Use leave-state-out evaluation
  before claiming anything about unseen regions.

## Preprocessing (reproducible)
`python scripts/prepare_crop_yield.py`:
1. `load_raw()` checks for exactly the 10 expected columns and reads them with explicit types.
2. `clean()` strips leading and trailing whitespace from Crop, Season and State. Nothing else
   changes.
3. `validate()` **stops with exit code 1** if it finds missing values, duplicate rows or keys,
   negative or infinite values, unexpected types or empty strings.
4. `quality_flags()` adds the `flag_*` columns above.
5. The output goes to
   `data/processed/crop_yield/crop_yield-sha256-ab9bc356b1f8/crop-yield-prep-v1/`:
   `crop_yield_clean.csv` (19,689 rows, none dropped) and `manifest.json` (input hash,
   versions, flag counts, flag thresholds, validation report). An existing output folder is never
   overwritten.

Not done yet, deliberately (these are decisions for modelling, not cleaning):
- normalising crop names
- harmonising units
- mapping season labels between the two systems
- excluding or correcting outliers
- aggregating to annual totals

## Train / validation / test usage
**This dataset is the actual training source for the MVP supply model** (`supply-xgb-v1`; see
`docs/model-cards/supply.md`). The team decided on 2026-09-28 to use it in place of S01.

- **Chronological split:** train ≤ 2013, validate 2014–2016, test 2017–2019. 2020 is excluded
  (finding 1).
- **Supply view (`to_supply_frame()`):** maps State/Crop/Season/Crop_Year/Area/Production to the
  pipeline's columns. It excludes Coconut and Cotton(lint) for their units (648 rows), plus rows
  flagged `flag_series_jump` (505), `flag_incomplete_year` (37) and `flag_aggregate_crop` (29).
  That removes 1,194 rows and leaves 18,495. No values are changed.
- **Columns not used:** Yield, Annual_Rainfall, Fertilizer and Pesticide (see "Leakage risks").
- **Season switches (finding 8):** left as they are. The first years after a switch have no
  lags, so they aren't scored. Nothing is imputed.

## Usage notes
- Always label these values "historical (1997–2019)" wherever they reach an API or UI.
- Attribute the dataset under CC-BY-SA-4.0 when it is redistributed.
- Before this data feeds a contract:
  - confirm the units against the upstream data.gov.in source
  - resolve the Coconut and cotton units
  - decide how to handle each flag
