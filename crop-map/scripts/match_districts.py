"""Match DES district names (database) to boundary polygons (data/geo/india.topo.json).

Matching never crosses states. For each state, in order:
  1. manual   rows you edited in data/geo/district_name_map.csv (method = manual) are kept as-is
  2. exact    same name after normalising (case, accents, punctuation, "district", "&" -> and)
  3. alias    same name after also unifying spelling variants and direction words
              (Purba/Purbi = East, Paschim = West, Dakshin = South, Uttar = North, 24 = Twenty Four,
              dropped bracketed parts and spaces, e.g. "UTTAR KASHI" = "Uttarkashi")
  4. subset   all words of the shorter name appear in the longer ("KANKER" = "Uttar Bastar Kanker")
  5. fuzzy    best remaining candidate by string similarity, if the score is >= --threshold
Everything left over is written as method = unmatched and printed.

Fixing a row by hand: open data/geo/district_name_map.csv, put the boundary name(s) in geo_names
(several polygons: separate with "|"), set method to "manual", and re-run this script. Use
geo_names = "-" with method = manual to say "no polygon for this district". Two DES districts
may name the same polygon (e.g. a district split after the boundaries were drawn); their crop
data is then shown together on that polygon.

    python scripts/match_districts.py
    python scripts/match_districts.py --threshold 0.8
"""
import argparse
import csv
import json
import re
import sqlite3
import unicodedata
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "output" / "crop_production.sqlite"
TOPO = ROOT / "data" / "geo" / "india.topo.json"
MAP_CSV = ROOT / "data" / "geo" / "district_name_map.csv"
FIELDS = ["state", "des_district", "geo_names", "geo_ids", "method", "score", "note"]

DIRECTIONS = {
    "UTTAR": "NORTH", "UTTARA": "NORTH",
    "DAKSHIN": "SOUTH", "DAKSHINA": "SOUTH",
    "PURBA": "EAST", "PURBI": "EAST", "PURV": "EAST", "PURVI": "EAST",
    "PASCHIM": "WEST", "PASHCHIM": "WEST", "PASHCHIMI": "WEST", "PASCHIMI": "WEST",
}
DROP_WORDS = {"DISTRICT", "DIST", "THE", "AND", "TWENTY", "FOUR", "24", "TOTAL"}


def base(s: str) -> str:
    """Upper case, no accents, & -> AND, punctuation -> space, single spaces."""
    s = unicodedata.normalize("NFKD", s)
    s = "".join(ch for ch in s if not unicodedata.combining(ch)).upper()
    s = s.replace("&", " AND ").replace("_", " ")
    s = re.sub(r"[^A-Z0-9 ]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def exact_key(s: str) -> str:
    return " ".join(w for w in base(s).split() if w not in {"DISTRICT", "THE"})


def phonetic(word: str) -> str:
    """Collapse common transliteration differences: aa/a, ee/i, oo/u, w/v, bh/b, doubled letters..."""
    w = word
    for a, b in (("AA", "A"), ("EE", "I"), ("OO", "U"), ("W", "V"), ("PH", "F"), ("Z", "J"),
                 ("SH", "S"), ("TH", "T"), ("DH", "D"), ("BH", "B"), ("KH", "K"), ("GH", "G"),
                 ("CH", "C"), ("Y", "I"), ("OU", "U")):
        w = w.replace(a, b)
    w = re.sub(r"(.)\1+", r"\1", w)          # doubled letters
    w = re.sub(r"(?<=.)[AEIOU]+$", "", w)    # trailing vowel (Bagalkote / Bagalkot)
    return w


def alias_keys(s: str) -> set[str]:
    """Several looser keys; two names match at this stage if any key is shared."""
    b = base(s)
    variants = {b, re.sub(r"\s*\(.*?\)\s*", " ", s)}  # with and without "(...)" parts
    keys = set()
    for v in variants:
        raw = [w for w in base(v).split() if w not in DROP_WORDS]
        if not raw:
            continue
        keys.add("".join(raw))                                   # "UTTAR KASHI" = "Uttarkashi"
        words = [DIRECTIONS.get(w, w) for w in raw]
        keys.add(" ".join(sorted(words)))                        # word order free
        keys.add("".join(words))                                 # space free
        keys.add(" ".join(sorted(phonetic(w) for w in words)))   # spelling free
        keys.add("".join(phonetic(w) for w in words))
    return keys


def word_set(s: str) -> set[str]:
    words = set()
    for w in base(re.sub(r"[()]", " ", s)).split():
        if w not in DROP_WORDS:
            words.add(phonetic(DIRECTIONS.get(w, w)))
    return words


def is_subset_match(a: str, b: str) -> bool:
    """All words of the shorter name appear in the longer one ("KANKER" / "Uttar Bastar Kanker")."""
    wa, wb = word_set(a), word_set(b)
    small, big = (wa, wb) if len(wa) <= len(wb) else (wb, wa)
    meaningful = [w for w in small if w not in {"NORT", "SOUT", "EAST", "VEST"} and len(w) >= 4]
    return bool(meaningful) and small <= big and small != big


def similarity(a: str, b: str) -> float:
    ka = " ".join(sorted(DIRECTIONS.get(w, w) for w in base(a).split() if w not in DROP_WORDS))
    kb = " ".join(sorted(DIRECTIONS.get(w, w) for w in base(b).split() if w not in DROP_WORDS))
    pa = "".join(phonetic(w) for w in ka.split())
    pb = "".join(phonetic(w) for w in kb.split())
    return max(SequenceMatcher(None, ka, kb).ratio(), SequenceMatcher(None, pa, pb).ratio())


def load_des() -> dict[str, list[str]]:
    con = sqlite3.connect(DB)
    out = defaultdict(list)
    for st, d in con.execute(
        "SELECT state_name, district_name FROM districts JOIN states USING(state_id) ORDER BY 1, 2"
    ):
        out[st].append(d)
    return out


def load_geo() -> dict[str, list[dict]]:
    topo = json.loads(TOPO.read_text(encoding="utf-8"))
    out = defaultdict(list)
    for g in topo["objects"]["districts"]["geometries"]:
        p = g["properties"]
        out[p["state"]].append({"id": p["id"], "name": p["name"]})
    return out


def load_manual() -> dict[tuple[str, str], dict]:
    if not MAP_CSV.exists():
        return {}
    with MAP_CSV.open(encoding="utf-8-sig", newline="") as f:
        return {(r["state"], r["des_district"]): r for r in csv.DictReader(f)
                if r.get("method", "").strip().lower() == "manual"}


def match(threshold: float) -> tuple[list[dict], dict[str, list[dict]]]:
    des, geo, manual = load_des(), load_geo(), load_manual()
    rows: list[dict] = []
    unused_geo: dict[str, list[dict]] = {}

    for state in sorted(set(des) | set(geo)):
        dnames = des.get(state, [])
        polys = geo.get(state, [])
        by_name = defaultdict(list)
        for p in polys:
            by_name[p["name"].upper()].append(p)
        used: set[str] = set()
        result: dict[str, dict] = {}

        # 1. manual
        for d in dnames:
            m = manual.get((state, d))
            if not m:
                continue
            names = [n.strip() for n in m["geo_names"].split("|") if n.strip() and n.strip() != "-"]
            ids = []
            for n in names:
                hits = by_name.get(n.upper())
                if not hits:
                    raise SystemExit(f"district_name_map.csv: '{n}' is not a boundary in {state} "
                                     f"(row {d}). Choose from: {sorted(p['name'] for p in polys)}")
                ids.extend(h["id"] for h in hits)
            used.update(ids)
            result[d] = dict(geo_names="|".join(names) or "-", geo_ids="|".join(ids),
                             method="manual", score="", note=m.get("note", ""))

        def free():
            return [p for p in polys if p["id"] not in used]

        # 2. exact, 3. alias: only accept a key shared by exactly one free polygon
        for method, keyf in (("exact", lambda s: {exact_key(s)}), ("alias", alias_keys)):
            for d in dnames:
                if d in result:
                    continue
                dk = keyf(d)
                hits = [p for p in free() if keyf(p["name"]) & dk]
                if len(hits) == 1:
                    p = hits[0]
                    used.add(p["id"])
                    result[d] = dict(geo_names=p["name"], geo_ids=p["id"], method=method, score="1.00", note="")

        # 4. subset: one name's words contained in the other's, unique on both sides
        open_d = [d for d in dnames if d not in result]
        for d in open_d:
            hits = [p for p in free() if is_subset_match(d, p["name"])]
            if len(hits) != 1:
                continue
            p = hits[0]
            if sum(is_subset_match(o, p["name"]) for o in open_d if o not in result) != 1:
                continue
            used.add(p["id"])
            result[d] = dict(geo_names=p["name"], geo_ids=p["id"], method="subset", score="", note="")

        # 5. fuzzy: best pairs first, one-to-one
        pairs = sorted(((similarity(d, p["name"]), d, p) for d in dnames if d not in result for p in free()),
                       key=lambda t: -t[0])
        for score, d, p in pairs:
            if score < threshold or d in result or p["id"] in used:
                continue
            used.add(p["id"])
            result[d] = dict(geo_names=p["name"], geo_ids=p["id"], method="fuzzy", score=f"{score:.2f}", note="")

        for d in dnames:
            r = result.get(d) or dict(geo_names="", geo_ids="", method="unmatched", score="", note="")
            if r["method"] == "unmatched":
                best = max(((similarity(d, p["name"]), p["name"]) for p in free()), default=None)
                if best:
                    r["note"] = f"closest free polygon: {best[1]} ({best[0]:.2f})"
            rows.append({"state": state, "des_district": d, **r})
        unused_geo[state] = free()
    return rows, unused_geo


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--threshold", type=float, default=0.75, help="minimum fuzzy score (0-1, default 0.75)")
    args = ap.parse_args()

    rows, unused_geo = match(args.threshold)
    with MAP_CSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)

    total = len(rows)
    counts = defaultdict(int)
    for r in rows:
        counts[r["method"]] += 1
    matched = total - counts["unmatched"]
    print(f"DES districts: {total}   matched: {matched} ({matched / total:.1%})")
    for m in ("manual", "exact", "alias", "subset", "fuzzy", "unmatched"):
        print(f"  {m:10s} {counts[m]}")

    fuzzy = [r for r in rows if r["method"] == "fuzzy"]
    if fuzzy:
        print("\nFuzzy matches (check these):")
        for r in sorted(fuzzy, key=lambda r: r["score"]):
            print(f"  {r['score']}  {r['state']:28s} {r['des_district']:28s} -> {r['geo_names']}")

    un = [r for r in rows if r["method"] == "unmatched"]
    if un:
        print("\nUNMATCHED DES districts (fix in data/geo/district_name_map.csv, method = manual):")
        for r in un:
            print(f"  {r['state']:28s} {r['des_district']:28s} {r['note']}")

    left = [(s, p["name"]) for s, ps in unused_geo.items() for p in ps]
    if left:
        print(f"\nBoundary polygons with no DES district ({len(left)}):")
        for s, n in left:
            print(f"  {s:28s} {n}")
    print(f"\nWrote {MAP_CSV.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
