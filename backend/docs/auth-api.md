# Auth API contract

Stateless JWT authentication.
- Send `Authorization: Bearer <accessToken>` on every protected request.
- There are no refresh tokens: when the token expires, log in again.
- Errors use the standard `ApiError` shape (see `farm-api.md`).

| Method | Path | Auth | Body | Success | Errors |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | public | `RegisterRequest` | `201` + `UserResponse` | 400 `VALIDATION_ERROR`, 409 `EMAIL_ALREADY_REGISTERED` |
| POST | `/api/auth/login` | public | `LoginRequest` | `200` + `LoginResponse` | 400 `VALIDATION_ERROR`, 401 `INVALID_CREDENTIALS` |
| GET | `/api/auth/me` | Bearer | none | `200` + `UserResponse` | 401 `UNAUTHORIZED` |
| GET | `/actuator/health` | public | none | `200` | none |

## RegisterRequest

| Field | Rules |
|---|---|
| `fullName` | required, not blank, ≤ 200 chars |
| `email` | required, valid email, ≤ 254 chars. Trimmed and lower-cased, so uniqueness is case-insensitive. |
| `password` | required, 8–72 characters. Stored only as a BCrypt hash. |

Public registration can't set a role. Every new account is `FARMER`, and a `role` field in the body is ignored.

## LoginRequest

`{ "email": "...", "password": "..." }`. The email is case-insensitive.

An unknown email, a wrong password and a disabled account all return the same `401 INVALID_CREDENTIALS` / "Invalid email or password".

## LoginResponse

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": "uuid", "fullName": "Anuj Sharma", "email": "anuj@example.com", "role": "FARMER" }
}
```

`expiresIn` is in seconds and comes from `JWT_EXPIRATION` (default 1 hour).

## UserResponse

`{ "id", "fullName", "email", "role" }`. `role` is one of `FARMER`, `FPO`, `AGRICULTURAL_OFFICER` or `ADMIN`. No password or password hash is ever returned.

## Token behaviour

- **401 `UNAUTHORIZED`**: the token is missing, malformed, expired or badly signed, or the account has been disabled. The message is always "Authentication is required". Clear the token and send the user to login.
- **403 `FORBIDDEN`**: the token is valid, but the role may not use that endpoint.
- **Account changes apply immediately.** The account is re-checked on every request, so a disabled user's existing token stops working at once, and role changes don't need a new token.

## Server configuration

| Env var | Required | Meaning |
|---|---|---|
| `JWT_SECRET` | **yes** | HS256 signing key, at least 32 bytes. Startup fails without it. Generate one with `openssl rand -base64 48`. Never commit it. |
| `JWT_EXPIRATION` | no | Token lifetime, e.g. `PT1H` or `30m`. Default `PT1H`. |
