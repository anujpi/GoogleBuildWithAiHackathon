# India Crop Production Database — State → District (Zilla) → Crop

What every district in India grows, how much land goes to each crop, how much it produces and its yield.

- **Year:** 2019-20 (`crop_year = 2019`), the latest complete year in the public district file
- **Coverage:** 34 states/UTs · 700 districts · 55 crops · 6 seasons · 19,264 records
- **Source:** Directorate of Economics & Statistics (DES), Ministry of Agriculture & Farmers Welfare, *District-wise, season-wise crop production statistics* (data.gov.in). Raw file: `data/raw/des_district_season_apy_1997_2020.csv` (all years from 1997 onward)

## Files

| File | What it is |
|---|---|
| `output/crop_production.sqlite` | Normalised SQLite database with ready-made views (use this) |
| `output/crop_production.csv` | Flat table: one row per state / district / crop / season |
| `output/district_summary.csv` | One row per district: number of crops, total crop area, top 5 crops, full crop list |
| `build_db.py` | Rebuilds everything from the raw file (any year, or all years) |
| `queries.sql` | Example queries |
| `data/raw/…csv` | Untouched source file, 1997-98 to 2019-20 |

## Schema

```
states(state_id, state_name)
districts(district_id, state_id → states, district_name)
crops(crop_id, crop_name, crop_group, production_unit, is_aggregate)
production(id, crop_year, year_label, district_id → districts, crop_id → crops,
           season, area_ha, production, yield_per_ha, split_record)
meta(key, value)            -- source and years
```

Views (joined and easy to query):

| View | Grain |
|---|---|
| `v_production` | Every record with state, district and crop names attached |
| `v_district_crop` | District × crop, seasons added together (aggregate rows excluded) |
| `v_state_crop` | State × crop totals, plus how many districts grow it |
| `v_district_summary` | District profile: crops grown, total area, top 5 crops by area |

## Units: read before adding numbers

- `area_ha`: hectares
- `production`: in `crops.production_unit`, which is **tonnes** for most crops, except:
  - Coconut → **nuts**
  - Cotton(lint) → **bales of 170 kg**
  - Jute, Mesta, Sannhamp → **bales of 180 kg**
- `yield_per_ha` = production ÷ area, so it is tonnes/ha, nuts/ha or bales/ha to match.
- Never add production across crops with different units.

## Things to know

- **Crop year:** `2019` means the agricultural year July 2019 to June 2020 (`year_label = '2019-20'`).
- **Seasons:** Kharif, Rabi, Summer, Winter, Autumn and Whole Year. A crop can have several rows in one district (e.g. rice in Kharif and Rabi); `v_district_crop` sums them.
- **`Oilseeds total`** is a sum of the individual oilseed rows (`is_aggregate = 1`). The summary views exclude it; exclude it yourself when you total `production` directly.
- **`split_record = 1`** (36 rows): the source has two rows for the same district/crop/season, such as two tobacco types in Andhra Pradesh or two garlic entries in Uttarakhand. Both are kept; add them together for the district total.
- **Names:** districts are UPPERCASE as published by DES. The same name can appear in two states (e.g. AURANGABAD in Bihar and Maharashtra), so always filter by state as well.
- **Totals vs national estimates:** these are district figures as reported by states. Added up nationally, they come out higher than DES's all-India final estimates for 2019-20 (rice about 135 vs 119 Mt, wheat about 132 vs 108 Mt), mainly because of Madhya Pradesh wheat and Telangana rice. Use this data for district and state comparisons, and quote DES national estimates for all-India totals.
- Horticulture beyond the crops listed here (most fruits and vegetables) is not in this dataset.
- 264 rows have area but no production reported (`production IS NULL`).

## Rebuild or extend

```bash
pip install pandas
python build_db.py                 # latest complete year (2019-20)
python build_db.py --year 2015     # any single year
python build_db.py --all-years     # 1997-98 to 2019-20 in one database
```

**Newer years (2020-21 to 2023-24):** download the district-wise APY file from UPAg (upag.gov.in → Reports → DES District Data) or data.desagri.gov.in, then run
`python build_db.py --raw path/to/that.csv`. The script trims and renames the usual column headers (`State`/`State_Name`, `District`/`District_Name`, `Crop`, `Crop_Year`, `Season`, `Area`, `Production`, `Yield`). If the new file uses other headers or writes the year as `2023-24`, add a mapping in `clean()`.

## Interactive map (`web/`)

A static, no-backend web map: pan and zoom and the side list shows the states (zoomed out) or districts (zoomed in, level 6+) currently in view, like "restaurants near here" but for crops. Hover a list item or a district to highlight the other, click a district for all its crops with a top-10 bar chart, search a district or state by name, and pick a crop to see only the districts growing it, coloured and ranked by its production (or area / yield). On a phone the list becomes a bottom sheet.

```bash
cd web
python -m http.server 8000      # then open http://localhost:8000
```

It loads Leaflet and topojson-client from jsDelivr and OpenStreetMap tiles; no API keys.

| File | What it is |
|---|---|
| `web/index.html`, `web/app.js`, `web/styles.css` | The app |
| `web/data/india.topo.json` | Simplified district + state boundaries (built) |
| `web/data/crops.json` | Crop data per map unit, keyed by boundary ID (built) |
| `data/geo/raw/` | Downloaded geoBoundaries files + their metadata (only needed to rebuild) |
| `data/geo/india.topo.json` | Same boundaries as the web copy |
| `data/geo/district_name_map.csv` | DES district → boundary polygon(s): how each was matched, with manual fixes |

**Boundaries:** [geoBoundaries](https://www.geoboundaries.org) India ADM2 (districts, from LGD, 2021, ODbL 1.0). Each district is tagged with its state by largest overlap with geoBoundaries ADM1 (DataMeet, CC BY 2.5 IN), and the state layer is dissolved from the districts so both layers share borders.

### Rebuild

Needs Python 3 (standard library only) and Node.js (mapshaper runs through `npx`).

```bash
python scripts/build_geo.py          # download (once) + simplify boundaries -> data/geo, web/data
python scripts/match_districts.py    # match DES names to polygons -> data/geo/district_name_map.csv
python scripts/build_web_data.py     # SQLite -> web/data/crops.json
```

Re-run `build_web_data.py` after rebuilding the database. `build_geo.py --keep 10` keeps more boundary detail (default 6% of vertices, about 1.2 MB).

### District name matching

DES names (UPPERCASE, e.g. `PARAGANAS NORTH`) differ from the boundary names (`North Twenty Four Parganas`). `match_districts.py` never matches across states. Within a state it tries, in order: your manual rows, exact match after normalising, spelling and direction aliases (Purba = East, Paschim = West, 24 = Twenty Four, bracketed parts, spaces), word subsets (`KANKER` = `Uttar Bastar Kanker`), then fuzzy similarity ≥ 0.75. It prints the fuzzy matches to check, every unmatched district, and the polygons left without data.

Current result: **700 / 700 DES districts matched**: 584 exact, 43 alias, 10 subset, 42 fuzzy (all checked by hand) and 21 manual rows (renamed districts such as BELAGAVI = Belgaum, AMROHA = Jyotiba Phule Nagar, plus the special cases below). 21 polygons have no DES data: Manipur (16 districts, not in the dataset), Lakshadweep, and the cities Mumbai, Kolkata and Hyderabad, plus Leh (DES reports only Kargil for Ladakh).

To fix a match, edit `data/geo/district_name_map.csv`: put the boundary name(s) in `geo_names` (several polygons: `A|B`), set `method` to `manual`, and re-run `match_districts.py` then `build_web_data.py`. Manual rows are kept on every re-run; the other rows are recomputed.

Special cases, handled as map units rather than one-to-one matches:
- `DELHI_TOTAL` is drawn over all 11 Delhi districts.
- Districts carved out recently that DES 2019-20 still reports inside their parent: the new polygon is drawn with the parent (Nagapattinam + Mayiladuthurai; Aizawl + Saitual, Champhai + Khawzawl, Lunglei + Hnahthial).
- `CHARKI DADRI` has no polygon of its own in the boundary file; it shares Bhiwani's and the map shows "Bhiwani + Charki Dadri" with crops added per crop (same unit per crop, so this is safe).
- Yanam (Puducherry) sits inside Andhra Pradesh; `build_geo.py` fixes its state.

Production units are kept per crop throughout (tonnes; nuts for Coconut; bales of 170 kg for Cotton; bales of 180 kg for Jute, Mesta, Sannhamp) and "Oilseeds total" is excluded.
