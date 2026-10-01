# React

## Component anatomy

Every component file follows this order:

```tsx
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './Pager.css';


const RETRY_DELAY_MS = 500;                              // 1. module constants

function remainingCount(total: number, loaded: number): number {   // 2. pure helpers (no state, no props)
    return Math.max(0, total - loaded);
}

interface PagerProps {                                   // 3. props interface, right above the component
    loaded: number;
    total: number;
    onLoadMore: () => void;
}

function Pager({ loaded, total, onLoadMore }: PagerProps) {        // 4. `function` declaration, props destructured
    const navigate = useNavigate();                      // 5. hooks: router/context → useState → useRef
    const [isLoading, setIsLoading] = useState(false);
    const timeoutIdsRef = useRef<number[]>([]);

    useEffect(() => { ... }, [...]);                     // 6. effects

    function handleLoadMore() { ... }                    // 7. handlers

    const nextCount = remainingCount(total, loaded);     // 8. derived values, right before render

    if (total === 0) {                                   // 9. early returns
        return null;
    }

    return ( ... );                                      // 10. JSX
}

export default Pager;                                    // 11. default export at the bottom
```

- Pure helpers live at module level, above the component, so they aren't recreated on every render.
- No arrow-function components (`const Pager = () => ...`).
- One component per file.

## Where files go

- Every component has its own folder: `<Name>/<Name>.tsx` + `<Name>/<Name>.css`.
- A child component nests inside its only consumer's folder (`HomePage/Feed/Pager/`).
- A component used by two or more parents moves up to their nearest common folder, or to
  `src/components/shared/<Name>/` when it's used across pages.
- A hook with one consumer sits next to it (`Feed/useFeedPages.ts`). With two or more consumers it moves to
  `src/hooks/`.
- A pure helper with two or more consumers goes to `src/utils/<topic>.ts`.
- Create `shared/`, `hooks/` and `utils/` only when the first file needs them.

## Extraction (guideline)

- **Custom hook** when a component's state plus effect(s) form one nameable concern. For example, `Feed`'s fetching
  becomes `useFeedPages(filter)` returning `{ topics, counts, status, loadMore }`, and the component keeps rendering and
  event handling.
- **Child component** when a JSX block has its own state, repeats, or the parent's JSX runs past one screen.
- **Signals to consider splitting:** component file > 150 lines, function or effect body > 30 lines, more than one
  `useEffect` doing unrelated things. These prompt a decision; they don't force a split of a cohesive component.

## State and context

- Local `useState` by default. Lift state to the nearest common parent when siblings need it.
- Context only when a value would pass through two or more components that don't use it, or is truly app-wide (the
  auth user, for example).
- Server data (feed pages, lists) never goes into context. It belongs to the component or hook that fetches it.
- Each context is one file in `src/contexts/`, exporting a provider and a `use<Name>()` hook. The context defaults to
  `null` and the hook throws outside its provider. Components call the hook, never `useContext` directly.

```tsx
// src/contexts/AuthContext.tsx
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: AuthProviderProps) { ... }

export function useAuth(): AuthContextValue {
    const context = useContext(AuthContext);

    if (!context) {
        throw new Error('useAuth must be used inside AuthProvider.');
    }

    return context;
}
```

## Effects

An effect that starts async work, a timer or a listener cleans it up. Async results are dropped after unmount with an
`isCancelled` flag.

```tsx
useEffect(() => {
    let isCancelled = false;

    me()
        .then((authUser) => {
            if (isCancelled) {
                return;
            }

            setUser(authUser);
        });

    return () => {
        isCancelled = true;
    };
}, []);
```

## JSX markup

- **Semantic elements first:** `<button>` for actions, `<Link>`/`<a>` for navigation, `<header>`, `<main>`, `<nav>`,
  `<section>`, `<article>`, and `<ul>`/`<li>` for lists. Never `<div onClick>`.
- Every `<button>` has an explicit `type` (`"button"` unless it submits a form).
- One `<h1>` per page; heading levels never skip.
- Every `<input>` has a `<label>`. A placeholder is not a label.
- No wrapper `<div>` that exists only to hold one child. Use `<>` fragments to group without a DOM node.
- `key` is a stable id from the data, never the array index.
- Visual state goes on `data-*` or `aria-*` attributes, never on conditional class names (see `css.md`).

```tsx
// flag
<div className={`topic-card ${topic.subscribed ? 'topic-card-subscribed' : ''}`} onClick={handleToggle}>

// prefer
<article className="topic-card" data-subscribed={topic.subscribed}>
    <button type="button" className="topic-card-button" onClick={handleToggle}>
```

## Loading states

- Tables show a skeleton (see **Tables**).
- Card lists and other non-table views show a text indicator ("Loading…"). No hand-built skeletons for them.

## Tables

- Always `@tanstack/react-table`, rendered with real `<table>`, `<thead>`, `<tbody>` and `<th scope="col">` markup,
  never `div` grids. Add the dependency when the first table is built.
- Every table goes through one shared `DataTable` component in `src/components/shared/DataTable/`, built with the first
  table. It wraps TanStack plus the skeleton plus the pager. Pages supply only `columns` and `data`.
- **Every table is pageable.** When the endpoint pages, use `manualPagination` and fetch per page. Only fully
  client-side data uses TanStack's built-in pagination.
- **Skeleton on first load:** shimmering placeholder rows in the real column layout, with the header row visible and
  the row count equal to the page size, so nothing jumps when data arrives. The shimmer stops under
  `prefers-reduced-motion`.
- **Page changes** keep the current rows visible (dimmed) until the new page arrives. No skeleton.
- A table wider than its container scrolls horizontally inside its own wrapper. The page never scrolls sideways.
