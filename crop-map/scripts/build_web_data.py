"""Precompute web/data/crops.json from the SQLite database and the district name map.

One record per map unit, keyed by the matched boundary ID (geoBoundaries shapeID). A unit is
usually one DES district on one polygon. It can also be one DES district drawn over several
polygons (DELHI_TOTAL, or a district split after 2019-20), or several DES districts sharing one
polygon (BHIWANI + CHARKI DADRI). In the shared case the crop rows are added per crop,
which is safe because a crop always has a single unit.

Crop rows come from v_district_crop (seasons added, "Oilseeds total" excluded); state rows from
v_state_crop. Production stays in each crop's own unit (tonnes, nuts, 170 kg or 180 kg bales),
so nothing adds production across crops.

    python scripts/build_web_data.py
"""
import csv
import json
import sqlite3
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "output" / "crop_production.sqlite"
MAP_CSV = ROOT / "data" / "geo" / "district_name_map.csv"
OUT = ROOT / "web" / "data" / "crops.json"

SEASONS = ["Kharif", "Rabi", "Summer", "Winter", "Autumn", "Whole Year"]
GROUPS = ["Cereals", "Pulses", "Oilseeds", "Fibres", "Sugar", "Spices & condiments",
          "Vegetables & tubers", "Fruits & plantation", "Other commercial"]


def num(x, nd=2):
    return None if x is None else round(x, nd)


def main() -> None:
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    year = con.execute("SELECT DISTINCT year_label FROM production").fetchall()
    if len(year) != 1:
        raise SystemExit(f"expected one crop year in the database, found {[r[0] for r in year]}")
    year = year[0][0]

    crops = [dict(r) for r in con.execute(
        "SELECT crop_name, crop_group, production_unit FROM crops WHERE is_aggregate = 0 ORDER BY crop_name")]
    unknown = {c["crop_group"] for c in crops} - set(GROUPS)
    if unknown:
        raise SystemExit(f"new crop group(s) {unknown}: add them to GROUPS here and in web/app.js")
    crop_ix = {c["crop_name"]: i for i, c in enumerate(crops)}

    # --- DES district -> polygons, grouped into map units (connected components) ---
    with MAP_CSV.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    missing = [r for r in rows if r["method"] == "unmatched"]
    if missing:
        print(f"warning: {len(missing)} unmatched DES districts are left off the map "
              f"(run scripts/match_districts.py for the list)")

    parent: dict[str, str] = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    des_polys = {}
    for r in rows:
        ids = [i for i in r["geo_ids"].split("|") if i]
        if not ids:
            continue
        des_polys[(r["state"], r["des_district"])] = ids
        for i in ids[1:]:
            parent[find(i)] = find(ids[0])
        find(ids[0])

    comp = defaultdict(lambda: {"des": [], "geo": []})
    for key, ids in des_polys.items():
        root = find(ids[0])
        comp[root]["des"].append(key)
        for i in ids:
            if i not in comp[root]["geo"]:
                comp[root]["geo"].append(i)

    # --- crop rows per DES district ---
    dcrop = defaultdict(list)
    for r in con.execute("SELECT state, district, crop, area_ha, production, seasons FROM v_district_crop"):
        dcrop[(r["state"], r["district"])].append(r)

    units = {}
    for root, c in comp.items():
        des = sorted(c["des"], key=lambda k: k[1])
        state = des[0][0]
        merged = {}
        for key in des:
            for r in dcrop.get(key, []):
                m = merged.setdefault(r["crop"], {"area": None, "prod": None, "seasons": 0})
                if r["area_ha"] is not None:
                    m["area"] = (m["area"] or 0) + r["area_ha"]
                if r["production"] is not None:
                    m["prod"] = (m["prod"] or 0) + r["production"]
                for s in (r["seasons"] or "").split(","):
                    if s.strip():
                        m["seasons"] |= 1 << SEASONS.index(s.strip())
        crop_rows = []
        for name, m in merged.items():
            y = m["prod"] / m["area"] if m["prod"] is not None and m["area"] else None
            crop_rows.append([crop_ix[name], num(m["area"], 1), num(m["prod"]), num(y, 3), m["seasons"]])
        crop_rows.sort(key=lambda x: -(x[1] or 0))
        units[c["geo"][0]] = {
            "name": " + ".join(k[1] for k in des),
            "state": state,
            "geo": c["geo"],
            "area": num(sum(x[1] or 0 for x in crop_rows), 1),
            "c": crop_rows,   # [crop index, area ha, production, yield per ha, season bitmask]
        }

    # --- states ---
    states = {}
    for r in con.execute("SELECT state, crop, area_ha, production, yield_per_ha, districts_growing "
                         "FROM v_state_crop ORDER BY state, area_ha DESC"):
        s = states.setdefault(r["state"], {"area": 0, "c": []})
        s["c"].append([crop_ix[r["crop"]], num(r["area_ha"], 1), num(r["production"]),
                       num(r["yield_per_ha"], 3), r["districts_growing"]])
        s["area"] += r["area_ha"] or 0
    for st, n in con.execute("SELECT state_name, COUNT(*) FROM districts JOIN states USING(state_id) GROUP BY 1"):
        states[st]["districts"] = n
        states[st]["area"] = num(states[st]["area"], 1)

    out = {
        "meta": {
            "year": year,
            "source": "Directorate of Economics & Statistics (DES), MoA&FW: district-wise season-wise APY",
            "boundaries": "geoBoundaries IND ADM2 (LGD 2021, ODbL); states dissolved from districts",
        },
        "seasons": SEASONS,
        "groups": GROUPS,
        "crops": [[c["crop_name"], GROUPS.index(c["crop_group"]), c["production_unit"]] for c in crops],
        "units": units,
        "states": states,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, separators=(",", ":")), encoding="utf-8")
    n_des = sum(len(c["des"]) for c in comp.values())
    print(f"Wrote {OUT.relative_to(ROOT)} ({OUT.stat().st_size / 1e3:.0f} kB): "
          f"{len(units)} map units from {n_des} DES districts, {len(states)} states, {len(crops)} crops")


if __name__ == "__main__":
    main()
