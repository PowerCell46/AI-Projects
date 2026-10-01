# CSS

The goal is a design that scales: text, spacing and containers grow and shrink together when a media query changes
one value.

## Files

- Each component styles itself in its co-located `<Name>.css`, imported by `<Name>.tsx`.
- `src/index.css` is the only global stylesheet: tokens, reset, root font scaling, and utilities such as `.sr-only`.
- Order within a component's file: base rules → state rules (`[data-*]`, `:hover`, `:disabled`) → media queries →
  the `prefers-reduced-motion` block last.

## Colors

No raw colors outside `src/index.css`: no hex, no `rgb()`/`rgba()`, no named colors like `white`. Components use
`var(--token)` only. A new color becomes a semantic token in `index.css` first.

## Units

px is allowed **only** for things that must never scale: borders, `outline` and `outline-offset`, and media-query
breakpoints. Everything else uses relative units.

| Case | Unit |
|---|---|
| Font sizes, spacing (margin, padding, gap), border radius, small motion offsets (`translate`) | `rem` (`em` when it should follow the element's own font size) |
| Borders, `outline`, `outline-offset` | `px` |
| Media-query breakpoints | `px` (rem breakpoints would shift with the root scaling below) |
| Widths relative to the parent | `%` |
| A readable maximum width | `rem` (`max-width: 30rem`) |
| Full-viewport layers (overlays, curtains) | `vw`, `vh`, `dvh` |
| Root font size | `%` on `html`, so the browser's font-size setting still applies; `body` is `1rem` |
| Durations | `ms` |

```css
/* flag */
.pager-head {
    width: 10px;
    border-radius: 4px;
    transform: rotate(45deg) translate(4px, 4px);
}

/* prefer */
.pager-head {
    width: 0.625rem;
    border-radius: 0.25rem;
    border-right: 1.5px solid var(--line-btn);
    transform: rotate(45deg) translate(0.25rem, 0.25rem);
}
```

## Scaling

- Scaling happens in **one place**: root font-size bands in `src/index.css`. Components write plain `rem`, and every
  size follows the root automatically.
- New pages and components never define their own scale variable, and never wrap sizes in
  `calc(var(--<page>-scale) * ...)`.
- `HomePage` and `AuthPage` still use `--home-scale`/`--auth-scale` until they're refactored onto root scaling. When
  editing inside those pages, keep their existing `calc()` pattern so nothing inside them stops scaling.
- Component media queries go at the bottom of the component's own `.css`. Reuse an existing breakpoint value before
  adding a new one.

## Class naming

- Classes are `<component-in-kebab>-<part>`, prefixed with the component's own name: `.topic-card`,
  `.topic-card-title`, `.pager-stem`. (`AuthForm` and `Panel` use an older `auth-*` prefix; leave them.)
- Visual state goes on `data-*` or `aria-*` attributes, selected in CSS. No BEM `--modifiers`, no state classes, no
  class names built with template strings.

```css
/* flag */
.topic-card--subscribed { ... }
.topic-card.is-subscribed { ... }

/* prefer */
.topic-card[data-subscribed='true'] { ... }
.feed-filter-cell[aria-selected='true'] { ... }
```

## Scope

- A component's `.css` styles only its own classes. It never reaches into a child's (`.feed .topic-card-title`).
- No `!important`, except the existing `prefers-reduced-motion` reset in `index.css`.
- No inline `style={{}}`, except for values computed at runtime (a progress width, a measured height).
- No ID selectors.

## Overflow (guideline)

A container shouldn't let its content spill out. Techniques that usually get there:

- Flex and grid children that hold text get `min-width: 0`, so a long word can't push the column wider.
- User or API text (emails, names, descriptions, URLs) gets `overflow-wrap: anywhere`.
- A label that must stay on one line uses `overflow: hidden`, `text-overflow: ellipsis` and `white-space: nowrap`,
  with the full text in a `title` attribute.
- `img`, `svg` and `video` get `max-width: 100%`.
- Content that's legitimately wider than its container (tables, code) scrolls inside its own `overflow-x: auto`
  wrapper. The page itself doesn't scroll sideways.
- Don't put `overflow: hidden` on a layout container just to hide a scrollbar. Find and fix what's too wide.
- Check new or changed layouts at 320px wide, the smallest root band.
