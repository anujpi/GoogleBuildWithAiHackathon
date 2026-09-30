# ML contract: crop suitability, demand, anomaly

Status: implemented for the hackathon MVP (2026-09-30). Consumer: Spring Boot `MlClient.postJson`.
All three are **transparent, non-trained methods**. None of them reports an accuracy it has not
measured, and none of them returns a calibrated probability.

Common rules:

- camelCase JSON; `422` = the inputs can't be served (the `detail` string explains why);
  `503` = data file missing on the server.
- Every response carries `provenance {method, methodVersion, dataSources[], datasetVersion,
  dataClassification, generatedAt}`.

## POST /v1/predict/crop-suitability

Request: `{state, season, soilPh?, irrigated?, topN=5}`. `season` uses the source label
(`Kharif`, `Rabi`, `Summer`, `Whole Year`, `Autumn`, `Winter`).

Response: `{region, season, scoreType, candidates[], cropsConsidered, limitations[], provenance}`.
Each candidate is `{crop, suitabilityScore, components[{name, score|null, weight, evidence}],
evidence[], notAssessed[], history{yearsObserved, firstYear, lastYear, medianYieldTPerHa,
latestAreaHectares, latestProductionTonnes, yieldTrendPctPerYear}}`.

Method (`crop-suit-v1`): a weighted evidence index in [0, 1] over the last 10 years of Kaggle
crop_yield rows for the state and season, excluding quality-flagged rows. Only crops with ≥ 3
years of data are scored.

| component | weight | definition |
|---|---|---|
| yield_rank | 0.30 | percentile of the state's median yield (Production/Area) among all states growing the crop in that season |
| establishment | 0.25 | percentile of the crop's median area among crops in the state and season |
| stability | 0.15 | 1 − coefficient of variation of yield |
| trend | 0.10 | 0.5 + (yield trend in % per year) / 10, clipped to [0, 1] |
| soil_ph | 0.10 | 1 inside an indicative pH range, falling linearly to 0 at 1.5 pH units outside it (only when `soilPh` is given) |
| water | 0.10 | crop water need vs. irrigation and state mean rainfall (only when `irrigated` is given) |

Components that can't be assessed are dropped and the remaining weights renormalised. The pH and
water-need table is **indicative** (cf. FAO ECOCROP), not site-calibrated. `dataClassification`
is `ESTIMATED`.

## POST /v1/predict/demand

Request: `{crop, state?, targetYear}`.

Response: `{crop, region, period, forecast, unit:"tonnes", dataClassification, interval|null,
method, evidence{…}, nationalHistory[{year, value}], backtests[{method, mapePct, n}],
limitations[], provenance}`.

Method (`demand-baseline-v1`):

- **Proxy:** FAOSTAT Food Balance Sheets, India, "Domestic supply quantity" (apparent national
  use), 2010–2023.
- **Forecast:** naive (last value) and an 8-year OLS trend are both back-tested with rolling-origin
  1-step forecasts over the last 6 years. The method with the lower MAPE is used.
- **Interval:** ± the 80th percentile of the absolute 1-step errors × √horizon. This is
  empirical, not a guaranteed coverage.
- **Observed years:** if `targetYear` is in the data, the observed value is returned (`OBSERVED`
  nationally, `ESTIMATED` once apportioned to a state).
- **State apportionment:** × the state's share of Census 2011 population, which assumes uniform
  per-capita use.
- **Classification:** `FORECAST` for years after 2023.

Supported crops: Potato, Wheat, Rice, Onion, Maize, Banana, Groundnut, Soyabean,
Rapeseed &Mustard, Jowar, Barley, Sugarcane, Tomato.

## POST /v1/predict/anomaly

Request: `{metric, unit?, series[{period, value}] (oldest first, 1–500), window=10, threshold=3.5}`.

Response: `{metric, unit, period, status: NORMAL|ANOMALY|INSUFFICIENT_DATA, direction:
HIGH|LOW|NONE, observedValue, baseline, deviation, deviationPct, robustZ, threshold, windowUsed,
method, provenance}`.

Method (`anomaly-robust-z-v1`): the last value is compared with the median of the previous
`window` values, using robust z = 0.6745·(x − median)/MAD. If MAD is 0, the spread falls back to
IQR/1.349; if that is also 0, any change from a flat history counts as an anomaly. Fewer than 5
prior points gives `INSUFFICIENT_DATA`.
