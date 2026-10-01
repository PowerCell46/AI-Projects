---
name: frontend-code-style
description: Frontend code style for this codebase — React + TypeScript + plain CSS: formatting, naming, TS type rules, URL constants, component anatomy and extraction, state/context, tables (TanStack), CSS units/naming/scaling. Use before writing or editing any `.ts`, `.tsx` or `.css` file under `frontend/`, and when judging existing frontend code for style.
---

# Frontend code style

**Precedence:** `frontend/CLAUDE.md` hard rules → this skill → the surrounding code. Match the surrounding code
wherever this skill is silent; where existing code breaks a rule here, follow the rule, not the code.

Design skills (`ui-ux-pro-max`) decide **what** it looks like (colors, type, spacing values, motion); this skill
decides **how** it's written. When their output conflicts with a rule here (Tailwind classes, raw hex, px), translate
it: hex → a new token in `src/index.css`, px → rem. Never paste it as is.

This file holds what applies to every frontend file. Load the reference for the kind of file you're touching:

- `references/typescript.md` — any `.ts`/`.tsx`: `interface` vs `type`, strictness, `as`/`!`, URL constants
- `references/react.md` — any `.tsx`: component anatomy, extraction, state/context, effects, JSX markup, tables
- `references/css.md` — any `.css`: units, class naming, scaling, overflow

**Rules vs guidelines.** Rules are hard: never break one when writing; when reviewing, report every violation.
Guidelines state the intent: apply them with judgement; when reviewing, mention a miss but don't count it as a
violation. Every section is a rule unless its heading says "(guideline)" or it sits under **Guidelines**.

Examples use `// flag` for what to avoid and `// prefer` for what to write.

---

## Stack

- React (latest stable) + TypeScript. Functional components only.
- Plain CSS: one co-located `<Name>.css` per component, imported by that component. No Tailwind, CSS-in-JS or CSS
  modules.
- State with React state and context only. No Redux, Zustand, Jotai, React Query or similar.
- Tables with `@tanstack/react-table` (see `references/react.md`).
- Any other new dependency needs the user's explicit approval.

## Principles

- **Readability is the end goal.** Small single-purpose functions, clear names, no hidden side effects.
- **Keep functions short.** When a function does more than one thing, or needs a comment to separate its steps, split
  it into well-named helpers.
- **DRY.** Don't duplicate logic; extract it into a shared function, hook or component.
- **Match the existing style** where this skill is silent: look at the surrounding file or a similar component first.

## Formatting & whitespace

### Indentation, semicolons, `const`

- 4-space indent in every `.ts`, `.tsx`, `.css` and config file.
- Every statement ends with a semicolon, imports included.
- `const` by default, `let` only when the binding is reassigned, never `var`.

### Imports

Exactly **two** blank lines after the last import.

```ts
// flag
import { fetchFeed } from '../../../api/feed';
import './Feed.css';

const PAGE_SIZE = 20;

// prefer
import { fetchFeed } from '../../../api/feed';
import './Feed.css';


const PAGE_SIZE = 20;
```

### No crammed one-liners

- Object literals with two or more fields put one field per line.
- A multi-argument call puts one argument per line when it's crammed: the line passes ~120 characters, or any
  argument is a function, an object literal or another multi-argument call. Short calls with plain arguments stay
  inline (`fetchFeed(filter, nextCursor, PAGE_SIZE)`).
- A single-statement `if` body still gets braces, with the body on its own line.
- Blank lines separate the logical steps inside a block.

```ts
// flag
const params = new URLSearchParams({ filter, size: String(size) });
if (!response.ok) throw new FeedApiError(response.status);
const timer = window.setTimeout(() => setStatus('loading'), LOADING_INDICATOR_DELAY_MS);

// prefer
const params = new URLSearchParams({
    filter,
    size: String(size),
});

if (!response.ok) {
    throw new FeedApiError(response.status);
}

const timer = window.setTimeout(
    () => setStatus('loading'),
    LOADING_INDICATOR_DELAY_MS,
);
```

### `if`/`else`

`} else` and `} else if` stay on the closing-brace line. Leave exactly one blank line at the end of every branch body
except the last.

```ts
// flag
if (filterChanged) {
    scheduleLoadingIndicator();
}
else {
    setStatus('loading');
}

// prefer
if (filterChanged) {
    scheduleLoadingIndicator();

} else {
    setStatus('loading');
}
```

### `try`/`catch`/`finally`

Same shape: `} catch` and `} finally` stay on the closing-brace line, with one blank line at the end of every block
except the last.

```ts
// flag
try {
    const page = await fetchFeed(filter, nextCursor, PAGE_SIZE);
    appendPage(page);
} catch {
    setLoadMoreError(LOAD_MORE_ERROR);
} finally {
    setIsLoadingMore(false);
}

// prefer
try {
    const page = await fetchFeed(filter, nextCursor, PAGE_SIZE);

    appendPage(page);

} catch {
    setLoadMoreError(LOAD_MORE_ERROR);

} finally {
    setIsLoadingMore(false);
}
```

### Method chaining

- A chain of **two or more** calls puts each call on its own line, indented 4 spaces from the start of the statement.
- A single call stays inline.
- Promise methods (`.then`, `.catch`, `.finally`) always start their own line, even alone.

```ts
// flag
const names = topics.filter(isActive).map((topic) => topic.name);
logout().catch(() => {});

// prefer
const activeTopics = topics.filter(isActive);

const names = topics
    .filter(isActive)
    .map((topic) => topic.name);

logout()
    .catch(() => {});
```

## Naming

- **Names must be self-explanatory.** A reader should know what something is or does without opening it. No `r`, `v`,
  `x`, `data`, or `item` for a domain object.
- Components, interfaces and types are `PascalCase`. A component's props interface is `<Component>Props`.
- Handlers defined inside a component are `handle<Event>` (`handleRetry`). Callback props are `on<Event>`
  (`onLoadMore`).
- Booleans start with `is`, `has` or `should` (`isLoading`, `hasMore`, `isCancelled`). Fields from API payloads keep
  the backend's name (`FeedTopic.subscribed`).
- Module constants are `UPPER_SNAKE_CASE`, with the unit in the name when the type doesn't carry it
  (`LOADING_INDICATOR_DELAY_MS`, not `LOADING_INDICATOR_DELAY`).
- Magic numbers get a named constant, unless the meaning is obvious (`0`/`1` as a start or step).
- Callback parameters are named for what they hold, including state updaters.

```ts
// flag
setItems((current) => current.map((item) => (item.id === topicId ? { ...item, subscribed } : item)));
const [loading, setLoading] = useState(false);

// prefer
setTopics((currentTopics) => currentTopics.map((topic) => withSubscription(topic, topicId, subscribed)));
const [isLoading, setIsLoading] = useState(false);
```

## Comments (guideline)

- **Explain why, never what.** The code already says what. Before writing a comment, check whether a better name makes
  it unnecessary.
- **Do comment non-obvious coupling**: a TS value that must match a CSS value, or a breakpoint in a hook that must
  match a media query. These are the places someone breaks without knowing.
- CSS: one short comment above a non-obvious block (a magic offset, a stacking trick). No section banners.
- No JSDoc on components or props. The props interface is the documentation.
- **Never reference a `.md` file** in a comment. Those files change independently of the code, so the pointer goes
  stale. Restate the reason in the comment itself.

```ts
// flag
// set loading to true
setIsLoading(true);

// prefer
// Must match the 340ms transition in SuccessCurtain.css, or the swap happens mid-animation.
const CURTAIN_COVER_MS = 340;
```

## Guidelines

- **Reuse first.** Before creating a component, hook or helper, look in `src/components`, `src/hooks` and `src/utils`
  for one to reuse or extend.
- **Don't hand-roll a built-in.** When a loop, temp variable or manual copy does what one standard call does
  (`Object.groupBy`, `array.at(-1)`, `toSorted()`, `structuredClone`, `Array.from({ length })`), use the call, as long
  as the result is clearer, not just shorter.
- **Extraction thresholds** (component > 150 lines, function or effect > 30 lines, several unrelated effects) are a
  signal to consider splitting. See `references/react.md`.
- **Overflow**: containers shouldn't let their content spill out. See `references/css.md`.
- **Comments**: see **Comments** above.

---

## Review checklist

When judging existing code, check the rules in this order:

1. Formatting: 4 spaces, semicolons, two blank lines after imports, multi-field objects and crammed calls broken one
   per line, braces on every `if`.
2. `if`/`else` and `try`/`catch` blank lines; chains of 2+ calls split; promise methods on their own line.
3. Naming: `is`/`has`/`should` booleans, units in constant names, `handle`/`on`, no vague names, no magic numbers.
4. TypeScript (`references/typescript.md`): interfaces for object shapes, no inline object types, no `any`/`!`/
   `@ts-ignore`, `as` only in `src/api/`, no URL or path literals outside `ENDPOINTS`/`ROUTES`.
5. React (`references/react.md`): component anatomy order, file placement, context shape, effect cleanup, semantic
   markup, stable `key`s, tables through `DataTable`.
6. CSS (`references/css.md`): units, class naming, own classes only, no `!important`/IDs/inline styles, no raw colors,
   no per-page scale var on new pages.

Then mention (not as violations) any guideline misses: comments (why not what, coupling commented, no `.md`
references), reuse, hand-rolled built-ins, extraction thresholds, overflow.
