Project-wide rules. Each service carries its own `CLAUDE.md` (`twitter_api_gateway/`, `twitter_tweet_service/`, `twitter_mail_service/`, `twitter_timeline_service/`) —
read the one for the service being touched.

## Writing frontend code

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

## Skills

Load skill bodies on demand (`/skill-name`). Descriptions live here — don't repeat them elsewhere.

| Area | Skills |
| --- | --- |
| Code style | `frontend-code-style` — mandatory before any `.ts`/`.tsx`/`.css` edit (see Writing frontend code) |
| Responsive | `responsive-fix` — a named component/view breaks at a given resolution |

Done = re-check touched files against any loaded skill's rules.
