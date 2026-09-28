# Frontend

This is the React single-page app for the Agricultural Intelligence Platform. It talks only to the Spring Boot backend and never calls the ML service directly.

Stack: React 19 · TypeScript · Vite 8 · Tailwind CSS 4 + shadcn/ui (Radix) · TanStack Query · React Router · React Hook Form + Zod · Recharts · MapLibre GL.

## Run locally

```bash
npm install
cp .env.example .env.local   # optional; the defaults work
npm run dev                  # http://localhost:5173
```

The backend must be running on `http://localhost:8080` (see `../backend/README.md`) and must allow this origin through CORS. That is its default.

| Script | What it does |
|---|---|
| `npm run dev` | Starts the Vite dev server |
| `npm run build` | Type-checks (`tsc -b`), then runs the production build |
| `npm run lint` | Runs ESLint |
| `npm run preview` | Serves the production build |

There is no test runner yet.

## Environment (`.env.local`)

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | Backend base URL |
| `VITE_INTELLIGENCE_SOURCE` | `mock` | Affects the Supply & Demand data only. `mock` uses a frontend mock labelled `SYNTHETIC`; `api` calls the backend, which currently answers 503. |

## Structure (`src/`)

| Path | Contents |
|---|---|
| `app/` | Router, navigation list (the sidebar and routes are both built from it), placeholder module page |
| `components/` | Design system: `ui/` (shadcn), `layout/`, `forms/`, `maps/`, `charts/`, `feedback/`, `data-display/` |
| `lib/api/client.ts` | **The only HTTP client.** Attaches the bearer token, ends the session on a 401, turns every failure into an `ApiError`, and `describeError()` supplies all user-facing error text. |
| `features/auth/` | Login and register pages, `AuthProvider`, route guards (`RequireAuth`, `PublicOnly`) |
| `features/farms/` | Farm list, profile, and a create/edit wizard (location on a map, farm info, optional soil, review) |
| `features/dashboard/` | Dashboard. Its panels use a **mock provider** (synthetic, labelled); the supply-vs-demand chart uses the intelligence layer. |
| `features/supply-demand/` | Supply & Demand page |
| `features/intelligence/` | Typed API clients and hooks for weather, supply-demand, crop recommendations and risk. Responses are checked with Zod. See [docs/intelligence-api.md](docs/intelligence-api.md). |

## Routes

`/login` and `/register` are public. Every other route needs a signed-in user; a signed-out visitor is sent to `/login` and returned to the page they wanted after signing in.

| Route | Data |
|---|---|
| `/dashboard` | Mock/synthetic, labelled |
| `/farms`, `/farms/new`, `/farms/:id`, `/farms/:id/edit` | Real backend |
| `/supply-demand` | Mock by default; the backend when `VITE_INTELLIGENCE_SOURCE=api` |
| `/crops`, `/market`, `/scenarios`, `/crop-doctor`, `/coordination`, `/alerts`, `/data-operations` | Placeholder ("scheduled for phase N") |

## Session

- **Token storage:** the access token is kept in `localStorage` under `agri.accessToken`, and only `lib/api/client.ts` reads or writes it.
- **Token rejected:** any 401 on a request that carried a token ends the session, clears the cached queries and redirects to `/login`.
- **Refresh:** there are no refresh tokens; the user signs in again once the token expires (1 hour by default).

## Rules

These are in [CLAUDE.md](CLAUDE.md):
- Components use hooks, hooks use `api.ts`, and `api.ts` uses the client.
- Mock data sits behind a data-access layer and is always labelled synthetic.
- Missing data is shown as unavailable, never as a made-up number.
- TypeScript and ESLint must pass.
