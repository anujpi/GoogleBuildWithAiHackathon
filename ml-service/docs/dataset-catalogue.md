# Dataset catalogue and data source audit

Audit date: 2026-09-28. Scope: a 2-day hackathon. Only sources that can realistically be downloaded and used in that time are ranked highly.
"Verified" means the URL was confirmed live on the audit date. Column lists come from the source documentation or well-known mirrors, so **re-check them after download**.

Data classes follow ML_CLAUDE.md: OBSERVED / FORECAST / MODEL_PREDICTION / ESTIMATED / SYNTHETIC.

---

## Summary

| # | Dataset | Use | Effort | Verdict |
|---|---|---|---|---|
| A1 | India Agriculture Crop Production (DES APY via Kaggle) | Supply | Low | **Primary for supply** |
| A2 | data.gov.in district-wise season-wise crop production | Supply (official copy of A1) | Low–Med | Provenance backup for A1 |
| A3 | ICRISAT District Level Database (DLD) | Supply, yield, suitability | Medium | **Primary for suitability/yield** |
| M1 | CEDA Ashoka Agmarknet (prices + arrivals) | Demand proxy, market | Low–Med | **Primary for demand proxy** |
| M2 | data.gov.in daily mandi price API | Live price only | Low | Demo/live only, not training |
| W1 | NASA POWER daily agro-climatology API | Weather features | Low | **Primary weather** |
| W2 | IMD gridded rainfall (0.25°) | Rainfall features | Medium | Optional upgrade over W1 |
| S1 | Kaggle "Crop Recommendation" (N, P, K, pH, climate) | Suitability demo | Very low | Fallback only, provenance weak |
| S2 | Soil Health Card portal | Soil features | High | Skip for hackathon |
| D1 | NSSO HCES 2022-23 consumption | Demand scaling | Medium | Optional, static |
| X1 | PlantVillage / PlantDoc | Disease | Low | Only if time allows |
| G1 | Sentinel/MODIS NDVI via Earth Engine | Satellite | High | Skip for hackathon |

---

## A1. India Agriculture Crop Production (1997–2021)

- **Source:** Ministry of Agriculture DES "Area, Production and Yield" (APY) statistics, republished on Kaggle.
- **URL:** https://www.kaggle.com/datasets/pyatakov/india-agriculture-crop-production (verified). Similar older mirror: https://www.kaggle.com/datasets/abhinand05/crop-production-in-india (1997–2015).
- **Geography:** All India, **district** level.
- **Time:** crop years 1997–2021. **Frequency:** annual × season (Kharif, Rabi, Whole Year, Summer, Winter, Autumn).
- **Columns:** State, District, Crop, Crop_Year, Season, Area (ha), Production, Yield (column names vary by mirror).
- **Target:** Production (tonnes) → supply.
- **License:** GODL-India (source) and Kaggle mirror terms. Needs a Kaggle login to download.
- **Class:** OBSERVED (official reported statistics). Yield is derived.
- **Missing values:** production is NA for some rows. Many district-crop series have gaps, because districts were split/renamed over time.
- **Units:** mostly tonnes. **Coconut is in nuts and cotton is in bales** — filter or convert before modelling.
- **Usefulness:** High. It is the most direct supply target at the right granularity.
- **Preprocessing:** trim/normalise State, District and Crop strings (trailing spaces are common). Harmonise district renames. Drop Whole Year rows that duplicate seasonal rows. Fix units. Build a (region, crop, season, year) panel.
- **Leakage:** Yield = Production / Area, so never use the same year's yield or area as a feature for the same year's production (area can be used only as a planned/sown-area input, which should be documented). Split by year. Nearby districts are correlated, so also test on held-out districts.

## A2. data.gov.in district-wise season-wise crop production

- **URL:** https://www.data.gov.in/catalog/district-wise-season-wise-crop-production-statistics-0 (verified)
- This is the same content as A1 at its official origin. Use it to cite provenance. Download A1 for speed.
- **License:** Government Open Data License – India. **Class:** OBSERVED.

## A3. ICRISAT District Level Database (DLD)

- **Source:** ICRISAT / TCI Cornell.
- **URL:** http://data.icrisat.org/dld/ (verified). Crops page: http://data.icrisat.org/dld/src/crops.html. Cleaned mirror: https://figshare.com/articles/dataset/Untitled_Item/19615764
- **Geography:** about 20 states, district level. It comes in two versions: *apportioned* to 1966 boundaries (consistent over time) and *unapportioned* (current districts).
- **Time:** 1966–2015/16 apportioned, 1990–2015/16 unapportioned. The latest available year is about 2017. **Frequency:** annual.
- **Columns:** area, production and yield for about 20 crops (rice, wheat, sorghum, pearl millet, maize, pulses, groundnut, soybean, cotton, sugarcane, total fruits & vegetables…), plus separate files for irrigation, rainfall (monthly normals/actuals), soil type, fertiliser use, land use, and markets.
- **Target:** yield (kg/ha) for suitability/yield. Production for supply.
- **License:** free for research. Registration is required on the site. Non-commercial.
- **Class:** OBSERVED (compiled from government sources). Apportioned values are ESTIMATED because of the boundary re-allocation.
- **Missing values:** a crop is often absent (0 or −1) in a district. Rainfall has some gaps.
- **Usefulness:** High for yield/suitability because rainfall, irrigation, soil and fertiliser are already joined at district-year level. It ends around 2017, so it is weaker for recent forecasting.
- **Preprocessing:** join the files on (Dist Code, Year). Treat −1 as NA. Keep units in kg/ha.
- **Leakage:** yield is derived from production and area. Same-year irrigated area is fine as a feature. Same-year production is not. Split by year and by district group.

## M1. CEDA Ashoka Agmarknet (mandi prices + arrivals)

- **Source:** AGMARKNET (DMI, Ministry of Agriculture), cleaned by the Centre for Economic Data & Analysis, Ashoka University.
- **URL:** https://agmarknet.ceda.ashoka.edu.in/ (verified). Portal: https://ceda.ashoka.edu.in/data-portal/. API docs: https://api.ceda.ashoka.edu.in/documentation/. Raw origin: https://agmarknet.gov.in
- **Geography:** about 3,000 mandis across India, with a state → district → market hierarchy.
- **Time:** 2000 to present, updated monthly. **Frequency:** daily, monthly and yearly aggregates.
- **Columns:** date, state, district, market, commodity, variety, min/max/modal price (₹/quintal), arrivals (tonnes).
- **Target:** monthly **arrivals** (tonnes) as the demand/market-throughput proxy. **Modal price** for market pressure.
- **License:** free for non-commercial/research use with attribution. Check the portal terms before any commercial demo.
- **Class:** OBSERVED market transactions. **Arrivals are a DEMAND PROXY. They are not consumption or retail sales.** Mandi prices are wholesale, not retail.
- **Missing values:** mandis report irregularly. There are zero/missing days, holidays, and outlier prices (unit-entry errors).
- **Usefulness:** High. It is the only realistic, recent, high-frequency market signal.
- **Preprocessing:** aggregate daily data to monthly per district or state. Winsorise price outliers. Remove markets with sparse reporting. Use log arrivals. Add month/season calendar features.
- **Leakage:** do not use same-period price as a feature for same-period arrivals (they are jointly determined). Use lagged features only. Split by time.

## M2. data.gov.in "Current daily price of various commodities from various markets"

- **URL:** https://www.data.gov.in/resource/current-daily-price-various-commodities-various-markets-mandi (API key is free with registration).
- Returns **today only**, so it cannot be used for training history. Good for a live "latest price" panel. **Class:** OBSERVED.

## W1. NASA POWER daily agroclimatology

- **URL:** https://power.larc.nasa.gov/ — API: `https://power.larc.nasa.gov/api/temporal/daily/point?parameters=T2M,T2M_MAX,T2M_MIN,PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN&community=AG&longitude=..&latitude=..&start=YYYYMMDD&end=YYYYMMDD&format=CSV`
- **Geography:** global, point queries on a grid of about 0.5°. **Time:** 1981 to near real time. **Frequency:** daily.
- **Features:** temperature (mean/min/max), precipitation, humidity, solar radiation.
- **License:** open, no key. **Class:** reanalysis/satellite-derived, so tag it ESTIMATED (it is not station-OBSERVED).
- **Missing values:** essentially none. The main limitation is coarse resolution.
- **Preprocessing:** query one point per district centroid. Aggregate to season windows (Kharif Jun–Oct, Rabi Nov–Mar): total rain, mean temperature, heat days.
- **Leakage:** for pre-season forecasts, use weather only up to the forecast date. Full-season weather is fine for a nowcast/yield model, but label it as such.

## W2. IMD gridded rainfall 0.25°

- **URL:** https://www.imdpune.gov.in/cmpg/Griddata/Rainfall_25_NetCDF.html. Python: `pip install imdlib`.
- 1901 to present, daily, all of India. OBSERVED (station-interpolated). More authoritative for rainfall than W1, but needs a grid → district join. Use it only if time remains.

## S1. Kaggle "Crop Recommendation Dataset"

- **URL:** https://www.kaggle.com/datasets/atharvaingle/crop-recommendation-dataset
- 2,200 rows: N, P, K, temperature, humidity, pH, rainfall → label (22 crops, 100 rows each).
- **Provenance is undocumented.** It is widely believed to be augmented/composed from ranges, and it has no geography or time.
- **Class:** treat it as **SYNTHETIC/ESTIMATED**. Models trained on it score about 99% accuracy, which reflects how the data was built, not real-world skill.
- **Use:** a UI/demo fallback only, clearly labelled. Do not present it as evidence.

## S2. Soil Health Card portal

- **URL:** https://soilhealth.dac.gov.in/
- It has card-level N/P/K/pH/micronutrients, but **there is no bulk download**. It is paginated per village, so scraping takes days. **Skip.** For soil, use the ICRISAT DLD soil-type file (A3) or the farm's own soil profile (backend `SoilProfile`) at inference time.

## D1. NSSO Household Consumption Expenditure Survey 2022-23

- **URL:** https://www.mospi.gov.in/ (HCES 2022-23 factsheet/report)
- Per-capita monthly quantity consumed of cereals, pulses, vegetables (including onion and potato), by state and rural/urban.
- It is a single cross-section, OBSERVED (survey). Use it together with Census population projections to build a static **ESTIMATED consumption baseline** for scaling a demand proxy. Not needed for the first model.

## X1. Disease images (only if time permits)

- **PlantVillage:** https://www.kaggle.com/datasets/abdallahalidev/plantvillage-dataset. About 54k lab images in 38 classes (tomato and potato included). CC BY-SA. The lab background means models overfit to it.
- **PlantDoc:** https://github.com/pratikkayal/PlantDoc-Dataset. About 2.6k field images. Use it as the realistic test set.
- **Leakage:** PlantVillage has several photos of the same leaf, so split by leaf/source where possible and report per-class F1.

## G1. Satellite NDVI (Earth Engine MODIS/Sentinel-2)

- It needs an Earth Engine account/project and a district-polygon reduction pipeline. **Skip for the hackathon.**

---

## Recommendations

### MVP region and crops (proposal, to be agreed with backend/frontend)

**Maharashtra; onion (Rabi), tomato, soybean (Kharif).**
- Onion: Lasalgaon/Nashik is India's largest onion mandi cluster. It has dense Agmarknet arrivals and prices and well-known price shocks, which gives the supply-demand gap a strong demo story.
- Tomato: dense mandi data, and it is volatile.
- Soybean: a major Kharif field crop that is covered by ICRISAT DLD (onion/tomato are not separate DLD crops), so it anchors the suitability/yield model.

### Recommended dataset per model

| Model | Dataset | Target | Unit | Data class of output |
|---|---|---|---|---|
| **Supply forecast** | A1 (+ W1 seasonal weather) | Production per district × crop × season × year | tonnes | MODEL_PREDICTION |
| **Demand forecast** | M1 arrivals, monthly per district/state | Market arrivals (demand **proxy**) | tonnes/month | MODEL_PREDICTION (proxy) |
| **Crop suitability / yield** | A3 ICRISAT DLD (yield + rainfall + irrigation + soil) with W1 | Yield | kg/ha → t/ha | MODEL_PREDICTION |

Suitability is exposed as the expected yield relative to the district's historical yield distribution for that crop. It is not a classifier on S1.

### Proposed features

- **Supply:** lag-1/2/3 production, 3-year rolling mean, lag area, trend (year), season, crop, district (target-encoded on training years only), seasonal rain/temperature anomaly (W1), and lagged harvest-month mandi price (M1). Price is a planting-decision signal.
- **Demand proxy:** lag-1/2/3/12 arrivals, rolling 3-month mean, month-of-year, lag-1 modal price, lag price change, festival month flag, and the previous season's regional production (A1).
- **Yield/suitability:** seasonal and monthly rainfall, temperature, irrigated-area share, fertiliser use per ha, soil type, lag yield, and trend.

### Baselines (required before any tree model)

- Supply: last year's same-season production, and a 3-year mean.
- Demand: seasonal naive (same month last year), and a 3-month moving average.
- Yield: the district's historical mean yield.

### Splits

- Supply: train ≤2016, validate 2017–2018, test 2019–2021. Add a secondary evaluation on held-out districts.
- Demand: train ≤2022, validate 2023, test 2024 to present (monthly).
- Yield: train ≤2010, validate 2011–2013, test 2014–2017.

### Data availability problems

1. The ML foundation (`ml-service/`, `ML_STATE.md`) is **not in the repository**. `ml-branch` is identical to `main`.
2. **There is no true demand data** (retail/POS). Arrivals and consumption surveys are proxies and must be labelled as proxies.
3. A1 ends around 2021 and A3 around 2017, so "forecasts" for 2026 extrapolate over a gap of 4 or more years. Say so in the UI.
4. District boundary changes break time series. Use ICRISAT apportioned data, or aggregate to state level if matching is painful.
5. Onion and tomato are not separate crops in ICRISAT DLD (they fall under "fruits & vegetables"). Suitability for them needs A1 yields plus W1.
6. Soil: there is no bulk Soil Health Card data. District soil type from DLD is the only realistic soil feature.
7. Mandi reporting is irregular, with outliers. Prices are wholesale, not retail.
8. Kaggle and ICRISAT both need a (free) account. Downloads cannot be fully automated without credentials.

### Next model to build

**Supply forecasting baseline on A1**: Maharashtra, onion, tomato and soybean, seasonal-naive plus a 3-year mean. After that, try XGBoost with lag and weather features, evaluated on 2019–2021.
