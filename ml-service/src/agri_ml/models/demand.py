"""Demand forecast: transparent statistical baseline on a public consumption PROXY.

Data: FAOSTAT Food Balance Sheets, India (national, annual, 2010-2023), element
"Domestic supply quantity" = production + imports - exports - stock change, i.e. apparent
national use (food, feed, seed, processing, losses). This is a demand PROXY, not observed
retail/market demand.

Forecast: two candidate baselines are back-tested with rolling-origin 1-step forecasts over the
last BACKTEST_YEARS years; the one with the lower MAPE is used:
  naive          last observed value
  linear_trend   OLS line on the previous TREND_WINDOW years, extrapolated
If the target year is inside the observed range the observed value is returned (OBSERVED).

State apportionment (optional): national value x the state's share of India's population in
Census 2011. It assumes uniform per-capita use across states, which is known to be false for
diet staples (e.g. rice vs. wheat), so the state value is an ESTIMATED proxy only.
"""

from __future__ import annotations

import csv
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np

METHOD_VERSION = "demand-baseline-v1"
ELEMENT = "Domestic supply quantity"
FOOD_ELEMENT = "Food"
PER_CAPITA_ELEMENT = "Food supply quantity (kg/capita/yr)"
TREND_WINDOW = 8
BACKTEST_YEARS = 6
INTERVAL_QUANTILE = 0.8

# Platform/crop_yield crop name -> FAOSTAT FBS item.
CROP_TO_ITEM = {
    "Potato": "Potatoes and products",
    "Wheat": "Wheat and products",
    "Rice": "Rice and products",
    "Onion": "Onions",
    "Maize": "Maize and products",
    "Banana": "Bananas",
    "Groundnut": "Groundnuts",
    "Soyabean": "Soyabeans",
    "Rapeseed &Mustard": "Rape and Mustardseed",
    "Jowar": "Sorghum and products",
    "Barley": "Barley and products",
    "Sugarcane": "Sugar cane",
    "Tomato": "Tomatoes and products",
}

FAOSTAT_SOURCE = ("FAOSTAT Food Balance Sheets (FBS), India, bulk file "
                  "FoodBalanceSheets_E_Asia.zip (FAO, last modified 2025-10-28), CC BY 4.0")
CENSUS_SOURCE = ("Census of India 2011 state populations, as tabulated on English Wikipedia "
                 "'List of states and union territories of India by population' (accessed "
                 "2026-09-30); sums to 1.2106 bn")

LIMITATIONS = [
    "Demand is a PROXY: FAOSTAT apparent national use (domestic supply quantity), not observed "
    "market or retail demand.",
    "FAOSTAT is national and annual; there is no seasonal or market-level demand signal.",
    "State values assume uniform per-capita use (Census 2011 population share); diets differ "
    "strongly between states, so state demand is a rough estimate.",
    "FAOSTAT commodity definitions (e.g. 'Rice and products', milled equivalent) may not match "
    "the production statistics' units exactly.",
    "No mandi arrival or price data is used: the data.gov.in API was unreachable when this was "
    "built.",
]


class UnsupportedCropError(ValueError):
    pass


@dataclass(frozen=True)
class DemandData:
    series: dict[tuple[str, str], dict[int, float]]  # (item, element) -> year -> value
    units: dict[tuple[str, str], str]
    population_2011: dict[str, int]


@lru_cache(maxsize=2)
def load_data(reference_dir: Path) -> DemandData:
    series: dict[tuple[str, str], dict[int, float]] = {}
    units: dict[tuple[str, str], str] = {}
    with open(reference_dir / "faostat_fbs_india.csv", newline="") as f:
        for r in csv.DictReader(f):
            key = (r["item"], r["element"])
            series.setdefault(key, {})[int(r["year"])] = float(r["value"])
            units[key] = r["unit"]
    with open(reference_dir / "census2011_state_population.csv", newline="") as f:
        pop = {r["state"]: int(r["population_2011"]) for r in csv.DictReader(f)}
    return DemandData(series, units, pop)


def _naive(years: list[int], values: list[float], target: int) -> float:
    return values[-1]


def _trend(years: list[int], values: list[float], target: int) -> float:
    ys, vs = np.array(years[-TREND_WINDOW:], float), np.array(values[-TREND_WINDOW:], float)
    if len(ys) < 3:
        return values[-1]
    slope, intercept = np.polyfit(ys, vs, 1)
    return float(slope * target + intercept)


METHODS = {"naive": (_naive, "last observed value"),
           "linear_trend": (_trend, f"OLS linear trend on the previous {TREND_WINDOW} years")}


def backtest(years: list[int], values: list[float], fn) -> tuple[float, list[float]]:
    """Rolling-origin 1-step-ahead backtest. Returns (MAPE %, absolute percentage errors)."""
    errs = []
    for i in range(len(years) - BACKTEST_YEARS, len(years)):
        pred = fn(years[:i], values[:i], years[i])
        errs.append(abs(pred - values[i]) / values[i])
    return 100 * float(np.mean(errs)), errs


def forecast(data: DemandData, crop: str, target_year: int, state: str | None = None) -> dict:
    item = CROP_TO_ITEM.get(crop.strip())
    if item is None:
        raise UnsupportedCropError(f"no FAOSTAT demand proxy mapped for {crop!r}; supported: "
                                   f"{sorted(CROP_TO_ITEM)}")
    key = (item, ELEMENT)
    hist = data.series.get(key)
    if not hist:
        raise UnsupportedCropError(f"FAOSTAT has no {ELEMENT!r} series for {item!r}")
    years = sorted(hist)
    values = [hist[y] for y in years]
    unit_fao = data.units[key]  # "1000 t"
    to_tonnes = 1000.0 if unit_fao == "1000 t" else 1.0

    backtests, errors = [], {}
    for name, (fn, desc) in METHODS.items():
        mape, errs = backtest(years, values, fn)
        backtests.append({"method": f"{name}: {desc}", "mape_pct": round(mape, 2),
                          "n": len(errs)})
        errors[name] = (mape, errs)
    best = min(errors, key=lambda n: errors[n][0])

    share, state_label = 1.0, "India"
    if state:
        match = next((s for s in data.population_2011 if s.lower() == state.strip().lower()), None)
        if match is None:
            raise UnsupportedCropError(f"no Census 2011 population for state {state!r}")
        share = data.population_2011[match] / sum(data.population_2011.values())
        state_label = match

    observed = target_year in hist
    horizon = max(0, target_year - years[-1])
    if observed:
        national = hist[target_year]
        interval = None
        method = f"observed FAOSTAT value for {target_year}"
    elif target_year < years[0]:
        raise UnsupportedCropError(f"target year {target_year} is before the FAOSTAT series "
                                   f"({years[0]}-{years[-1]})")
    else:
        fn = METHODS[best][0]
        national = fn(years, values, target_year)
        q = float(np.quantile(errors[best][1], INTERVAL_QUANTILE)) * np.sqrt(horizon)
        interval = {"lower": national * (1 - q) * to_tonnes * share,
                    "upper": national * (1 + q) * to_tonnes * share,
                    "method": f"+/- {int(INTERVAL_QUANTILE * 100)}th percentile of absolute "
                              f"1-step backtest errors (n={len(errors[best][1])}) x sqrt(horizon "
                              f"{horizon}); empirical, not a guaranteed coverage"}
        method = f"{best}: {METHODS[best][1]} (lowest backtest MAPE)"

    classification = "OBSERVED" if observed and not state else "ESTIMATED" if observed else \
        "FORECAST"
    per_cap = data.series.get((item, PER_CAPITA_ELEMENT), {})
    food = data.series.get((item, FOOD_ELEMENT), {})
    last = years[-1]
    return {
        "crop": crop.strip(),
        "region": state_label,
        "period": f"calendar/marketing year {target_year} (annual)",
        "forecast": round(national * to_tonnes * share, 1),
        "unit": "tonnes",
        "data_classification": classification,
        "interval": interval,
        "method": method,
        "evidence": {
            "faostatItem": item,
            "faostatElement": ELEMENT,
            "demandProxy": "apparent national use (domestic supply quantity)",
            "lastObservedYear": last,
            "lastObservedNationalTonnes": hist[last] * to_tonnes,
            "horizonYears": horizon,
            "nationalValueTonnes": round(national * to_tonnes, 1),
            "stateShareOfPopulation2011": round(share, 5) if state else None,
            "foodUseTonnesLastYear": food.get(last, 0) * to_tonnes if food else None,
            "perCapitaFoodKgLastYear": per_cap.get(last),
        },
        "national_history": [{"year": y, "value": hist[y] * to_tonnes} for y in years],
        "backtests": backtests,
        "limitations": LIMITATIONS,
    }
