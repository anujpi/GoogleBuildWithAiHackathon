"""Download India boundaries and build the simplified TopoJSON used by the web map.

Source: geoBoundaries (gbOpen), India
  ADM2 (districts): LGD-based, year 2021, ODbL 1.0
  ADM1 (states):    DataMeet / ECI, CC BY 2.5 IN; used only to tag each district with its state

Steps
  1. Download both layers into data/geo/raw/ (skipped if already there).
  2. Tag every district with the state it overlaps most (mapshaper largest-overlap join).
  3. Simplify, then dissolve districts by state so the two layers share borders.
  4. Write data/geo/india.topo.json (layers: districts, states) and copy it to web/data/.

Needs Node.js (mapshaper is run through npx). No Python packages beyond the standard library.

    python scripts/build_geo.py            # default simplification (keeps 6% of vertices)
    python scripts/build_geo.py --keep 10  # more detail, bigger file
"""
import argparse
import json
import shutil
import subprocess
import sys
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GEO = ROOT / "data" / "geo"
RAW = GEO / "raw"
WEB_DATA = ROOT / "web" / "data"

API = "https://www.geoboundaries.org/api/current/gbOpen/IND/{level}/"
MAPSHAPER = "mapshaper@0.7"

# geoBoundaries ADM1 names carry diacritics; the database uses plain names.
STATE_NAME_FIXES = {
    "Dadra and Nagar Haveli and Daman and Diu": "Dadra and Nagar Haveli and Daman and Diu",
}


# Enclaves the largest-overlap join puts in the surrounding state (Yanam lies inside Andhra Pradesh).
DISTRICT_STATE_FIXES = {
    "Yanam": "Puducherry",
}


def plain(s: str) -> str:
    s = unicodedata.normalize("NFKD", s)
    return "".join(ch for ch in s if not unicodedata.combining(ch)).strip()


def download(level: str) -> Path:
    out = RAW / f"IND_{level}.geojson"
    if out.exists():
        print(f"  {out.name} already downloaded")
        return out
    with urllib.request.urlopen(API.format(level=level)) as r:
        meta = json.load(r)
    url = meta["gjDownloadURL"]
    print(f"  {level}: {meta['boundarySource']} ({meta['boundaryYearRepresented']}, {meta['boundaryLicense']})")
    print(f"  downloading {url}")
    urllib.request.urlretrieve(url, out)
    (RAW / f"IND_{level}.meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return out


def run_mapshaper(args: list[str]) -> None:
    npx = shutil.which("npx")
    if not npx:
        sys.exit("npx not found: install Node.js (https://nodejs.org) to run mapshaper")
    cmd = [npx, "-y", MAPSHAPER, *args]
    print("  mapshaper", " ".join(args))
    subprocess.run(cmd, check=True)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--keep", type=float, default=6, help="percent of vertices to keep (default 6)")
    args = ap.parse_args()

    RAW.mkdir(parents=True, exist_ok=True)
    WEB_DATA.mkdir(parents=True, exist_ok=True)

    print("1. Boundaries")
    adm2 = download("ADM2")
    adm1 = download("ADM1")

    print("2-3. Join states, simplify, dissolve")
    tmp = GEO / "_districts_tmp.json"
    run_mapshaper([
        str(adm2),
        "-join", str(adm1), "fields=shapeName", "prefix=st_", "largest-overlap",
        "-each", "id=shapeID, name=shapeName.trim().replace(/\\s+/g,' '), state=st_shapeName",
        "-filter-fields", "id,name,state",
        "-simplify", f"{args.keep}%", "weighted", "keep-shapes",
        "-clean",
        "-o", str(tmp), "format=geojson", "precision=0.0001",
    ])

    # Plain-ASCII state names that match the database
    gj = json.loads(tmp.read_text(encoding="utf-8"))
    for f in gj["features"]:
        p = f["properties"]
        st = plain(p["state"])
        p["state"] = DISTRICT_STATE_FIXES.get(p["name"], STATE_NAME_FIXES.get(st, st))
    tmp.write_text(json.dumps(gj), encoding="utf-8")

    out = GEO / "india.topo.json"
    run_mapshaper([
        str(tmp), "name=districts",
        # the dissolved states layer keeps the unlabelled polygon (disputed area) so state
        # outlines are complete; the districts layer drops it because it has no data
        "-dissolve", "state", "+", "name=states",
        "-filter", "name != 'DATA NOT AVAILABLE'", "target=districts",
        # interior label point of each polygon: marker position and "is it in view?" test
        "-each", "lx=Math.round(this.innerX*1e4)/1e4, ly=Math.round(this.innerY*1e4)/1e4", "target=districts,states",
        "-o", str(out), "format=topojson", "quantization=100000", "target=districts,states",
    ])
    tmp.unlink()

    shutil.copy(out, WEB_DATA / out.name)
    topo = json.loads(out.read_text(encoding="utf-8"))
    n_d = len(topo["objects"]["districts"]["geometries"])
    n_s = len(topo["objects"]["states"]["geometries"])
    print(f"4. Wrote {out.relative_to(ROOT)} ({out.stat().st_size / 1e6:.2f} MB): {n_d} districts, {n_s} states")
    print(f"   copied to {(WEB_DATA / out.name).relative_to(ROOT)}")


if __name__ == "__main__":
    main()
