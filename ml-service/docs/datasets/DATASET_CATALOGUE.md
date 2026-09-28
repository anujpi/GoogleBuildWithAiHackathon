# Dataset Catalogue: Stage 1 Source Audit

**Audit date:** 2026-09-24
**Method:** we sent small HTTP requests (1–3 rows, or the first few KB of a file) against each source.
Nothing large was downloaded, and no data was stored in the repo.

## Rules used in this document
- **Status values:**
  - **VERIFIED**: we received real data from the source in this audit.
  - **CANDIDATE**: the source exists and looks usable, but its data or access was not fully confirmed.
  - **BLOCKED**: currently inaccessible, or no sanctioned programmatic access was found.
  - **NOT_SUITABLE**: accessible, but wrong for our tasks (granularity, coverage, content).
- **UNVERIFIED** marks a single fact we could not confirm. We didn't guess.
- **Source** (where data came from) and **dataClassification** (OBSERVED, FORECAST, ESTIMATED,
  MODEL_PREDICTION, SYNTHETIC) are recorded separately.
- **PROXY** marks a signal that stands in for something we can't observe directly, most importantly demand.

## data.gov.in API key used
- Every data.gov.in resource needs an `api-key`. Without one, the API returns `{"error":"Authorization field missing"}`.
- **The team does not have its own key yet.** Tests used the public sample key that data.gov.in
  publishes for demos. It is shared and heavily rate-limited: it returned `"Rate limit exceeded"`
  several times during this audit.
- A personal key needs a free data.gov.in account, which a team member has to register.
  Pulling data at any real volume isn't possible until that key exists.
- The data.gov.in page footer shows a banner reading "This is a sandbox environment created for
  testing and demonstration purposes only". Its meaning for the API is **UNVERIFIED**. Verify
  against the production portal once the team key exists.

---

## Summary

| ID | Source | Category | Status | Class |
|---|---|---|---|---|
| S01 | data.gov.in: District-wise season-wise crop production | Production / area | **VERIFIED** | OBSERVED (official statistics) |
| S02 | UPAg: Unified Portal for Agricultural Statistics | Production / area / yield | CANDIDATE | OBSERVED / ESTIMATED |
| S03 | DES APY portals (aps.dac.gov.in, data.desagri.gov.in) | Production / area / yield | **BLOCKED** | OBSERVED |
| S04 | ICRISAT District Level Database (DLD) | Production / area / yield / more | CANDIDATE | OBSERVED + ESTIMATED |
| S05 | FAOSTAT | Production | NOT_SUITABLE | OBSERVED / ESTIMATED |
| S06 | data.gov.in: Variety-wise Daily Market Prices (historical) | Prices | **VERIFIED** | OBSERVED |
| S07 | data.gov.in: Current Daily Mandi Prices | Prices | **VERIFIED** | OBSERVED |
| S08 | AGMARKNET 2.0 portal | Prices + arrivals | CANDIDATE | OBSERVED |
| S09 | CEDA (Ashoka Univ.) Agmarknet API | Prices + arrivals | CANDIDATE | OBSERVED (third-party mirror) |
| S10 | eNAM | Prices + arrivals | **BLOCKED** | OBSERVED |
| S11 | IMD gridded rainfall 0.25° | Weather observations | **VERIFIED** | OBSERVED (gridded from gauges) |
| S12 | IMD gridded Tmax/Tmin 1.0° | Weather observations | CANDIDATE | OBSERVED (gridded) |
| S13 | IMD API (api.imd.gov.in) | Weather forecasts / nowcasts | **BLOCKED** | FORECAST / OBSERVED |
| S14 | NASA POWER | Weather (historical) | **VERIFIED** | ESTIMATED (reanalysis/satellite) |
| S15 | Open-Meteo (forecast + ERA5 archive) | Weather forecast + history | **VERIFIED** | FORECAST / ESTIMATED |
| S16 | Soil Health Card portal | Soil | **BLOCKED** | OBSERVED |
| S17 | ISRIC SoilGrids | Soil | **VERIFIED** | ESTIMATED (modelled) |
| S18 | Copernicus Sentinel-2 (CDSE) | Satellite | CANDIDATE | OBSERVED (remote sensing) |
| S19 | Landsat + MODIS via Microsoft Planetary Computer | Satellite / NDVI | CANDIDATE | OBSERVED (remote sensing) |
| S20 | Google Earth Engine | Satellite platform | CANDIDATE | n/a (platform) |
| S21 | PlantVillage | Disease images | CANDIDATE | OBSERVED (lab images) |
| S22 | PlantDoc | Disease images | CANDIDATE | OBSERVED (field images) |
| S23 | MoSPI HCES 2022-23 / 2023-24 | Demand proxy (consumption) | CANDIDATE | OBSERVED (survey) · **PROXY** |
| S24 | Mandi arrivals (via S08 / S09) | Demand / supply proxy | CANDIDATE | OBSERVED · **PROXY** |
| S25 | data.gov.in retail / wholesale price series (misc.) | Prices | NOT_SUITABLE | OBSERVED |

---

## A. Agricultural production, area, yield

### S01. District-wise, season-wise crop production statistics (data.gov.in) · **VERIFIED**
| Field | Value |
|---|---|
| Provider | Ministry of Agriculture & Farmers Welfare, Directorate of Economics & Statistics (via data.gov.in) |
| Official URL | https://www.data.gov.in/resource/district-wise-season-wise-crop-production-statistics-1997 |
| Access method | REST API `https://api.data.gov.in/resource/35be999b-0208-4354-b557-f6ca9a5355de` (JSON/CSV/XML); filters on `state_name`, `crop`, `crop_year` confirmed working |
| Authentication | data.gov.in API key |
| License / terms | Government Open Data License – India (GODL), per the data.gov.in footer. Attribution required. |
| Geographic coverage | All India (all states/UTs) |
| Temporal coverage | **1997 to 2014 in practice.** Rows per year: 1997: 8,899 · 2010: 14,065 · 2014: 10,973 · 2015: **562** (partial) · 2016+: **0** |
| Granularity | District × season (Kharif / Rabi / Whole Year / …) × crop × year |
| Variables | `state_name`, `district_name`, `crop_year`, `season`, `crop`, `area_`, `production_` |
| Units | Area in hectares, production in tonnes (dataset description). **Exceptions per crop, e.g. coconut in nuts and cotton in bales, are UNVERIFIED and must be checked.** Sample: UP Agra potato 1997 = 14,430 ha, 397,344 t (≈27.5 t/ha, plausible) |
| Actual test result | 246,091 total rows. Sample rows returned. Resource last updated 2021-07-13. |
| Coverage checks (rows) | UP: Potato 1,276 · Onion 1,736 · Wheat 1,275 · Maize 2,552 · **Tomato 0**. Maharashtra: Maize 1,056 · Wheat 518 · Onion 26 · Tomato 30 · Potato 0. Karnataka: Maize 1,331 · Onion 837 · Potato 447 · Wheat 266 · Tomato 50. UP potato 2014 = 75 districts. |
| ML task | Supply forecasting (target = production), yield (production / area), crop suitability evidence |
| Limitations | **Ends in 2014** (2015 partial). Horticulture crops (tomato, many vegetables) are missing or sparse. District boundaries change over time (new districts). Yield isn't a field and has to be derived. |
| Data quality concerns | Zero/blank values vs. missing need checking. Crop names need normalising. Season labels vary. Duplicate rows for the same district-crop-season-year are UNVERIFIED. |
| Preprocessing effort | Medium: district boundary harmonisation, crop-name mapping, unit exceptions |

### S02. UPAg: Unified Portal for Agricultural Statistics · CANDIDATE
| Field | Value |
|---|---|
| Provider | Ministry of Agriculture & Farmers Welfare (launched Sept 2023) |
| Official URL | https://upag.gov.in · dashboard https://dash.upag.gov.in/apy |
| Access method | Web dashboard (Plotly Dash app); both URLs return HTTP 200. **Export / API: UNVERIFIED** |
| Authentication | None needed to view. Download: UNVERIFIED |
| License / terms | **UNKNOWN** |
| Geographic coverage | All India; a third-party mirror (dataful.in) shows district-level UP data |
| Temporal coverage | Reported as 1997-98 to 2024-25 (secondary sources). **UNVERIFIED** |
| Granularity | District × season × crop × year (reported) |
| Variables / units | Area (ha), production (t), yield (t/ha), as reported. UNVERIFIED |
| Actual test result | Site reachable; data not retrieved |
| ML task | Supply / yield. **This would fill S01's 2015–2025 gap.** |
| Limitations | No documented programmatic access. Values are triangulated estimates (reported). |
| Preprocessing effort | Unknown until access is confirmed |

### S03. DES Area-Production-Yield portals · **BLOCKED**
| Field | Value |
|---|---|
| Provider | Directorate of Economics & Statistics, MoA&FW |
| Official URL | https://aps.dac.gov.in/APY/Index.htm · https://data.desagri.gov.in/website/crops-apy-report-web |
| Test result | Both **timed out** on repeated attempts (connection code 000) |
| Other fields | UNVERIFIED |
| Notes | Historically the upstream source of S01. Recheck later; don't depend on it. |

### S04. ICRISAT District Level Database (DLD) · CANDIDATE
| Field | Value |
|---|---|
| Provider | ICRISAT (with TCI Cornell) |
| Official URL | http://data.icrisat.org/dld/ · docs: https://dataverse.icrisat.org/dataset.xhtml?persistentId=doi:10.21421/D2/XFB1BZ |
| Access method | Web portal with category pages (crops, irrigation, prices, biophysical, …). Download mechanism is JavaScript-driven: **UNVERIFIED** |
| Authentication | UNVERIFIED |
| License / terms | "ICRISAT Data Sharing Agreement" (Aug 2020) says datasets are international public goods with unrestricted public access, and binds users to its terms of use. Dataverse lists `license: NONE` and points to that agreement. Treat as **open with terms; read before use.** |
| Geographic coverage | India, 1966-boundary "apportioned" districts (19–20 major states) plus an unapportioned set |
| Temporal coverage | 1966 to about 2015-17 (dataverse metadata starts 1966; end year UNVERIFIED) |
| Variables | Crop area/production/yield, irrigation, inputs, prices, rainfall and TerraClimate-derived climate, census, GDP |
| Actual test result | Portal and dataverse reachable. Dataverse API returned metadata and 3 documentation files (not data). |
| ML task | Supply / yield history with harmonised district boundaries |
| Limitations | Stale (ends around the mid-2010s). Apportioning is an estimate. |
| Preprocessing effort | Low–medium (already harmonised) |

### S05. FAOSTAT · NOT_SUITABLE
- **URL:** https://www.fao.org/faostat
- **Test:** the bulk download endpoint responded (HTTP 206).
- **License:** CC BY 4.0 (FAO).
- **Why not suitable:** national-level only, which is too coarse for district or regional supply. Could still serve as a national sanity check.

---

## B. Mandi prices and arrivals

### S06. Variety-wise Daily Market Prices Data of Commodity (data.gov.in) · **VERIFIED**
| Field | Value |
|---|---|
| Provider | MoA&FW, Department of Agriculture & Farmers Welfare (AGMARKNET data, via data.gov.in) |
| Official URL | https://www.data.gov.in (resource `35985678-0d79-46b4-9ed6-6f13308a1d24`) |
| Access method | REST API `https://api.data.gov.in/resource/35985678-0d79-46b4-9ed6-6f13308a1d24`; exposed filters `State`, `District`, `Commodity`, `Arrival_Date` confirmed working |
| Authentication | data.gov.in API key |
| License / terms | GODL-India (site footer) |
| Geographic coverage | All India, market (APMC) level |
| Temporal coverage | Earliest observed in the sample: **2009**. Earlier years **UNVERIFIED** (rate-limited). **Current:** the resource was updated 2026-09-23 and 22/09/2026 alone has 21,712 rows. 01/01/2015 has 10,713 rows. |
| Granularity | Market × commodity × variety × grade × day |
| Variables | `Arrival_Date`, `State`, `District`, `Market`, `Commodity`, `Commodity_Code`, `Variety`, `Grade`, `Min_Price`, `Max_Price`, `Modal_Price` |
| Units | **Not stated in the API.** The Agmarknet convention is ₹/quintal, but that is **UNVERIFIED** for this resource. Prices come back as **strings** (`keyword` type). |
| Actual test result | 82,004,712 total rows. Sample: West Bengal / Hooghly / Sheoraphuly / Potato / Jyoti / 11-11-2009, modal 1580. |
| Coverage checks (rows) | UP: Potato 826,017 · Onion 744,451 · Wheat 765,202 · Tomato 601,390 · Rice 571,981 · Paddy (Common) 252,107 · Maize 192,552. Maharashtra: Onion 244,485 · Maize 199,430 · Tomato 170,808 · Potato 111,648. MP: Maize 355,704 · Onion 161,495. Karnataka: Maize 177,557 · Tomato 121,341 · Onion 102,597. AP and Telangana are much smaller (13k–123k). |
| ML task | Price anomaly detection, market signals, price features for supply/demand; context for the backend's market risk |
| Limitations | **No arrival quantity field.** Varieties and grades are mixed. Market reporting is irregular (gaps are not zeros). Can't pull in bulk through the rate-limited sample key. |
| Data quality concerns | String→numeric parsing, outliers or typos in prices, inconsistent commodity naming (`Rice` vs `Paddy(Dhan)(Common)`), date format `DD/MM/YYYY` |
| Preprocessing effort | Medium–high (volume, cleaning, variety aggregation) |

### S07. Current Daily Price of Various Commodities from Various Markets (data.gov.in) · **VERIFIED**
| Field | Value |
|---|---|
| URL / access | API resource `9ef84268-d588-465a-a308-a864a43d0070`, API key required |
| License | GODL-India |
| Test result | 18,801 rows, **all for the current day** (e.g. 24/09/2026, Azamgarh APMC, Groundnut, modal 9753) |
| Variables | `state`, `district`, `market`, `commodity`, `variety`, `grade`, `arrival_date`, `min_price`, `max_price`, `modal_price` (numeric) |
| Limitations | **Today's snapshot only, with no history.** No arrival quantities. Useful for live inference inputs, not for training. |
| ML task | Live features for anomaly detection and market signals |

### S08. AGMARKNET 2.0 portal · CANDIDATE
| Field | Value |
|---|---|
| Provider | Directorate of Marketing & Inspection (DMI), MoA&FW |
| Official URL | https://agmarknet.gov.in (upgraded to "Agmarknet 2.0" in Nov 2025, per press coverage) |
| Access method | React web app with price and arrival reports (e.g. `/daily-price-and-arrival-report`). The app calls an **undocumented** backend at `api.agmarknet.gov.in/v1/`. That backend isn't a published API, so we don't use it. |
| Authentication | None to view. Official bulk export: **UNVERIFIED** |
| License / terms | **UNKNOWN** for the portal itself. The same data on data.gov.in (S06/S07) is GODL. |
| Coverage | ~4,300+ mandis (reported). Arrival quantities are shown in reports (reported). |
| Test result | Site reachable; page content is rendered client-side. No official export was confirmed. |
| ML task | **Would be the official source of arrival quantities**, which S06 lacks |
| Limitations | No documented programmatic access. Scraping the internal API is not sanctioned. |

### S09. CEDA Agri Market Data API (Ashoka University) · CANDIDATE
| Field | Value |
|---|---|
| Provider | Centre for Economic Data & Analysis (CEDA), Ashoka University. Third-party; attributes data to DMI, MoA&FW |
| Official URL | https://agmarknet.ceda.ashoka.edu.in · API docs: https://api.ceda.ashoka.edu.in/documentation/ |
| Access method | REST API, confirmed from its OpenAPI spec: `GET /agmarknet/commodities`, `GET /agmarknet/geographies`, `POST /agmarknet/markets`, `POST /agmarknet/prices`, **`POST /agmarknet/quantities`** at national/state/district/market level. The web portal also has "Download Data". |
| Authentication | **Bearer JWT** (registration required) |
| License / terms | **UNKNOWN** (no license in the API spec or on the portal page) |
| Coverage | Temporal coverage UNVERIFIED |
| Test result | API spec retrieved. **No data retrieved** (no token). |
| ML task | **Arrival quantities**: supply-side market signal and demand PROXY |
| Limitations | Third-party mirror, so aggregation methods need checking against S06. License unclear. |

### S10. eNAM · **BLOCKED**
- **Provider / URL:** SFAC, MoA&FW · https://enam.gov.in/web/
- **Access:** public dashboards only ("Trading Details", "Live Price", "Agmarknet Price Dashboard", "eNAM vs AGMARKNET"), plus ~30 MIS reports for logged-in stakeholders.
- **Evidence:** the site is reachable. We found no documented public download or API.
- **License:** UNKNOWN. **Coverage:** eNAM-integrated mandis only, a subset of Agmarknet markets.
- **Status reason:** no official programmatic or bulk access for historical price and arrival data.

---

## C. Weather observations and forecasts

### S11. IMD gridded daily rainfall 0.25° · **VERIFIED**
| Field | Value |
|---|---|
| Provider | India Meteorological Department, Pune |
| Official URL | https://www.imdpune.gov.in/cmpg/Griddata/Rainfall_25_NetCDF.html |
| Access method | HTML form POST (`RF25.php`, field `RF25=<year>`) returns yearly NetCDF (`ind<year>_rfp25.nc`). Binary format also available. |
| Authentication | None |
| License / terms | **UNCLEAR.** The site disclaimer says "Reproducing the material … for commercial purpose is not permitted, unless … permission is obtained". Research and non-commercial use is common practice but isn't stated as a license. |
| Coverage | India, 6.5–38.5°N, 66.5–100°E (135×129 grid). **1901–2024** (page text; the form also lists 2025). |
| Granularity / units | 0.25° grid × day; rainfall in mm |
| Actual test result | Downloaded the first 4 KB of 2023: valid NetCDF (`CDF\x01`, `LONGITUDE` dimension) |
| ML task | Weather features for supply, yield and suitability; rainfall anomaly detection |
| Limitations | Gauge-interpolated, so sparse-gauge areas are smoother. Needs district aggregation (zonal statistics). |
| Preprocessing effort | Medium (grid → district) |

### S12. IMD gridded daily Tmax / Tmin 1.0° · CANDIDATE
- **URL:** https://www.imdpune.gov.in/cmpg/Griddata/Max_1_Bin.html and `Min_1_Bin.html`
- **Coverage:** reported as 1951–2024, 31×31 grid, °C.
- **Status:** same portal and access pattern as S11, but not tested directly. Only 1° resolution (≈100 km), which is coarse at district level.
- **License:** same as S11 (UNCLEAR).

### S13. IMD API (api.imd.gov.in) · **BLOCKED**
- **URL:** https://api.imd.gov.in/public/api_reference.html. The API list is public: city forecast (7-day), district rainfall forecast (5-day), current weather, AWS/ARG data, nowcasts, warnings, agromet advisories and more.
- **Test:** `GET /api/v1/cityforecast?id=42182` returned **HTTP 401 `{"error":"API key missing"}`**. Secondary sources report that access needs IP whitelisting through IMD.
- **License:** UNKNOWN. **Historical depth:** mainly current and forecast data (UNVERIFIED).
- **Status reason:** needs an institutional key or whitelisting that the team doesn't have.

### S14. NASA POWER · **VERIFIED**
| Field | Value |
|---|---|
| Provider | NASA Langley Research Center |
| URL / access | https://power.larc.nasa.gov/api/temporal/daily/point (REST, JSON/CSV), POWER Daily API v2.10.0 |
| Authentication | None |
| License / terms | NASA open data (no usage restriction; citation requested). Exact wording UNVERIFIED in this audit. |
| Coverage | Global, 1981 to near-present (per NASA documentation) |
| Granularity / units | Point queries on a ~0.5° MERRA-2 / 1° CERES grid × day. T2M in °C, PRECTOTCORR in mm/day. |
| Actual test result | Bengaluru 2024-01-01..03: T2M 21.79/22.42/22.27 °C, precipitation 0.0/0.0/0.01 |
| ML task | Temperature, humidity and radiation features where IMD is coarse |
| Limitations | Reanalysis/satellite-derived, so **ESTIMATED**, not station observations. Coarse. |

### S15. Open-Meteo (forecast + historical ERA5) · **VERIFIED**
| Field | Value |
|---|---|
| URL / access | https://api.open-meteo.com/v1/forecast · https://archive-api.open-meteo.com/v1/archive |
| Authentication | None (free tier) |
| License / terms | Data **CC BY 4.0** (attribution). Free API is **non-commercial only**, ≤10,000 calls/day. |
| Coverage | Global. Forecast up to 16 days. Archive from 1940 (ERA5) / 1950 (ERA5-Land). |
| Actual test result | Bengaluru daily forecast (precip 0.7 / 6.1 mm, tmax 29 / 28 °C) and the 2000-01-01 archive returned |
| ML task | **Weather forecasts as model inputs** (dataClassification FORECAST); historical ESTIMATED features |
| Limitations | Not IMD; model output, not observations. Commercial deployment needs a paid plan. |

---

## D. Soil

### S16. Soil Health Card portal · **BLOCKED**
- **URL:** https://soilhealth.dac.gov.in (nutrient dashboard at `/nutrient-dashboard`).
- **Access:** a React/GraphQL web app. Public reports are said to cover nutrient status by district, block and village, but there is **no documented export or API**. Farmer-level cards are looked up individually.
- **License:** UNKNOWN.
- **Status reason:** no sanctioned bulk access. Recheck for district-aggregate report exports.

### S17. ISRIC SoilGrids 2.0 · **VERIFIED**
| Field | Value |
|---|---|
| URL / access | https://rest.isric.org/soilgrids/v2.0/properties/query (REST point query). WCS/GeoTIFF also available. |
| Authentication | None |
| License / terms | CC BY 4.0 (ISRIC). Exact page not re-checked in this audit: UNVERIFIED. |
| Coverage | Global, 250 m, 6 depth intervals; a static product (no time dimension) |
| Variables / units | pH (pH×10), SOC, N, sand/silt/clay, CEC, bulk density… The API reports `d_factor` scaling. |
| Actual test result | Bengaluru pH (H₂O) 0–5 cm mean = 64 → **pH 6.4** |
| ML task | Soil features for crop suitability |
| Limitations | **ESTIMATED** (machine-learning model), not farm lab tests. Must not be labelled OBSERVED. The backend's `REGIONAL_ESTIMATE` source fits here. |

---

## E. Satellite / environmental indicators

### S18. Copernicus Sentinel-2 L2A (Copernicus Data Space Ecosystem) · CANDIDATE
- **URL:** https://dataspace.copernicus.eu, with STAC at `https://stac.dataspace.copernicus.eu/v1`.
- **Auth:** none for catalogue search. **Downloads need a free account and access token.**
- **License:** free, full and open (Copernicus Sentinel data legal notice).
- **Test:** STAC search returned `S2B_MSIL2A_20250127T051009_…_T43PGQ` over Bengaluru.
- **Coverage:** global, 10–20 m, ~5-day revisit, 2015 onwards.
- **Why not VERIFIED:** actual imagery download wasn't tested.
- **ML task:** NDVI/EVI crop-condition features.
- **Effort:** high (cloud masking, compositing, district zonal statistics).

### S19. Landsat C2 L2 and MODIS 13Q1 NDVI via Microsoft Planetary Computer · CANDIDATE
- **URL:** https://planetarycomputer.microsoft.com/api/stac/v1
- **Auth:** none for search. Asset access uses free SAS tokens (not tested).
- **License:** Landsat is USGS public domain; MODIS is NASA open. The platform's terms were not reviewed.
- **Test:** STAC returned `LC08_L2SP_144051_20250124_02_T1` (Landsat 8) and `MYD13Q1.A2025057.h25v07.061` (MODIS 16-day NDVI, 250 m).
- **ML task:** a MODIS NDVI time series is the cheapest vegetation signal (250 m, 2000 onwards).
- **Effort:** medium.

### S20. Google Earth Engine · CANDIDATE (not tested)
- **URL:** https://earthengine.google.com
- **Access:** needs a Google Cloud project registered for Earth Engine.
- **Terms:** free for non-commercial, research and education use. Since 27 Apr 2026, non-commercial projects have monthly compute quotas. **Commercial and operational use needs a paid subscription.**
- **Note:** convenient for zonal statistics, but its license depends on how the product is deployed.

---

## F. Crop disease images

### S21. PlantVillage · CANDIDATE (license verified per distribution; content not downloaded)
| Field | Value |
|---|---|
| Provider | Hughes & Salathé (Penn State PlantVillage), 2015 |
| Distributions and license (checked from repository metadata) | Hugging Face `mohanty/PlantVillage`: **CC BY-SA 3.0** (`license:cc-by-sa-3.0`). Mendeley `tywbtsjrjv/1` (augmented version by Geetharamani & Pandian): **CC0 1.0**. GitHub `spMohanty/PlantVillage-Dataset`: **no license file**, so treat that copy as UNKNOWN. |
| Content | ~54,300 leaf images, 38 classes (14 crops, 26 diseases + healthy), including **potato early/late blight**, tomato and maize diseases |
| Test result | License metadata retrieved via the Mendeley and Hugging Face APIs. Images not downloaded. |
| ML task | Disease classification (image → class probabilities) |
| Limitations | **Lab images on a uniform background.** Models trained on it transfer poorly to field photos. The augmented Mendeley copy contains augmented duplicates, which risks train/test leakage. Crops aren't India-specific. ShareAlike terms (HF copy) apply to redistributed derivatives. |

### S22. PlantDoc · CANDIDATE
- **URL:** https://github.com/pratikkayal/PlantDoc-Dataset (CODS-COMAD 2020).
- **License:** **CC BY 4.0**, verified via GitHub repo metadata.
- **Content:** ~2,600 **field** images, 13 species, 17 disease classes (per the paper), including potato and tomato classes. Counts not re-verified.
- **Use:** field-condition evaluation and fine-tuning alongside PlantVillage.
- **Limitation:** small, with some label noise reported in the literature.

---

## G. Demand / consumption proxies

India has no public retail or POS sales data at usable granularity. **Everything in this
section is a PROXY and must never be presented as actual demand.**

### S23. MoSPI Household Consumption Expenditure Survey (HCES) 2022-23 / 2023-24 · CANDIDATE · **PROXY**
- **URL:** https://microdata.gov.in/NADA/index.php/catalog/237 (2023-24) and `/catalog/224` (2022-23).
- **Test:** microdata.gov.in **timed out** during this audit. Access conditions (registration or licensed use) are **UNVERIFIED**.
- **Content (reported):** household-level quantities and values of food items consumed. Survey estimates are reliable at state (sometimes NSS-region) level and usually not at district level.
- **ML use:** per-capita consumption × population gives a **baseline consumption proxy** by state.
- **Limitation:** survey periods only, not a continuous time series.

### S24. Mandi arrivals (quantities via S08 AGMARKNET / S09 CEDA) · CANDIDATE · **PROXY**
- **What it measures:** arrivals are the quantity **brought to the market**. That is a supply-side flow, and at best a **market-throughput proxy** for demand. It is not consumption.
- **Status:** CANDIDATE because no official, verified programmatic source for arrival quantities exists yet (S06/S07 have prices only).

### S25. Miscellaneous data.gov.in price series · NOT_SUITABLE
- **Examples:** "Weekly wholesale price of Potato / Onion / Wheat / Rice upto 2012", "All India Yearly Retail Average Prices of 22 Essential Commodities 2018–2022", MSP tables.
- **Why not suitable:** too coarse or too short, and superseded by S06. Retail prices are **not** demand.
- **Useful as:** a sanity check for price units and levels.
