# WealthBuilder — Frontend

React 19 + TypeScript (Vite) SPA. Currently implements the auth surface (login + register)
wired to the Spring Boot backend.

## Stack

- React 19 + Vite, strict TypeScript
- `react-router-dom` v7 for routing/guards
- Plain CSS via CSS Modules + shared design tokens (`src/styles/theme.css`) — no UI libs
- React Context + state for auth and theme (no Redux)
- ESLint with `@stylistic` (4-space indent, semicolons, single quotes)

## Prerequisites

The backend must be running on `http://localhost:8080` (see `../backend`). CORS there
allows the Vite dev origin `http://localhost:5173`.

## Setup

```bash
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL if the backend isn't on :8080
```

## Scripts

```bash
npm run dev      # dev server at http://localhost:5173
npm run build    # type-check (tsc -b) + production build
npm run lint     # eslint
npm run preview  # serve the production build
```

## Configuration

`VITE_API_BASE_URL` (in `.env`) points at the backend API; defaults to
`http://localhost:8080/api`. All backend URLs derive from it in `src/constants/api.ts`.

## Structure

```
src/
  components/   per-component dirs (AuthScreen + subcomponents, guards, HomePage, ...)
  context/      AuthContext + ThemeContext (provider / context / hook per folder)
  hooks/        usePrefersReducedMotion
  services/     apiClient (fetch wrapper), authService
  constants/    api URLs, routes, storage keys
  types/        auth + RFC-7807 problem types
  styles/       theme tokens + global reset
```

## Auth flow

- `AuthProvider` holds the bearer token (mirrored to `localStorage`, rehydrated on load)
  and the current user.
- `apiClient` injects `Authorization: Bearer <token>` and parses RFC-7807 errors into a
  typed `ApiError`; a 401 on an authenticated request clears the session.
- `ProtectedRoute` guards `/`; `PublicOnlyRoute` bounces logged-in users away from
  `/login` and `/register`.

## Auth screen

A single CRT-terminal surface hosting both forms, toggled by a VHS tracking-band sweep
(`prefers-reduced-motion` falls back to an instant swap). The screen is intentionally
always dark per the design brief; the light/dark `ThemeProvider` governs the rest of the
app.
