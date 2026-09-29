"""
Build a state -> district (zilla) -> crop database for India from the
DES "District-wise, season-wise crop production statistics" file.

Usage:
    python build_db.py                      # latest complete year in the raw file
    python build_db.py --year 2019          # a specific crop year (2019 = 2019-20)
    python build_db.py --all-years          # every year in the file
    python build_db.py --raw path/to/newer.csv --year 2023

Outputs (in ./output):
    crop_production.sqlite   normalised SQLite database with views
    crop_production.csv      flat table, one row per state/district/crop/season
    district_summary.csv     one row per district: crops grown, top crops
"""
import argparse
import re
import sqlite3
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).parent
DEFAULT_RAW = ROOT / "data" / "raw" / "des_district_season_apy_1997_2020.csv"
OUT = ROOT / "output"

# --- clean-up tables ------------------------------------------------------

STATE_FIX = {
    "Andaman and Nicobar Island": "Andaman and Nicobar Islands",
    "CHANDIGARH": "Chandigarh",
    "Laddak": "Ladakh",
    "THE DADRA AND NAGAR HAVELI": "Dadra and Nagar Haveli and Daman and Diu",
    "Dadra and Nagar Haveli": "Dadra and Nagar Haveli and Daman and Diu",
}

CROP_FIX = {
    "Other  Rabi pulses": "Other Rabi pulses",
    "other oilseeds": "Other oilseeds",
    "Rapeseed &Mustard": "Rapeseed & Mustard",
}

CROP_GROUP = {
    "Cereals": ["Rice", "Wheat", "Maize", "Jowar", "Bajra", "Ragi", "Barley",
                "Small millets", "Other Cereals"],
    "Pulses": ["Arhar/Tur", "Gram", "Moong(Green Gram)", "Urad", "Masoor",
               "Horse-gram", "Khesari", "Moth", "Cowpea(Lobia)",
               "Peas & beans (Pulses)", "Other Kharif pulses",
               "Other Rabi pulses", "Other Summer Pulses"],
    "Oilseeds": ["Groundnut", "Rapeseed & Mustard", "Soyabean", "Sunflower",
                 "Sesamum", "Castor seed", "Linseed", "Safflower",
                 "Niger seed", "Other oilseeds", "Oilseeds total"],
    "Fibres": ["Cotton(lint)", "Jute", "Mesta", "Sannhamp"],
    "Sugar": ["Sugarcane"],
    "Spices & condiments": ["Black pepper", "Cardamom", "Coriander",
                            "Dry chillies", "Garlic", "Ginger", "Turmeric"],
    "Vegetables & tubers": ["Onion", "Potato", "Sweet potato", "Tapioca"],
    "Fruits & plantation": ["Banana", "Coconut", "Arecanut", "Cashewnut"],
    "Other commercial": ["Tobacco", "Guar seed"],
}
GROUP_OF = {c: g for g, cs in CROP_GROUP.items() for c in cs}

# Production units as published by DES; everything else is tonnes.
UNIT = {
    "Coconut": "nuts",
    "Cotton(lint)": "bales (170 kg)",
    "Jute": "bales (180 kg)",
    "Mesta": "bales (180 kg)",
    "Sannhamp": "bales (180 kg)",
}
AGGREGATES = {"Oilseeds total"}  # sum of other rows -> exclude when totalling


def clean(raw: Path) -> pd.DataFrame:
    df = pd.read_csv(raw)
    df.columns = [re.sub(r"\s+", "_", c.strip()) for c in df.columns]
    df = df.rename(columns={
        "State_Name": "State", "District_Name": "District",
        "Area": "area_ha", "Production": "production", "Yield": "yield_src",
    })
    for c in ["State", "District", "Crop", "Season"]:
        df[c] = df[c].astype(str).str.strip().str.replace(r"\s+", " ", regex=True)
    df["State"] = df["State"].replace(STATE_FIX)
    df["District"] = df["District"].str.upper()
    df["Crop"] = df["Crop"].replace(CROP_FIX)
    df["Crop_Year"] = df["Crop_Year"].astype(int)
    return df


def latest_complete_year(df: pd.DataFrame) -> int:
    counts = df["Crop_Year"].value_counts().sort_index()
    # a year counts as complete if it has at least half the median row count
    full = counts[counts >= counts.median() * 0.5]
    return int(full.index.max())


def build(df: pd.DataFrame, years: list[int]) -> None:
    df = df[df["Crop_Year"].isin(years)].copy()
    df["year_label"] = df["Crop_Year"].map(lambda y: f"{y}-{str(y + 1)[-2:]}")
    df["crop_group"] = df["Crop"].map(GROUP_OF).fillna("Other")
    df["production_unit"] = df["Crop"].map(UNIT).fillna("tonnes")
    df["is_aggregate"] = df["Crop"].isin(AGGREGATES).astype(int)
    # recompute yield from area & production (unit per hectare)
    df["yield_per_ha"] = (df["production"] / df["area_ha"]).where(df["area_ha"] > 0).round(3)
    key = ["Crop_Year", "State", "District", "Crop", "Season"]
    df["split_record"] = df.duplicated(key, keep=False).astype(int)

    OUT.mkdir(exist_ok=True)
    db_path = OUT / "crop_production.sqlite"
    db_path.unlink(missing_ok=True)
    con = sqlite3.connect(db_path)
    con.executescript(SCHEMA)

    states = sorted(df["State"].unique())
    con.executemany("INSERT INTO states(state_name) VALUES (?)", [(s,) for s in states])
    sid = dict(con.execute("SELECT state_name, state_id FROM states"))

    dists = df[["State", "District"]].drop_duplicates().sort_values(["State", "District"])
    con.executemany("INSERT INTO districts(state_id, district_name) VALUES (?,?)",
                    [(sid[s], d) for s, d in dists.itertuples(index=False)])
    did = {(s, d): i for i, s, d in con.execute(
        "SELECT d.district_id, s.state_name, d.district_name FROM districts d JOIN states s USING(state_id)")}

    crops = df[["Crop", "crop_group", "production_unit", "is_aggregate"]].drop_duplicates().sort_values("Crop")
    con.executemany("INSERT INTO crops(crop_name, crop_group, production_unit, is_aggregate) VALUES (?,?,?,?)",
                    crops.itertuples(index=False))
    cid = dict(con.execute("SELECT crop_name, crop_id FROM crops"))

    rows = [
        (r.Crop_Year, r.year_label, did[(r.State, r.District)], cid[r.Crop], r.Season,
         none(r.area_ha), none(r.production), none(r.yield_per_ha), r.split_record)
        for r in df.itertuples(index=False)
    ]
    con.executemany("""INSERT INTO production
        (crop_year, year_label, district_id, crop_id, season, area_ha, production, yield_per_ha, split_record)
        VALUES (?,?,?,?,?,?,?,?,?)""", rows)
    con.executescript(VIEWS)
    con.execute("INSERT INTO meta VALUES ('source', ?)", (SOURCE,))
    con.execute("INSERT INTO meta VALUES ('years', ?)", (", ".join(sorted(df['year_label'].unique())),))
    con.commit()

    flat = pd.read_sql("SELECT * FROM v_production ORDER BY state, district, crop, season", con)
    flat.to_csv(OUT / "crop_production.csv", index=False)
    pd.read_sql("SELECT * FROM v_district_summary ORDER BY state, district, crop_year", con) \
        .to_csv(OUT / "district_summary.csv", index=False)
    con.close()
    print(f"Built {db_path} with {len(rows):,} records, {len(states)} states, "
          f"{len(dists)} districts, {len(crops)} crops, years {sorted(set(df['year_label']))}")


def none(v):
    return None if pd.isna(v) else float(v)


SOURCE = ("Directorate of Economics & Statistics (DES), Ministry of Agriculture & Farmers Welfare - "
          "District-wise, season-wise crop production statistics (data.gov.in)")

SCHEMA = """
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE states (
    state_id   INTEGER PRIMARY KEY,
    state_name TEXT NOT NULL UNIQUE
);
CREATE TABLE districts (
    district_id   INTEGER PRIMARY KEY,
    state_id      INTEGER NOT NULL REFERENCES states(state_id),
    district_name TEXT NOT NULL,
    UNIQUE(state_id, district_name)
);
CREATE TABLE crops (
    crop_id         INTEGER PRIMARY KEY,
    crop_name       TEXT NOT NULL UNIQUE,
    crop_group      TEXT NOT NULL,
    production_unit TEXT NOT NULL,
    is_aggregate    INTEGER NOT NULL DEFAULT 0  -- 1 = a total of other rows (Oilseeds total)
);
CREATE TABLE production (
    id           INTEGER PRIMARY KEY,
    crop_year    INTEGER NOT NULL,             -- 2019 means agricultural year 2019-20
    year_label   TEXT NOT NULL,                -- '2019-20'
    district_id  INTEGER NOT NULL REFERENCES districts(district_id),
    crop_id      INTEGER NOT NULL REFERENCES crops(crop_id),
    season       TEXT NOT NULL,                -- Kharif, Rabi, Whole Year, Summer, Winter, Autumn
    area_ha      REAL,                         -- hectares
    production   REAL,                         -- in crops.production_unit
    yield_per_ha REAL,                         -- production / area_ha
    split_record INTEGER NOT NULL DEFAULT 0    -- 1 = source has >1 row for this key (sub-varieties)
);
CREATE INDEX ix_prod_district ON production(district_id);
CREATE INDEX ix_prod_crop ON production(crop_id);
CREATE INDEX ix_prod_year ON production(crop_year);
"""

VIEWS = """
CREATE VIEW v_production AS
SELECT p.id, p.crop_year, p.year_label, s.state_name AS state, d.district_name AS district,
       c.crop_name AS crop, c.crop_group, p.season, p.area_ha, p.production,
       c.production_unit, p.yield_per_ha, c.is_aggregate, p.split_record
FROM production p
JOIN districts d USING(district_id)
JOIN states s USING(state_id)
JOIN crops c USING(crop_id);

-- one row per district-crop-year, seasons added together (aggregates excluded)
CREATE VIEW v_district_crop AS
SELECT crop_year, year_label, state, district, crop, crop_group, production_unit,
       SUM(area_ha) AS area_ha, SUM(production) AS production,
       ROUND(SUM(production) / NULLIF(SUM(area_ha), 0), 3) AS yield_per_ha,
       GROUP_CONCAT(DISTINCT season) AS seasons
FROM v_production WHERE is_aggregate = 0
GROUP BY crop_year, state, district, crop;

-- one row per state-crop-year
CREATE VIEW v_state_crop AS
SELECT crop_year, year_label, state, crop, crop_group, production_unit,
       SUM(area_ha) AS area_ha, SUM(production) AS production,
       ROUND(SUM(production) / NULLIF(SUM(area_ha), 0), 3) AS yield_per_ha,
       COUNT(DISTINCT district) AS districts_growing
FROM v_district_crop
GROUP BY crop_year, state, crop;

-- what each district grows: crop count and top 5 crops by area
CREATE VIEW v_district_summary AS
WITH ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY crop_year, state, district ORDER BY area_ha DESC) AS rk
    FROM v_district_crop WHERE area_ha IS NOT NULL
)
SELECT crop_year, year_label, state, district,
       COUNT(*) AS crops_grown,
       ROUND(SUM(area_ha), 1) AS total_crop_area_ha,
       GROUP_CONCAT(CASE WHEN rk <= 5 THEN crop END, ', ') AS top5_crops_by_area,
       GROUP_CONCAT(crop, ', ') AS all_crops
FROM ranked
GROUP BY crop_year, state, district;
"""

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", type=Path, default=DEFAULT_RAW)
    ap.add_argument("--year", type=int, help="crop year, e.g. 2019 for 2019-20")
    ap.add_argument("--all-years", action="store_true")
    a = ap.parse_args()
    data = clean(a.raw)
    if a.all_years:
        yrs = sorted(data["Crop_Year"].unique())
    else:
        yrs = [a.year or latest_complete_year(data)]
    build(data, yrs)
