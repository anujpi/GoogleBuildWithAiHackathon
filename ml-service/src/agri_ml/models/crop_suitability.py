"""Transparent, evidence-based crop suitability index (no trained model).

For one state and season, every crop with enough recent history in the Kaggle crop_yield data
(state x crop x season, 1997-2019) is scored on documented components:

  yield_rank     0.30  the state's median yield vs. every other state growing that crop/season
  establishment  0.25  how much area the crop occupies in this state/season (it is grown here)
  stability      0.15  1 - coefficient of variation of yield (low year-to-year volatility)
  trend          0.10  recent yield trend
  soil_ph        0.10  farm soil pH inside an indicative crop range (only if pH is supplied)
  water          0.10  crop water need vs. irrigation + state rainfall (only if irrigation known)

Components that cannot be assessed are dropped and the weights renormalised; they are listed in
`notAssessed`. The index is a relative ranking aid. It is NOT a probability, is not calibrated
and has no measured accuracy.
"""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np
import pandas as pd

from agri_ml.datasets import crop_yield as cy

METHOD = "weighted-evidence-index"
METHOD_VERSION = "crop-suit-v1"
RECENT_YEARS = 10
MIN_YEARS = 3
WEIGHTS = {"yield_rank": 0.30, "establishment": 0.25, "stability": 0.15, "trend": 0.10,
           "soil_ph": 0.10, "water": 0.10}
EXCLUDED_CROPS = cy.SUPPLY_EXCLUDED_CROPS | cy.AGGREGATE_CROPS

# Indicative agronomic reference ranges compiled for this MVP from general agronomy guidance
# (cf. FAO ECOCROP optimal ranges). They are coarse, not variety- or site-specific, and must be
# checked against local KVK / state agriculture department guidance before real use.
# crop -> (pH low, pH high, water need)
CROP_REFERENCE: dict[str, tuple[float, float, str]] = {
    "Rice": (5.5, 7.0, "high"),
    "Sugarcane": (6.0, 7.5, "high"),
    "Banana": (5.5, 7.0, "high"),
    "Wheat": (6.0, 7.5, "medium"),
    "Maize": (5.5, 7.5, "medium"),
    "Potato": (5.0, 6.5, "medium"),
    "Onion": (6.0, 7.0, "medium"),
    "Cotton(lint)": (5.8, 8.0, "medium"),
    "Soyabean": (6.0, 7.0, "medium"),
    "Groundnut": (5.5, 7.0, "low"),
    "Gram": (6.0, 8.0, "low"),
    "Arhar/Tur": (5.0, 7.0, "low"),
    "Rapeseed &Mustard": (6.0, 7.5, "low"),
    "Bajra": (5.5, 7.5, "low"),
    "Jowar": (5.5, 7.5, "low"),
    "Barley": (6.0, 8.0, "low"),
}
REFERENCE_NOTE = ("indicative reference range compiled for this MVP (cf. FAO ECOCROP); "
                  "not site-calibrated")
# Mean annual state rainfall (mm) below which a high-water crop without irrigation is penalised.
HIGH_WATER_RAIN_MM = 1000.0

LIMITATIONS = [
    "The score is a weighted evidence index for ranking, not a probability; it is uncalibrated "
    "and has no measured accuracy.",
    "Historical evidence is state x season level (Kaggle crop_yield, 1997-2019); it says nothing "
    "about a specific field.",
    "A crop that is rarely grown in the state scores low on establishment even if it could grow "
    "there.",
    "Soil pH and water-need references are indicative ranges, not variety- or site-specific.",
    "Yield is computed as Production / Area from the source; units are as stated by the publisher.",
]


@dataclass(frozen=True)
class SuitabilityData:
    frame: pd.DataFrame  # State, Crop, Season, Crop_Year, Area, Production, Annual_Rainfall, yield
    dataset_version: str


@lru_cache(maxsize=4)
def load_data(path: Path) -> SuitabilityData:
    df = cy.quality_flags(cy.load(path))
    df = df[~df["flag_any"] & ~df["Crop"].isin(EXCLUDED_CROPS) & (df["Area"] > 0)].copy()
    df["yield"] = df["Production"] / df["Area"]
    cols = ["State", "Crop", "Season", "Crop_Year", "Area", "Production", "Annual_Rainfall",
            "yield"]
    return SuitabilityData(df[cols].reset_index(drop=True), cy.dataset_version(path))


class UnknownRegionError(ValueError):
    pass


def _pct_rank(values: pd.Series, value: float) -> float:
    """Share of values <= value, in [0, 1]. A single value ranks 0.5 (no comparison possible)."""
    if len(values) <= 1:
        return 0.5
    return float((values < value).sum() + 0.5 * ((values == value).sum() - 1)) / (len(values) - 1)


def _trend_pct(years: np.ndarray, yields: np.ndarray) -> float | None:
    if len(years) < 4 or np.mean(yields) <= 0:
        return None
    slope = np.polyfit(years, yields, 1)[0]
    return float(100 * slope / np.mean(yields))


def _ph_score(crop: str, ph: float | None) -> tuple[float | None, str]:
    ref = CROP_REFERENCE.get(crop)
    if ph is None:
        return None, "soil pH not supplied"
    if ref is None:
        return None, f"no pH reference range for {crop}"
    lo, hi, _ = ref
    if lo <= ph <= hi:
        return 1.0, f"soil pH {ph:g} is inside {lo:g}-{hi:g} ({REFERENCE_NOTE})"
    gap = lo - ph if ph < lo else ph - hi
    score = max(0.0, 1.0 - gap / 1.5)
    return score, f"soil pH {ph:g} is {gap:.1f} outside {lo:g}-{hi:g} ({REFERENCE_NOTE})"


def _water_score(crop: str, irrigated: bool | None,
                 rain_mm: float | None) -> tuple[float | None, str]:
    ref = CROP_REFERENCE.get(crop)
    if irrigated is None:
        return None, "irrigation status not supplied"
    if ref is None:
        return None, f"no water-need reference for {crop}"
    need = ref[2]
    rain_txt = f"state mean annual rainfall {rain_mm:.0f} mm (source data)" if rain_mm else \
        "state rainfall unknown"
    if irrigated or need == "low":
        return 1.0, f"{need} water need; {'irrigated' if irrigated else 'rain-fed'}; {rain_txt}"
    if need == "medium":
        return 0.7, f"medium water need on a rain-fed farm; {rain_txt}"
    if rain_mm is not None and rain_mm >= HIGH_WATER_RAIN_MM:
        return 0.7, f"high water need, rain-fed, but {rain_txt} >= {HIGH_WATER_RAIN_MM:.0f} mm"
    return 0.2, f"high water need on a rain-fed farm; {rain_txt}"


def score(data: SuitabilityData, state: str, season: str, soil_ph: float | None = None,
          irrigated: bool | None = None,
          top_n: int = 5) -> tuple[list[dict], int, str, str]:
    """Return (top candidates by score, number of crops considered, state label, season)."""
    df = data.frame
    st = df[df["State"].str.lower() == state.strip().lower()]
    if st.empty:
        raise UnknownRegionError(f"state {state!r} is not in the source data; known: "
                                 f"{sorted(df['State'].unique())}")
    ss = st[st["Season"].str.lower() == season.strip().lower()]
    if ss.empty:
        raise UnknownRegionError(f"no {season!r} season rows for {state}; seasons available: "
                                 f"{sorted(st['Season'].unique())}")
    state_label, season_label = ss["State"].iloc[0], ss["Season"].iloc[0]
    last_year = int(ss["Crop_Year"].max())
    recent = ss[ss["Crop_Year"] > last_year - RECENT_YEARS]
    rain = st.groupby("Crop_Year")["Annual_Rainfall"].first()
    rain_mm = float(rain[rain.index > last_year - RECENT_YEARS].mean()) if not rain.empty else None

    per_crop = recent.groupby("Crop")
    area_median = per_crop["Area"].median()
    eligible = [c for c, g in per_crop if g["Crop_Year"].nunique() >= MIN_YEARS]
    out = []
    for crop in eligible:
        g = per_crop.get_group(crop).sort_values("Crop_Year")
        med_yield = float(g["yield"].median())
        # National comparison: each state's median yield for this crop/season in the same years.
        peers = df[(df["Crop"] == crop) & (df["Season"] == season_label)
                   & (df["Crop_Year"] > last_year - RECENT_YEARS)]
        peer_medians = peers.groupby("State")["yield"].median()
        yield_rank = _pct_rank(peer_medians, med_yield)
        establishment = _pct_rank(area_median, float(area_median[crop]))
        cv = float(g["yield"].std(ddof=0) / g["yield"].mean()) if g["yield"].mean() > 0 else 1.0
        stability = float(np.clip(1 - cv, 0, 1))
        trend = _trend_pct(g["Crop_Year"].to_numpy(float), g["yield"].to_numpy(float))
        trend_score = None if trend is None else float(np.clip(0.5 + trend / 10, 0, 1))
        ph, ph_txt = _ph_score(crop, soil_ph)
        water, water_txt = _water_score(crop, irrigated, rain_mm)

        comps = [
            ("yield_rank", yield_rank,
             f"median yield {med_yield:.2f} t/ha ranks at the {100 * yield_rank:.0f}th percentile "
             f"of {len(peer_medians)} states growing {crop} in {season_label}"),
            ("establishment", establishment,
             f"median area {area_median[crop]:,.0f} ha ranks at the {100 * establishment:.0f}th "
             f"percentile of {len(area_median)} crops grown in {state_label} {season_label}"),
            ("stability", stability, f"yield coefficient of variation {cv:.2f} over "
                                     f"{g['Crop_Year'].nunique()} years"),
            ("trend", trend_score, "fewer than 4 years of yield" if trend is None else
             f"yield trend {trend:+.1f}% per year"),
            ("soil_ph", ph, ph_txt),
            ("water", water, water_txt),
        ]
        assessed = [(n, s) for n, s, _ in comps if s is not None]
        wsum = sum(WEIGHTS[n] for n, _ in assessed)
        total = sum(WEIGHTS[n] * s for n, s in assessed) / wsum
        latest = g.iloc[-1]
        out.append({
            "crop": crop,
            "suitability_score": round(total, 3),
            "components": [{"name": n, "score": None if s is None else round(s, 3),
                            "weight": WEIGHTS[n], "evidence": e} for n, s, e in comps],
            "evidence": [e for n, s, e in comps if s is not None],
            "not_assessed": [f"{n}: {e}" for n, s, e in comps if s is None],
            "history": {
                "years_observed": int(g["Crop_Year"].nunique()),
                "first_year": int(g["Crop_Year"].min()),
                "last_year": int(g["Crop_Year"].max()),
                "median_yield_t_per_ha": round(med_yield, 3),
                "latest_area_hectares": float(latest["Area"]),
                "latest_production_tonnes": float(latest["Production"]),
                "yield_trend_pct_per_year": None if trend is None else round(trend, 2),
            },
        })
    out.sort(key=lambda c: -c["suitability_score"])
    return out[:top_n], len(eligible), state_label, season_label
