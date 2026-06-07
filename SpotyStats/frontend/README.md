# SpotyStats Frontend

React 19 + TypeScript SPA for SpotyStats. Talks to the Spring BFF — no OAuth logic, no
tokens in the browser, just same-origin JSON calls with a session cookie. See the repo-root
[`PLAN.md`](../PLAN.md) and especially
[`docs/frontend-backend-integration.md`](../docs/frontend-backend-integration.md) for how
the SPA and backend fit together.

## Prerequisites

- **Node 20+** and npm.
- The **backend running on `127.0.0.1:8080`** — the Vite dev server proxies `/api`, `/auth`,
  `/login`, and `/oauth2` to it (see `vite.config.ts`). Without the backend, every page is a
  sign-in screen. Backend setup: [`../backend/README.md`](../backend/README.md).

## Run (local dev)

```bash
npm install
npm run dev
```

Then open **`http://127.0.0.1:5173`** — *not* `localhost:5173`. Cookies are scoped per host
and the Spotify redirect URI is registered for `127.0.0.1`; using `localhost` makes every
request look anonymous.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Vite dev server with HMR on `127.0.0.1:5173` |
| `npm run build` | Type-check (`tsc -b`) + production build to `dist/` |
| `npm run lint` | ESLint over the whole project |
| `npm run preview` | Serve the production build locally |

## Structure & conventions

```
src/
  components/   reusable UI — one directory per component:
                Foo/Foo.tsx + Foo/Foo.module.css (+ optional Foo.helpers.ts)
  pages/        route-level components, same per-directory layout
  hooks/        custom hooks
  services/     fetch-based API clients (api.ts owns the CSRF header logic)
  styles/       theme.css (design tokens) + global.css (reset)
```

- Functional components, **named exports only** (no `export default`).
- **No `any`** — `unknown` + narrowing, or a proper type.
- Styling: CSS Modules per component; shared values only via `var(--…)` tokens from
  `src/styles/theme.css`. No chart library — charts are hand-rolled SVG components.
- Routing: React Router; auth gating lives in `App.tsx` (probes `GET /api/me` on load).
