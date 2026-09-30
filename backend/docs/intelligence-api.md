# Intelligence, disease and advisory API

These endpoints need a bearer token (see `auth-api.md`). The frontend calls only these; it never calls the ML
service or Gemini directly.

Boundary: **the ML service predicts** (suitability, supply, demand, anomaly, disease), **Spring Boot decides**
(gap status, risk levels, crop decision), and **Gemini explains** (advisory text).

## GET /api/intelligence/farms/{farmId}?crop=

Returns the full report for one of the caller's farms. Another user's farm gets a 404. `crop` is optional; the
default order is the farm's `currentCrop`, then the top suitability candidate.

Each ML-backed part is a **section** `{status: OK|UNAVAILABLE, data, errorCode, errorMessage}`, so one failing
source never hides the others and nothing is substituted in its place.

| Field | Origin | Notes |
|---|---|---|
| `farm` | DB | Farm context: state, district, season label, irrigation, soil pH and its classification, area in ha |
| `weather` | Open-Meteo (live) | `status: OK/UNAVAILABLE`, `dataClassification: FORECAST`. When unavailable, every value is null and nothing is estimated |
| `suitability` | ML `/v1/predict/crop-suitability` | Passed through unchanged. The score is an evidence index, **not a probability** |
| `supply` | ML `/v1/predict/supply` via `SupplyForecastService` | The target year is the last historical year + 1 (currently 2020), and the area assumption is the last observed area (`targetAreaSource: REQUEST_INPUT`) |
| `demand` | ML `/v1/predict/demand` | For the same year as supply (needed for the gap) |
| `demandOutlook` | ML `/v1/predict/demand` | For the current calendar year (`FORECAST`) |
| `gap` | backend rule | `supplyTonnes − demandTonnes`. `BALANCED` within ±10 % of demand, otherwise `SURPLUS`/`DEFICIT`. Classified `ESTIMATED` |
| `anomalies[]` | ML `/v1/predict/anomaly` | Yield and production (production_history) and the national demand proxy (FAOSTAT) |
| `risk` | backend rules `backend-decision-rules-v1` | Factors `{category, level: HIGH/MODERATE/LOW/UNKNOWN, reason, evidenceSource}`. Thresholds: rain over 7 days >50 mm moderate, >100 mm high; max temperature ≥36 °C moderate, ≥40 °C high; humidity ≥80 % at 15–30 °C gives moderate disease conditions; latest yield anomalously LOW is high; stability <0.6 is moderate; surplus >25 % is high. `overall` is the highest known level |
| `decision` | backend rules | `SUITABLE_CANDIDATE` (index ≥ 0.60 and overall risk not HIGH), `SUITABLE_WITH_MARKET_RISK`, `REVIEW`, `NOT_RECOMMENDED` (index < 0.45), or `INSUFFICIENT_EVIDENCE`. Demand never overrides suitability. `alternatives` lists up to 3 other crops with index ≥ 0.60 |
| `dataNotices[]` | backend | Plain-language caveats to show with the report |

## POST /api/disease/diagnose (multipart)

Parts: `image` (JPEG or PNG, ≤ 10 MB) and an optional `crop`. Returns:

- `ml`: the ML response, passed through unchanged. Its `prediction.probability` is an uncalibrated softmax value.
- `needsExpertReview`: true when the probability is below 0.70. This is a platform policy, not a calibrated
  threshold.
- `cropWarning`: set when `crop` isn't covered by the model (only Potato and Tomato are).
- `limitations[]`

Errors:

- `400 IMAGE_REQUIRED`
- `415 UNSUPPORTED_IMAGE_TYPE`
- `422 ML_REQUEST_REJECTED`: the file is corrupt or not an image
- `503 ML_MODEL_UNAVAILABLE`: no model is loaded

## POST /api/advisories

Body: `{farmId, crop?, language: "en"|"hi", disease?: {crop, disease, probability, modelVersion, needsExpertReview}}`.

The backend rebuilds the farm report on the server, turns it into a compact evidence object, and sends that
object alone to Gemini. The system prompt forbids new facts, and the response is constrained to a JSON schema. The
backend then checks every number in the generated text against the evidence and returns any that don't match in
`numbersNotFoundInEvidence`.

Response fields:

- `language`
- `generatedBy`: `GEMINI` or `TEMPLATE_FALLBACK`
- `model`, `aiGenerated`
- `explanation`, `keyFactors[]`, `recommendedActions[]`, `uncertainty[]`
- `numbersNotFoundInEvidence[]`, `groundingNote`
- `fallbackReason`: why the template was used, when it was
- `evidence`: exactly what was sent to the model
- `generatedAt`

If `GEMINI_API_KEY` isn't set or the Gemini call fails, a deterministic template fills fixed English or Hindi
sentences with evidence values. It is always labelled `TEMPLATE_FALLBACK` and `aiGenerated: false`.

Configuration: `GEMINI_API_KEY` (required for AI), `GEMINI_MODEL` (default `gemini-2.5-flash`),
`WEATHER_BASE_URL` (default Open-Meteo).
