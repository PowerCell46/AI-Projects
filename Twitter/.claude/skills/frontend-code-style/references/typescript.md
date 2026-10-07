# TypeScript

The point of every rule here: let the types carry the meaning, so you and the agent can navigate the code by them.

## Strictness

- Write code that compiles under `strict: true`, with `tsc -b` clean.
- No `any`, explicit or implicit. When a value's type is genuinely unknown, type it `unknown` and narrow it.
- No `// @ts-ignore` or `// @ts-expect-error`. If the types don't line up, fix the types.

## `interface` vs `type`

- `interface` for every **object shape**: props, API payloads, state objects, context values.
- `type` only for what an interface can't express: unions, literal sets, function types
  (`type UnauthorizedHandler = () => void`) and aliases built from utility types (`Record<...>`, `Pick<...>`).
- **No inline object types**, whether in generics, parameters or return types. Every object shape gets a named
  interface.

```ts
// flag
type FeedCounts = {
    all: number;
    subscribed: number;
};

const [curtainGreeting, setCurtainGreeting] = useState<{ heading: string; sub: string } | null>(null);

// prefer
interface FeedCounts {
    all: number;
    subscribed: number;
}

interface CurtainGreeting {
    heading: string;
    sub: string;
}

type FeedStatus = 'loading' | 'ready' | 'error';

const [curtainGreeting, setCurtainGreeting] = useState<CurtainGreeting | null>(null);
```

## Assertions and non-null

- `as` is allowed **only in `src/api/`**, to type a parsed response body (`response.json()` returns `any`, so the
  boundary has to cast). Nowhere else.
- `as const` is fine everywhere. It narrows the type rather than overriding it.
- Non-null `!` is banned. Narrow with a check, `?.` or `??` instead.

```ts
// flag
const email = user!.email;
const button = event.target as HTMLButtonElement;

// prefer
if (!user) {
    return;
}

const email = user.email;

// typed by the handler's MouseEvent<HTMLButtonElement> parameter
const button = event.currentTarget;
```

## URLs and paths

No string literal that is a URL or an app path appears outside these two files.

**API URLs** live in `src/api/endpoints.ts`. Nothing else reads `import.meta.env.VITE_BASE_API_URL`. Endpoints that
take a parameter are functions, and they encode every path parameter with `segment()`, so an id like `../users`
can't change the path.

```ts
const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? '';

const API_V1 = `${BASE_URL}/api/v1`;

function segment(value: string): string {
    return encodeURIComponent(value);
}

export const ENDPOINTS = {
    feed: `${API_V1}/feed`,
    subscriptions: `${API_V1}/subscriptions`,
    subscription: (interestTopicId: string) => `${API_V1}/subscriptions/${segment(interestTopicId)}`,
    auth: {
        login: `${API_V1}/auth/login`,
        register: `${API_V1}/auth/register`,
        logout: `${API_V1}/auth/logout`,
        me: `${API_V1}/auth/me`,
    },
} as const;
```

**App routes** live in `src/routes.ts`, and both `<Route path>` and `navigate()` use them.

```ts
export const ROUTES = {
    home: '/',
    login: '/login',
    register: '/register',
} as const;
```

```tsx
// flag
const response = await fetch(`${BASE_URL}/api/v1/feed?${params.toString()}`);
navigate('/login');

// prefer
const response = await fetch(`${ENDPOINTS.feed}?${params.toString()}`);
navigate(ROUTES.login);
```

## API modules

One module per resource in `src/api/`. Each one:

- exports the request/response interfaces for its resource
- sends through one shared request helper, which throws one shared `ApiError` (with the HTTP `status`) on a non-OK
  response, so callers catch every API failure the same way
- returns typed promises, so callers never see `any`

## Module layout

A `.ts` module that isn't a component follows this order: constants → types and interfaces → module state (`let`) →
classes → functions. This matches the component anatomy in `react.md`. Declare a function before the code that calls
it, so the file reads from the building blocks up. Exported and private functions mix freely within that order.

## Exports

Components are default exports, except a context's provider, which is a named export next to its `use<Name>()`
hook. Everything else (types, constants, functions, hooks) uses named exports.
