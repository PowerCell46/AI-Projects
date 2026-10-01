## Running the project

`npm install`, then `npm run dev`; serves on **:5173**. Other scripts: `npm run build`
(`tsc -b && vite build`), `npm run lint`, `npm run preview`.

`/api/*` is proxied to `http://localhost:8080` (see `vite.config.ts`) — run the gateway first
(`mvn spring-boot:run` from `../signal_flow_api_gateway`, needs `docker compose up postgres` from
`../` first) or API calls fail.

To run containerized instead, `docker compose up` from `../` once the gateway and this frontend are
wired into `docker-compose.yml` — neither is yet (see that file's own state); `nginx.conf` reverse-
proxies `/api/*` to an `app` service on port 8080.

## Writing code

**Before writing or editing any `.ts`, `.tsx` or `.css` file, invoke the `frontend-code-style` skill first.** It
carries the full style rules, conventions and examples (formatting, naming, TypeScript, components, CSS). No formatter
or lint rule enforces them — hold them by hand. Applies to hand-written and AI-generated code alike.

Hard rules — these hold whether or not the skill is loaded:

- Functional components only. No class components.
- Keep components small; one component per file, co-located `<Name>.css` next to `<Name>.tsx`.
- API access lives in `/src/api` (one module per resource, base URL from `VITE_BASE_API_URL`).
  Components never hardcode origins or ports.
- No raw hex in components — semantic tokens in CSS (`src/index.css`). Base font `1rem` (16px at the browser default), line-height 1.5.
- Touch targets ≥ 44×44px; visible labels on inputs; errors next to the field, not only at the top.
- Respect `prefers-reduced-motion`; never remove focus rings; icon-only buttons need labels.

`src/` layout:

- `/api` — backend clients (base URL handling, request/response shapes)
- `/components` — one folder per page-level component (`<Name>.tsx` + `<Name>.css`); child components nest inside their sole consumer's folder (e.g. `AuthPage/Panel/`, `AuthPage/AuthForm/`)
- `/App.tsx`, `/main.tsx`, `/index.css`, `/vite-env.d.ts`

`public/` holds the favicon and `robots.txt` — this app is not a public tool, so it disallows all crawling.

Keep `tsc -b` clean — `noUnusedLocals`/`noUnusedParameters` are on.

`auth-design.md` (`../`, one level up) is the design spec for the sign-in/create-account page — read it
before touching `components/AuthPage`.

## Skills

Load skill bodies on demand (`/skill-name`). Descriptions live here — don't repeat them elsewhere.

| Area | Skills |
| --- | --- |
| Code style | `frontend-code-style` — mandatory before any `.ts`/`.tsx`/`.css` edit (see Writing code) |
| Visual design | `ui-ux-pro-max` — palettes, type, layout, UX; its output is translated to `frontend-code-style` rules, never pasted |
| Responsive | `responsive-fix` — a named component/view breaks at a given resolution |

Done = re-check touched files against any loaded skill's rules.

---

## Working with me

- **No filler.** Skip "Great question!", "Here's a summary:", "I hope this helps." Get to the point.
- **Concision above all.** In all responses and plans — be extremely concise. Sacrifice grammar for brevity.
- **After completing a task:** one or two sentences max — what changed and anything worth flagging. No listing every file touched, no restating the request.
- **Auto-run builds and checks only for significant changes.** Run `npm run build` or `npm run lint` ONLY WHEN the change is behavior-affecting or structurally meaningful: new or removed features, modified logic, API/contract changes, dependency/build changes, or broader refactors. DO **NOT** run them for comment-only edits, formatting-only changes, typo fixes, or similarly tiny updates. If checks fail due to my change — fix them, then report. If the cause is unclear — stop and report immediately.
- **No silent assumptions.** If you hit an unknown, a missing detail, or multiple valid implementation paths, stop immediately and ask a focused open or choice question before continuing. Do not guess; this is a hard rule.
- **`DECISIONS.md`** (this directory) tracks non-obvious decisions that would be hard to re-derive from the code alone. Read it at the start of any non-trivial task; add to it when one is made.

### Autonomy

- **NEVER IMPLEMENT ANYTHING UNLESS I EXPLICITLY TELL YOU TO IMPLEMENT.** This overrides everything below. A question ("can we do X?", "how would Y work?", "I don't like Z") is a request for discussion/a proposal — NOT a license to write, edit, or delete code. Propose the approach and wait. Only act when I use an explicit imperative ("implement", "do it", "go ahead", "write it", "apply", "proceed").
- **Simple, well-scoped tasks** (fix this bug, add this field, refactor this method) — propose, then implement only on explicit go-ahead.
- **Complex tasks** (architecture, new patterns, meaningful design options) — describe approach in 2-3 sentences, wait for approval, then implement. At the end of any plan, list unresolved questions concisely (grammar optional) — if any.
- **Stuck mid-task** — stop and ask. Don't guess on hard prerequisites.

### Pushback and disagreement

- Push back **once**, bluntly — state the concern and why. If the user still wants to proceed their way, do it their way without further resistance.
- If asked to do something that violates a CLAUDE.md rule — flag it once, then comply if confirmed.
- Never add `// TODO`, `// !`, or `// ?` inline comments on your own initiative — flag concerns in the response instead. Exception: when you explicitly ask for one ("add a TODO here", "leave a `// !` on this").

### Scope

- Stay strictly in scope. If something adjacent is worth fixing or flagging, mention it in **one line at the end** — never silently expand the change.
- Research silently. Report findings and conclusions, not the exploration process.
