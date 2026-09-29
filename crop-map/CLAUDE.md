# Project notes for Claude

This folder is an India crop production database (state → district → crop), year 2019-20, from DES district-wise season-wise APY data.

- Query `output/crop_production.sqlite` with Python's `sqlite3` or pandas; the `sqlite3` CLI may not be installed.
- Prefer the views: `v_production`, `v_district_crop`, `v_state_crop`, `v_district_summary`. The schema and caveats are in README.md.
- Production units differ by crop (`crops.production_unit`): tonnes, except Coconut (nuts), Cotton(lint) (170 kg bales), and Jute/Mesta/Sannhamp (180 kg bales). Never sum production across different units.
- Exclude `is_aggregate = 1` ("Oilseeds total") when totalling.
- District names are UPPERCASE and are not unique across states, so always filter by state as well as district.
- To rebuild or change years, edit and run `build_db.py`; don't hand-edit the output files.
- The web map lives in `web/` (static; serve with `python -m http.server` inside `web/`). Its data files in `web/data/` are built by `scripts/build_geo.py`, `scripts/match_districts.py` and `scripts/build_web_data.py`; fix district name matches in `data/geo/district_name_map.csv` (method = manual), never in the built JSON.
