# Regional and admin API contract

MASTER_SPEC §4, §6.6 and §6.7.
- **Auth:** every endpoint needs a Bearer token.
- **Enforcement:** role gates are enforced in `SecurityConfig`; district and ownership checks are done in the services.
- **Errors:** the standard `ApiError` body (see `intelligence-api.md`).

## Roles

| Role | How obtained | Own farms | Regional view | Admin API |
|---|---|---|---|---|
| FARMER | Public registration (always) | yes | 403 | 403 |
| FPO | Set by an ADMIN | yes | assigned districts | 403 |
| AGRICULTURAL_OFFICER | Set by an ADMIN | 403 on `/api/farms/**` | assigned districts | 403 |
| ADMIN | Set by an ADMIN, or bootstrapped via `BOOTSTRAP_ADMIN_EMAIL` | 403 on `/api/farms/**` | all districts | yes |

- **Where roles come from:** roles and district assignments come only from the database. Anything a client sends (`ownerId`, `role`, `userId`, district claims) is ignored.
- **When changes apply:** the principal is reloaded on every request, so a role change or a disabled account takes effect immediately, even on an existing token.
- **Hidden resources:** a resource outside the caller's scope is reported as missing (404 `FARM_NOT_FOUND` / `DISTRICT_NOT_FOUND`), never as 403.

## Regional view (FPO, AGRICULTURAL_OFFICER: assigned districts; ADMIN: all). Read-only, GET only.

| Endpoint | Response |
|---|---|
| `GET /api/regional/districts` | `[{ districtId, stateId, label, farmCount }]` |
| `GET /api/regional/farms?districtId=` | `FarmSummary[] { id, name, districtId, districtLabel, area, areaUnit, currentCrop, season, soilDataAvailable, updatedAt }`. No owner data. A district not visible to the caller → 404 `DISTRICT_NOT_FOUND` |
| `GET /api/regional/farms/{id}` | `FarmResponse` (same as `farm-api.md`). A farm outside the caller's districts → 404 `FARM_NOT_FOUND` |
| `GET /api/regional/farms/{id}/weather`, `/crop-evidence`, `/risk` | Same bodies as the owner endpoints in `intelligence-api.md` |

Legacy ownerless farms never appear in the regional view.

## Admin (ADMIN only)

`Page<T>` is `{ items[], page, size, totalItems, totalPages }`. `page` is zero-based and `size` is 1–100 (default 20).

| Endpoint | Body | Response | Errors |
|---|---|---|---|
| `GET /api/admin/users?page&size&role&q` | none | `Page<UserResponse>`. `q` is a case-insensitive substring match on email or name | 400 for `size` > 100 |
| `PATCH /api/admin/users/{id}` | `{ "role"?: Role, "enabled"?: boolean }` | `UserResponse` | 404 `USER_NOT_FOUND`; 409 `CONFLICT` if an admin demotes or disables themselves, or the last enabled ADMIN would be lost |
| `PUT /api/admin/users/{id}/districts` | `{ "districtIds": ["up-agra", …] }` (replaces the whole set) | `UserResponse` | 409 `CONFLICT` unless the user is FPO/OFFICER; 400 `VALIDATION_ERROR` (`details[].field = districtIds`) for an unknown district |
| `GET /api/admin/system/health` | none | See below | none |
| `GET /api/admin/ml/models` | none | See below | none |
| `POST /api/admin/reference/sync` | none | `{ id, syncedAt, status: SUCCEEDED\|FAILED, mlModelVersion, message }`. `message` is the error code of a failed sync | none |
| `GET /api/admin/audit?page&size&action&actorUserId&from&to` | none | `Page<{ id, occurredAt, actorUserId, action, targetType, targetId, details, requestId }>`, newest first | none |
| `GET /api/admin/farms/unowned` | none | `FarmSummary[]` (legacy farms from before accounts existed) | none |
| `POST /api/admin/farms/{id}/owner` | `{ "userId": "uuid" }` | `FarmSummary` | 404 `FARM_NOT_FOUND` / `USER_NOT_FOUND`; 409 `CONFLICT` if the farm already has an owner (owners are never transferred) or the user isn't FARMER/FPO |

`UserResponse` = `{ id, fullName, email, role, enabled, assignedDistrictIds[] }`.

**System health** (`GET /api/admin/system/health`):

```jsonc
{ "database": { "status": "UP|DOWN" },
  "mlService": { "status": "UP|DEGRADED|DOWN", "url": "…", "latencyMs": 12, "models": [ML /health models] },
  "weatherProvider": { "status": "UP|DEGRADED|DOWN", "provider": "OPEN_METEO", "lastSuccessAt": "…", "lastError": null },
  "referenceSync": { "lastSyncedAt": "…|null", "status": "SUCCEEDED|FAILED|null" },
  "build": { "version": null } }
```

- **`mlService.status`:** DEGRADED means ML answers but a model isn't READY; DOWN means ML is unreachable, and then `latencyMs` is null.
- **`weatherProvider.status`:** if the provider hasn't been used since startup, this endpoint makes one probe call first.
- **Unknown values:** anything not known is `null`, never invented. For example, `build.version` is null because no build-info is generated.

**ML models** (`GET /api/admin/ml/models`) returns `{ mlError, models[], datasets[], modelEvaluation }`:
- **`mlError`:** the §13 code when ML can't be reached, in which case `models` is empty.
- **`modelEvaluation`:** the served supply model's real evaluation. It's read from one real estimate, and is `null` when no model is READY.

## Audit actions

`USER_REGISTERED`, `LOGIN_SUCCEEDED`, `LOGIN_FAILED` (stores the normalized email only), `ROLE_CHANGED`, `USER_ENABLED`, `USER_DISABLED`, `DISTRICTS_ASSIGNED`, `FARM_CREATED`, `FARM_UPDATED`, `FARM_OWNER_ASSIGNED`, `REFERENCE_SYNCED`, `ADMIN_BOOTSTRAPPED`.

`details` never contains passwords, tokens or secrets. Every row carries the request's correlation id.
