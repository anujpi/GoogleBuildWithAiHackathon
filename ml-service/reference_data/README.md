# reference_data/

Small, committed extracts of public datasets, used at inference time by `POST /v1/predict/demand`.
They're committed (unlike `data/`) because the service can't run without them and they total
about 65 KB. Nothing in them is modified, estimated or synthetic.

## faostat_fbs_india.csv

| Field | Value |
|---|---|
| Source | FAOSTAT Food Balance Sheets (FBS), bulk file `FoodBalanceSheets_E_Asia.zip` from `https://bulks-faostat.fao.org/production/` |
| Downloaded | 2026-09-30. Server last-modified 2025-10-28. zip sha256 `4d4356d2dcb9bfcc20f172142bdaae5cf69d4e90e3f250fe9aa090d922e04421` |
| License | FAO statistical data, CC BY 4.0 (FAO terms of use; re-check before commercial use) |
| Extract | `Area == "India"`; items Potatoes and products, Wheat and products, Rice and products, Onions, Maize and products, Bananas, Groundnuts, Soyabeans, Rape and Mustardseed, Sorghum and products, Barley and products, Sugar cane, Tomatoes and products, Population; elements Domestic supply quantity, Food, Food supply quantity (kg/capita/yr), Production, Import/Export quantity, Total Population; years 2010–2023 |
| Format | long: `item, element, unit, year, value` (the value is copied verbatim from `FoodBalanceSheets_E_Asia_NOFLAG.csv`) |
| Granularity | India, national, calendar/marketing year |
| Classification | OBSERVED (official statistics; FAO balance-sheet items are partly imputed by FAO) |
| Use | Demand **proxy**: apparent national use. It is not market or retail demand. |

## census2011_state_population.csv

| Field | Value |
|---|---|
| Source | Census of India 2011 state and UT populations, as tabulated in English Wikipedia, "List of states and union territories of India by population" (column "2011 Census Population"), fetched with the MediaWiki API on 2026-09-30 |
| License | Census figures are government statistics; the Wikipedia table is CC BY-SA 4.0 |
| Check | The 36 rows sum to 1,210,568,112, against the official Census 2011 total of 1,210,854,977 (the difference comes from rounding in the Ladakh / J&K split rows) |
| Caveats | Telangana and Andhra Pradesh are listed separately (post-2014 boundaries). The data is from 2011 and not projected forward. |
| Use | Apportioning national demand to states by population share only |
