## Running the project

`npm install`, then `npm run dev`; serves on **:5173**. Other scripts: `npm run build`
(`tsc -b && vite build`), `npm run lint`, `npm run preview`.

`/api/*` is proxied to the review backend at `http://localhost:8080` (see
`vite.config.ts`) — run `backend` first (`mvn spring-boot:run`
on **:8080**) or API calls fail.

To run the whole project containerized instead (frontend + backend + Mongo + Redis),
`docker compose up` from the repo root; frontend serves on **:5173** via `Dockerfile`/
`nginx.conf` (nginx reverse-proxies `/api/*` to the `app` container).

## Writing code

**Before writing or editing any `.tsx`/`.css` file, invoke the `ui-ux-pro-max` skill first.**
It carries the full UI rules, style data and pre-delivery checklist. Applies to hand-written
and AI-generated code alike. Always invoke its search script by full path — never assume
a working directory.

Hard rules — these hold whether or not the skill is loaded:

- Functional components only. No class components.
- 4-space indent in all frontend source (`.tsx`, `.ts`, `.css`, config). No formatter enforces it — hold it by hand.
- No crammed one-liners: multi-argument calls and object literals put one argument/field per line, single-statement `if` bodies still get braces on their own lines, `else` starts on its own line (never `} else {`), blank lines separate logical steps inside a block.
- Promise chains break before each link: `.then`/`.catch`/`.finally` always start on their own line, never trail the closing paren.
- Keep components small; one component per file, co-located `<Name>.css` next to `<Name>.tsx`.
- API access lives in `/src/api` (one module per resource, base URL from `VITE_BASE_API_URL`).
  Components never hardcode origins or ports.
- No raw hex in components — semantic tokens in CSS. Base font 16px, line-height 1.5.
- Touch targets ≥ 44×44px; visible labels on inputs; errors next to the field, not only at the top.
- Respect `prefers-reduced-motion`; never remove focus rings; icon-only buttons need labels.

`src/` layout:

- `/api` — backend clients (base URL handling, request/response shapes)
- `/components` — one folder per page-level component (`<Name>.tsx` + `<Name>.css`); child components nest inside their sole consumer's folder (e.g. `RegisterPanel/DocumentTable/`, `ControlStrip/ScopeTabs/`)
- `/App.tsx`, `/main.tsx`, `/index.css`, `/App.css`, `/vite-env.d.ts`

`public/` holds the favicon and `robots.txt` (allow-all — this is a public tool, not an internal panel).

Keep `tsc -b` and `eslint .` clean — `noUnusedLocals`/`noUnusedParameters` are on.

## Skills

Load skill bodies on demand (`/skill-name`). Descriptions live here — don't repeat them elsewhere.

| Area | Skills |
| --- | --- |
| UI/UX | `ui-ux-pro-max` — mandatory before any `.tsx`/`.css` edit (see Writing code) |
| Decisions | `grill-me` — open-ended/vague tasks, or design calls with real tradeoffs |

Done = re-check touched files against any loaded skill's rules.

---

## Working with me

- **No filler.** Skip "Great question!", "Here's a summary:", "I hope this helps." Get to the point.
- **Concision above all.** In all responses and plans — be extremely concise. Sacrifice grammar for brevity.
- **After completing a task:** one or two sentences max — what changed and anything worth flagging. No listing every file touched, no restating the request.
- **Auto-run builds and checks only for significant changes.** Run `npm run build` or `npm run lint` ONLY WHEN the change is behavior-affecting or structurally meaningful: new or removed features, modified logic, API/contract changes, dependency/build changes, or broader refactors. DO **NOT** run them for comment-only edits, formatting-only changes, typo fixes, or similarly tiny updates. If checks fail due to my change — fix them, then report. If the cause is unclear — stop and report immediately.
- **No silent assumptions.** If you hit an unknown, a missing detail, or multiple valid implementation paths, stop immediately and ask a focused open or choice question before continuing. Do not guess; this is a hard rule.
- **`DECISIONS.md`** (repo root, two levels up) tracks non-obvious decisions that would be hard to re-derive from the code alone. Read it at the start of any non-trivial task; add to it when one is made.

### Autonomy

- **NEVER IMPLEMENT ANYTHING UNLESS I EXPLICITLY TELL YOU TO IMPLEMENT.** This overrides everything below. A question ("can we do X?", "how would Y work?", "I don't like Z") is a request for discussion/a proposal — NOT a license to write, edit, or delete code. Propose the approach and wait. Only act when I use an explicit imperative ("implement", "do it", "go ahead", "write it", "apply", "proceed").
- **Simple, well-scoped tasks** (fix this bug, add this field, refactor this method) — propose, then implement only on explicit go-ahead.
- **Complex tasks** (architecture, new patterns, meaningful design options) — describe approach in 2-3 sentences, wait for approval, then implement. At the end of any plan, list unresolved questions concisely (grammar optional) — if any. Suggest a suitable skill if appropriate — see Skills section.
- **Stuck mid-task** — stop and ask. Don't guess on hard prerequisites.
- **Open-ended or vague task** — suggest `/grill-me` rather than interpreting unilaterally.

### Pushback and disagreement

- Push back **once**, bluntly — state the concern and why. If the user still wants to proceed their way, do it their way without further resistance.
- If asked to do something that violates a CLAUDE.md rule — flag it once, then comply if confirmed.
- Never add `// TODO`, `// !`, or `// ?` inline comments on your own initiative — flag concerns in the response instead. Exception: when you explicitly ask for one ("add a TODO here", "leave a `// !` on this").

### Scope

- Stay strictly in scope. If something adjacent is worth fixing or flagging, mention it in **one line at the end** — never silently expand the change.
- Research silently. Report findings and conclusions, not the exploration process.

