"""Download S01 (data.gov.in district-wise season-wise crop production) for one state and crops.

Pulls from the official data.gov.in API, filtered on state and crop, and writes the raw rows
unchanged to data/raw/s01_crop_production/ with a manifest recording provenance (MASTER_SPEC §9.6).

    DATA_GOV_IN_API_KEY=<registered key> python scripts/download_s01_crop_production.py

A registered key is required: register at https://data.gov.in (My Account > API key). The
manifest is written only after every crop has been downloaded completely (rows == API total), so
training never starts from a partial pull.
"""

import argparse
import csv
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from datetime import UTC, datetime

from agri_ml.config import get_settings
from agri_ml.datasets.crop_production import S01_RESOURCE_ID

API_URL = f"https://api.data.gov.in/resource/{S01_RESOURCE_ID}"
KEY_ENV = "DATA_GOV_IN_API_KEY"
PAGE_SIZE = 1000


def fetch_page(api_key: str, state: str, crop: str, offset: int) -> dict:
    params = {
        "api-key": api_key,
        "format": "json",
        "offset": offset,
        "limit": PAGE_SIZE,
        "filters[state_name]": state,
        "filters[crop]": crop,
    }
    url = f"{API_URL}?{urllib.parse.urlencode(params)}"
    for attempt in range(6):
        try:
            with urllib.request.urlopen(url, timeout=60) as resp:
                payload = json.load(resp)
            if "records" in payload:
                return payload
            print(
                f"  unexpected payload ({payload.get('error') or payload.get('message')}), retrying"
            )
        except Exception as exc:  # network errors and rate-limit HTTP codes
            print(f"  attempt {attempt + 1} failed: {exc}")
        time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"giving up on {state}/{crop} offset {offset}")


def download(state: str, crop: str, api_key: str) -> tuple[list[dict], int]:
    rows: list[dict] = []
    offset = 0
    total = None
    while total is None or offset < total:
        page = fetch_page(api_key, state, crop, offset)
        total = int(page.get("total", 0))
        records = page["records"]
        if not records:
            break
        rows.extend(records)
        offset += len(records)
        print(f"  {crop}: {len(rows)}/{total}")
    return rows, total or 0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state", default="Uttar Pradesh")
    parser.add_argument("--crops", nargs="+", default=["Potato", "Wheat", "Onion", "Maize"])
    args = parser.parse_args()

    api_key = os.environ.get(KEY_ENV, "").strip()
    if not api_key:
        sys.exit(f"{KEY_ENV} is not set. Register a data.gov.in API key and export it; "
                 "no shared or sample key is used.")
    out_dir = get_settings().data_dir / "raw" / "s01_crop_production"
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest = {
        "source": "S01 data.gov.in district-wise season-wise crop production statistics",
        "resourceId": S01_RESOURCE_ID,
        "apiUrl": API_URL,
        "license": "Government Open Data License - India (GODL)",
        "accessedAt": datetime.now(UTC).isoformat(timespec="seconds"),
        "keyType": "REGISTERED",
        "files": {},
    }
    for crop in args.crops:
        rows, total = download(args.state, crop, api_key)
        if not rows or len(rows) != total:
            sys.exit(f"incomplete download for {crop}: {len(rows)} rows, API total {total}; "
                     "no manifest written, rerun the download")
        path = out_dir / f"{args.state.lower().replace(' ', '_')}_{crop.lower()}.csv"
        fields = sorted({k for r in rows for k in r})
        with path.open("w", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
        manifest["files"][path.name] = {
            "state": args.state,
            "crop": crop,
            "rows": len(rows),
            "apiTotal": total,
        }
        print(f"wrote {path} ({len(rows)} rows, api total {total})")

    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
