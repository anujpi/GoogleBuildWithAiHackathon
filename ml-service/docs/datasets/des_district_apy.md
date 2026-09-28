# DES district-wise season-wise crop production (all India, 1997-2019)

| | |
|---|---|
| Source | Directorate of Economics & Statistics (DES), Ministry of Agriculture & Farmers Welfare, via data.gov.in |
| File | `des_district_season_apy_1997_2020.csv` (21 MB, 345,336 rows); not in Git |
| Location | `data/raw/des_district_apy/` with a `manifest.json` |
| Coverage | 37 states/UTs, all districts, 55 crops, 6 seasons, crop years 1997-2019 (2020 has 319 rows and is partial) |
| Classification | OBSERVED (official statistics as reported by states) |
| Used by | Supply model `supply-xgb-v2` |

Same data as S01 for every state, so `datasets/crop_production.py` reads it unchanged. Its headers
are `State`, `District` rather than `State_Name`, `District_Name`; the loader maps them.

## Cleaning (see the model's `metadata.json` -> `cleaningReport`)

- 9 rows with no crop name dropped.
- 435 `Oilseeds total` rows dropped: they are sums of the individual oilseed rows.
- 4,944 rows with blank production dropped (missing, not zero).
- 1,168 rows dropped where the same district/crop/season/year appears twice with different values.
- Crop year 2020 dropped as partial (`--last-complete-year 2019`).

## Units

Production is tonnes except Coconut (nuts), Cotton(lint) (bales of 170 kg) and Jute, Mesta,
Sannhamp (bales of 180 kg). The API reports the right unit per crop (`unitsByCrop` in the model
metadata). Never add production across crops with different units.

## Train

```bash
python scripts/train_supply.py --raw-dir data/raw/des_district_apy \
    --last-complete-year 2019 --model-version supply-xgb-v2
```

Split: train <= 2015, validation 2016-2017, test 2018-2019.

## Related reference file

`data/reference/district_summary_2019.csv` (700 districts, 2019-20): crops grown, total crop area
and top-5 crops per district, built from the same source. It has no production figures, so it is
not used for training; it is kept for district profiles and for checking crop coverage.
