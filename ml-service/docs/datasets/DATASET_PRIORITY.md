# Dataset Priority and MVP Data Strategy

**Date:** 2026-09-24. Based on `DATASET_CATALOGUE.md` (source IDs S01–S25).

Sources are ranked by **development priority**: how much the MVP depends on them and how
accessible they are today. **No models exist yet, so this ranking says nothing about model performance.**

## Priority categories

### P0: Required for MVP
These sources have verified access and are the backbone of the first task.

| Source | Why P0 |
|---|---|
| **S01** data.gov.in district crop production | The only VERIFIED district-level production/area source. It's the target variable for supply forecasting and the basis for derived yield. |
| **S06** data.gov.in historical daily mandi prices | VERIFIED, 82M rows, updated daily. It gives the market signal for supply-demand context and price anomalies. |
| **S11** IMD gridded rainfall 0.25° | VERIFIED official observed rainfall, 1901–2024. It's the primary weather driver for supply and yield. |
| **S14** NASA POWER | VERIFIED, no authentication. It supplies temperature and other variables until IMD Tmax/Tmin (S12) is verified. Labelled ESTIMATED. |

### P1: Important
Each of these fills a known MVP gap once access is confirmed.

| Source | Why P1 |
|---|---|
| **S02** UPAg | Would fill S01's **2015–2025 gap**. Without it, supply models can only be back-tested on history, not used for current forecasts. |
| **S09** CEDA API / **S08** AGMARKNET | The only candidate sources of **arrival quantities** (a market-supply signal and demand proxy). |
| **S15** Open-Meteo forecast | VERIFIED forecast inputs for forward-looking predictions (FORECAST). The non-commercial terms need a decision. |
| **S17** SoilGrids | VERIFIED soil features for suitability/yield (ESTIMATED). |
| **S07** data.gov.in current mandi prices | VERIFIED live snapshot for inference-time features. |
| **S12** IMD Tmax/Tmin | The official temperature source; replaces S14 if it verifies. |

### P2: Useful later

| Source | Why P2 |
|---|---|
| **S04** ICRISAT DLD | Harmonised district boundaries and a long history (1966 onwards), but stale and the download is unverified. Useful for boundary mapping and robustness checks. |
| **S19** MODIS/Landsat (Planetary Computer), **S18** Sentinel-2 | Vegetation indices add value for yield and crop health, but the processing cost is high. Add after tabular baselines exist. |
| **S23** HCES consumption survey | State-level consumption PROXY for demand. Access unverified. |
| **S13** IMD API | Official forecasts, but blocked (API key or whitelisting). Pursue through an institutional request. |
| **S16** Soil Health Card | Observed soil tests, but no bulk access. |

### P3: Experimental

| Source | Why P3 |
|---|---|
| **S21** PlantVillage, **S22** PlantDoc | Disease detection is scheduled after the tabular tasks (ML_TEAM_PLAN Stage 7). Licenses are verified per distribution, but lab-to-field transfer is unproven. |
| **S20** Google Earth Engine | Convenient, but its licensing depends on commercial vs. non-commercial deployment. |
| **S10** eNAM | No programmatic access. |
| **S05** FAOSTAT, **S25** misc. price series | National or coarse; sanity checks only. |

---

## MVP data strategy per ML task

| Task | Required data | Candidate source | Availability | Granularity | Major limitation | Readiness |
|---|---|---|---|---|---|---|
| **Supply forecasting** (district production) | Historical production and area; weather; crop; season | S01 + S11 + S14 (+ S02 for recent years) | S01, S11, S14 VERIFIED; S02 CANDIDATE | District × season × year | **S01 ends in 2014.** Recent years depend on UPAg (unverified). District boundaries change. | 🟡 **Ready for historical baseline and back-testing.** Not ready for current-year forecasts. |
| **Demand forecasting** | Consumption or demand signal; prices; seasonality | S23 (HCES), S24 (arrivals), S06 (prices) | All demand signals CANDIDATE; prices VERIFIED | State (HCES), market × day (arrivals) | **No observed demand exists.** Only proxies, and none verified yet. | 🔴 **Not ready.** |
| **Crop suitability / yield** | Yield history; soil; weather; season | S01 (yield = production/area), S17, S11, S14 | Yield derivable (VERIFIED inputs); soil ESTIMATED | District × season × year; soil 250 m | Yield ends 2014. No observed soil tests (S16 blocked). Suitability labels don't exist and have to come from yield. | 🟡 **Partial.** A yield baseline is possible; suitability needs a design decision. |
| **Disease detection** | Labelled leaf/plant images | S21, S22 | Licenses verified per distribution; images not downloaded | Image | Lab vs. field domain gap. Not India-specific. Deferred by the team plan. | 🟡 **Data exists; deferred.** |
| **Anomaly detection** (prices) | Long daily price series | S06 (+ S07 live) | VERIFIED, current | Market × commodity × day | No arrivals yet. Units not stated in the API. Needs a sample-key-independent pull (team API key). | 🟢 **Strongest current data**, once the team API key allows the pull. |

### Which task has the strongest data foundation?
- **Current, continuously updated data:** *price anomaly detection* (S06). The data is verified and updated daily.
- **Core product workflow (Farm → Supply → Gap):** *supply forecasting*. It has verified, official
  district production data plus verified weather, but only up to 2014.

Recommendation: **build the supply forecasting baseline first**, as the team plan says, as a
**historical back-test** (train ≤2010, validate 2011–2012, test 2013–2014). Unblock UPAg in
parallel before anyone claims current-year forecasts. Price anomaly detection is the natural second task.

### MVP region and crops (proposal, to agree with the team)
- **Region: Uttar Pradesh.** It has the deepest price history in S06 (600k–830k rows per major
  commodity) and dense S01 production data (~75 districts for potato).
- **Crops:**

  | Crop | S01 production rows (UP) | S06 price rows (UP) | Note |
  |---|---|---|---|
  | **Potato** | 1,276 (≈75 districts in 2014) | 826,017 | PlantVillage has potato blight classes, a later disease link |
  | **Wheat** | 1,275 | 765,202 | |
  | **Onion** *(tentative)* | 1,736 | 744,451 | UP is not a major onion producer. Check per-district continuity. Rice/Paddy is the alternative. |

  Rice: S06 has UP Rice (572k) and Paddy (252k) rows; S01 UP rice counts not yet checked (rate-limited).
- **Not tomato:** S01 has **0 UP tomato rows**, and only 30–50 in Maharashtra and Karnataka.
  Tomato price data exists, but there is no production target.

### What should NOT be attempted yet
- Demand forecasting presented as real demand. There is no observed demand data.
- Current-year supply forecasts presented as reliable (S01 ends in 2014).
- Tomato or other horticulture supply models (no production target in S01).
- Satellite/NDVI pipelines before tabular baselines exist.
- Disease models (scheduled later; domain gap unresolved).
- Scraping undocumented endpoints (`api.agmarknet.gov.in/v1/`, Soil Health Card GraphQL).
- Bulk pulls with the shared sample API key.

### Actions to unblock (owner: team)
1. **Register a data.gov.in API key** (needed for S01/S06 bulk pulls).
2. **Confirm UPAg export or API** for district APY 2015–2025 (S02).
3. **Register for the CEDA API** and confirm its license terms (S09, arrival quantities).
4. Decide the product's commercial vs. non-commercial status (it affects S15, S20, and IMD terms).
5. Apply for **IMD API access / IP whitelisting** if official forecasts are required (S13).
6. Confirm price units (₹/quintal) and `crop_year` semantics (calendar vs. agricultural year) with the source documentation.
