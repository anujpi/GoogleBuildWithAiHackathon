# Farm API contract

Base URL (local): `http://localhost:8080`. CORS allows `http://localhost:5173` (override with `CORS_ALLOWED_ORIGINS`).

**Authentication required.** Every endpoint needs `Authorization: Bearer <accessToken>` (see `auth-api.md`), and the caller must be a `FARMER` or `FPO`.
- The farm owner is always the authenticated user. Never send `ownerId`; it is ignored.
- Every endpoint sees only the caller's own farms. Another user's farm returns `404 FARM_NOT_FOUND`, exactly like a farm that doesn't exist.

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| POST | `/api/farms` | `FarmRequest` | `201` + `FarmResponse`, `Location: /api/farms/{id}` | `400` |
| GET | `/api/farms` | – | `200` + `FarmResponse[]`, newest first | – |
| GET | `/api/farms/{id}` | – | `200` + `FarmResponse` | `400`, `404` |
| PUT | `/api/farms/{id}` | `FarmRequest` | `200` + `FarmResponse` | `400`, `404` |

`GET /api/farms` is unpaged for now. When pagination arrives it will be a new response shape, announced as a contract change.

## FarmRequest (POST and PUT)

PUT is a full replacement: send every field. An omitted or `null` optional field is stored as `null`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | not blank, ≤ 200 chars |
| `area` | number | yes | > 0, in `areaUnit` |
| `areaUnit` | `ACRE` \| `HECTARE` | yes | |
| `irrigationType` | `RAIN_FED` \| `DRIP` \| `SPRINKLER` \| `CANAL` \| `BOREWELL` \| `OTHER` | yes | |
| `currentCrop` | string \| null | no | ≤ 100 chars, free text until the Crop domain exists |
| `previousCrop` | string \| null | no | ≤ 100 chars |
| `season` | `KHARIF` \| `RABI` \| `ZAID` \| `OTHER` | yes | |
| `location` | object | yes | see below |
| `soilProfile` | object \| null | no | see below. Omit or send `null` when no soil data exists. |

`location`

| Field | Type | Required | Rules |
|---|---|---|---|
| `latitude` | number | yes | −90 … 90, WGS84 decimal degrees |
| `longitude` | number | yes | −180 … 180, WGS84 decimal degrees |
| `state` | string | yes | not blank, ≤ 100 |
| `district` | string | yes | not blank, ≤ 100 |
| `taluk` | string \| null | no | ≤ 100 |
| `addressLabel` | string \| null | no | ≤ 255, display text only |

`soilProfile` is optional: a farm can exist without soil data. On PUT, omitting it removes any stored profile. Every measurement inside it is also optional. **A missing value is returned as `null`. It is never estimated or filled in.**

| Field | Unit (Soil Health Card convention) | Rules |
|---|---|---|
| `ph` | – | 0 … 14 |
| `electricalConductivity` | dS/m | ≥ 0 |
| `organicCarbon` | % | ≥ 0 |
| `nitrogen`, `phosphorus`, `potassium` | kg/ha | ≥ 0 |
| `sulphur`, `zinc`, `iron`, `manganese`, `copper`, `boron` | ppm (mg/kg) | ≥ 0 |
| `source` | `SOIL_HEALTH_CARD` \| `LAB_REPORT` \| `MANUAL` \| `REGIONAL_ESTIMATE` \| `OTHER` | **required** |
| `dataClassification` | `OBSERVED` \| `ESTIMATED` \| `SYNTHETIC` | **required**. The UI must show non-`OBSERVED` data as such |
| `measuredAt` | ISO date `YYYY-MM-DD` \| null | not in the future |
| `confidence` | number \| null | 0 … 1 |

The API does not convert units. Values are stored and returned exactly as sent.

`source` limits which `dataClassification` is allowed. Any other pair is rejected with `INCONSISTENT_SOIL_PROVENANCE`:

| `source` | Allowed `dataClassification` |
|---|---|
| `SOIL_HEALTH_CARD`, `LAB_REPORT` | `OBSERVED` |
| `REGIONAL_ESTIMATE` | `ESTIMATED`, `SYNTHETIC` |
| `MANUAL`, `OTHER` | any |

## FarmResponse

Same fields as the request, plus:

- `id`, `location.id`, `soilProfile.id`: UUID strings. Location and soil IDs stay the same across updates.
- `createdAt`, `updatedAt`: ISO-8601 UTC instants, e.g. `2026-09-23T16:19:34.484313Z`. `updatedAt` changes on every PUT.
- `soilDataAvailable`: boolean. `false` means no soil data exists for this farm, and `soilProfile` is then `null`. The UI must show soil as unavailable, not as zero or default values. Later intelligence calculations will treat missing soil as a missing input and lower confidence.
- Every field is always present. Optional values come back as `null`, not omitted.
- Numbers come back as JSON numbers with the precision that was sent.

## Errors

All errors share one shape:

```json
{
  "timestamp": "2026-09-23T16:19:35.719Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/farms",
  "details": [{ "field": "location.latitude", "message": "must be less than or equal to 90" }]
}
```

| `code` | Status | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Field rules fail. `details[].field` uses dotted paths such as `soilProfile.confidence`. |
| `MALFORMED_REQUEST` | 400 | Invalid JSON, wrong type, or unknown enum value |
| `INVALID_PARAMETER` | 400 | `{id}` is not a UUID |
| `INCONSISTENT_SOIL_PROVENANCE` | 400 | `soilProfile.source` and `dataClassification` contradict each other (table above) |
| `UNAUTHORIZED` | 401 | Missing, invalid or expired token, or a disabled account |
| `FORBIDDEN` | 403 | Authenticated, but the role has no farm access (`ADMIN`, `AGRICULTURAL_OFFICER` for now) |
| `FARM_NOT_FOUND` | 404 | No farm with that id **owned by the caller** |
| `INTERNAL_ERROR` | 500 | Unexpected failure. No internals are exposed. |

`details` is an empty array except for `VALIDATION_ERROR`.
