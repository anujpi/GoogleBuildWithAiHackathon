"""Download S01 (data.gov.in district-wise season-wise crop production) for one state and crops.

Pulls from the official data.gov.in API, filtered on state and crop, and writes the raw rows
unchanged to data/raw/s01_crop_production/ with a manifest recording provenance.

    python scripts/download_s01_crop_production.py --state "Uttar Pradesh" --crops Potato Wheat

The API key is read from AGRI_ML_DATA_GOV_API_KEY. Without it the public data.gov.in sample key
is used, which is shared and rate-limited (see docs/datasets/DATASET_CATALOGUE.md).
"""

import argparse
import csv
import json
import os
import time
import urllib.parse
import urllib.request
from datetime import UTC, datetime

from agri_ml.config import get_settings

RESOURCE_ID = "35be999b-0208-4354-b557-f6ca9a5355de"
API_URL = f"https://api.data.gov.in/resource/{RESOURCE_ID}"
# Published by data.gov.in for demos; not a secret.
PUBLIC_SAMPLE_KEY = "579b464db66ec23bdd000001cdd3946e44ce4aad7209ff7b23ac571b"
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
            print(f"  unexpected payload ({payload.get('error') or payload.get('message')}), retrying")
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
    parser.add_argument("--crops", nargs="+", default=["Potato", "Wheat", "Onion", "Rice"])
    args = parser.parse_args()

    api_key = os.environ.get("AGRI_ML_DATA_GOV_API_KEY", PUBLIC_SAMPLE_KEY)
    out_dir = get_settings().data_dir / "raw" / "s01_crop_production"
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest = {
        "source": "S01 data.gov.in district-wise season-wise crop production statistics",
        "resourceId": RESOURCE_ID,
        "apiUrl": API_URL,
        "license": "Government Open Data License - India (GODL)",
        "accessedAt": datetime.now(UTC).isoformat(timespec="seconds"),
        "usedSampleKey": api_key == PUBLIC_SAMPLE_KEY,
        "files": {},
    }
    for crop in args.crops:
        rows, total = download(args.state, crop, api_key)
        path = out_dir / f"{args.state.lower().replace(' ', '_')}_{crop.lower()}.csv"
        fields = sorted({k for r in rows for k in r})
        with path.open("w", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
        manifest["files"][path.name] = {
            "state": args.state, "crop": crop, "rows": len(rows), "apiTotal": total,
        }
        print(f"wrote {path} ({len(rows)} rows, api total {total})")

    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
