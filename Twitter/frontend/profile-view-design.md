# Implementation Brief — Profile View

Build the user's own profile page, reached from EDIT ACCOUNT in the avatar dropdown. The app header, post cell, compose modal and dropdown already exist — **reuse them unchanged.**

---

## 1. Rationale

### One page, not two
This is the **public profile with an edit affordance**, not a settings screen. It is the same page another user would see, minus the EDIT button. One layout, one route, one component — a separate "account settings" form would mean maintaining two renderings of the same data and would make the profile feel like a form rather than a page about a person.

### Read view, then an edit sheet
Fields are read-only text by default. EDIT opens a full-bleed sheet containing the form.

Alternatives rejected:
- **Always-editable fields** — a page of input boxes reproduces the "large empty rectangle" problem already fixed on the tweet detail page, and it is never clear when anything was actually saved.
- **Inline per-field editing** — elegant, but it means independent save calls per field, per-field error states, and per-field pending UI. Disproportionate for three editable values.
- **In-place form swap** — workable, but the page would jump between two heights and the save/cancel controls would sit in a different place each time.

The sheet is the same construction as compose and as the auth steps: mono eyebrow, headline, fields over hairline rules that fill on focus. Editing your profile becomes the same *kind* of event as posting. It is the third use of this pattern, which is the point — it is now the app's way of saying "this is a deliberate action."

### Three editable fields only
Email and username are **not editable**. Email realistically requires a verification flow; username is identity, unique-constrained, and needs availability checking and rate limiting. Both are shown in the edit sheet with a `LOCKED` tag rather than hidden — a user who opens the sheet looking for them should find them and understand immediately why they are inert, instead of concluding the app lost their email.

That leaves **profile picture, bio, location**. Location is a free-form text field in the GitHub style (`Sofia, Bulgaria`) — no country dropdown. A ~195-option native `<select>` would be the single most foreign-looking element in the product, and a custom listbox is disproportionate work for a field nobody filters on.

### Posts live on this page
Posts appear below the profile under a section header rather than behind a separate route. The alternative — a bounded "View all tweets" link out to its own view — keeps the profile a pure record, but costs a route and a click to see the thing people actually came for.

---

## 2. Tokens

All already in the app. Nothing new is introduced.

```
--bg      #050E22   page, sheet background
--cell    #07142C   post cell surface only
--glow    #4E8BFF   accent — EDIT/SAVE buttons, focus rules, sheet eyebrow, CHANGE PHOTO
--pale    #E2E9F7   text base
--alarm   #FF7A5C   over-limit character count only
--e       cubic-bezier(.16,.84,.26,1)
--s       cubic-bezier(.34,.02,.18,1)

rgba(226,233,247,.88)  bio text
rgba(226,233,247,.50)  locked field values, brand wordmark
rgba(226,233,247,.40)  handle, metadata, stat labels, counter, CANCEL
rgba(226,233,247,.28)  LOCKED tag text
rgba(226,233,247,.24)  avatar borders
rgba(226,233,247,.22)  input placeholders
rgba(226,233,247,.18)  field rules at rest
rgba(226,233,247,.16)  LOCKED tag border, post cell border
rgba(226,233,247,.12)  stats divider, section header line
rgba(226,233,247,.10)  app header bottom border
rgba(78,139,255,.55)   ghost button border at rest
rgba(78,139,255,.18)   avatar tint fill
```

Typefaces: IBM Plex Mono and Inter Tight. **No third family.**

---

## 3. Read View

Column `max-width: 660px`, `padding: 34px 24px 140px`. The extra top padding over the feed's `20px` keeps the masthead off the header rule.

### Masthead
Flex row, `22px` gap, items aligned to the top.

| Element | Spec |
|---|---|
| Avatar | `84px` square, `1px` border `rgba(226,233,247,.24)`, tinted fill, mono initials `24px` |
| Display name | Inter Tight 500, `25px`, `-.028em`, `#fff` |
| Handle | Mono `9.5px`, `.1em`, `7px` below the name |
| Bio | Inter Tight 400, `16.5px`, line-height `1.5`, `rgba(226,233,247,.88)`, `max-width: 46ch`, `15px` above |
| Metadata | Mono `9.5px`, `.14em`, `22px` apart — location and join date, uppercased. `15px` above |
| EDIT button | Ghost button, pushed right, aligned to the top of the row |

**Email is not shown in the read view.** It is private and belongs only in the edit sheet.

Empty optional fields are **omitted entirely**, not shown as placeholders. A profile with no bio and no location shows avatar, name, handle and join date — nothing else. Do not render "No bio yet" or an empty metadata row.

### Stats
`20px` below the masthead, separated by a `1px` top rule with `20px` padding. Three items, `28px` apart: followers, following, tweets. Mono `9.5px` `.14em` labels with the number in `#fff`, `tabular-nums`, `7px` before the label.

### Section header and posts
```
TWEETS · 342   ──────────────────────────────
```
Mono `9.5px`, `.2em`, then a `1px` rule filling the remaining width. `30px` above, `18px` below.

Below it, the existing feed post cell, unchanged, with the standard `22px` gaps. Same infinite-scroll pagination as the feed. Empty state: `NO TWEETS YET` in the end-marker style.

---

## 4. Edit Sheet

Full-bleed over the page on `--bg`, scrollable, `max-width: 620px` inner column, `padding: 60px 24px 80px` (`40px 18px 70px` ≤620px).

Enter: opacity `340ms`, `translateY(28px) → 0` over `560ms` on `--s`. Autofocus the bio textarea ~80ms after open. `Escape` closes.

Contents in order:

1. **Eyebrow** — `EDIT PROFILE`, mono `10px`, `.2em`, `--glow`, `20px` below
2. **Headline** — `clamp(23px, 4vw, 34px)`, Inter Tight 400, `-.03em`, line-height `1.05`, `36px` below
3. **Photo row** — `70px` avatar, then `CHANGE PHOTO` in mono `10px` `--glow`, with `JPG OR PNG · MAX 2 MB` beneath in the counter style. `34px` below
4. **EMAIL** — label with `LOCKED` tag, value rendered as static text in `rgba(226,233,247,.5)`. No input, no rule
5. **USERNAME** — same treatment
6. **BIO** — textarea, `66px` tall, with a `160` character counter right-aligned beneath
7. **LOCATION** — single-line input, placeholder `City, Country`
8. **Controls** — `SAVE CHANGES` (ghost button) and `CANCEL ESC` (plain mono text), `26px` apart, `38px` above

### Field construction
Editable fields have **no border box**. Label above in mono `9.5px` `.16em`; input at `17.5px` in `#fff` with `5px` bottom padding; a `1px` rule beneath in `rgba(226,233,247,.18)`.

On focus the rule fills `scaleX(0) → scaleX(1)` from the left over `720ms` on `--e` — **identical to auth, compose and the reply composer.** Do not add a border, background, or outline to any input on this page.

### LOCKED tag
Mono `8.5px`, `.14em`, `rgba(226,233,247,.28)`, `1px` border `rgba(226,233,247,.16)`, `2px 7px` padding, zero radius. Sits inline in the label row, `12px` after the label text. It needs accessible text explaining why — e.g. "Email cannot be changed here" — not just the visual tag.

### Counter
Bio counter is mono `9px`, `.12em`, `tabular-nums`, right-aligned, `9px` below the rule. Turns `--alarm` past 160. SAVE is disabled while over the limit.

---

## 5. Behaviour

- **Dirty tracking.** SAVE submits only changed fields. If nothing changed, close without a request.
- **Unsaved changes.** CANCEL or Escape with a dirty form must confirm before discarding. Do not silently throw away edits.
- **Saving.** Disable SAVE and show a pending state while in flight. On success, close the sheet and update the masthead in place — do not reload the page. On failure, keep the sheet open, keep the user's input, and show the error near the offending field in the `--alarm` colour.
- **Photo upload.** `CHANGE PHOTO` opens a file picker. Validate type and the 2 MB limit client-side and reject with a message in the same style. Show the new image immediately as a local preview before the upload resolves. Cropping is **not** in scope — square-crop centred, and document that.
- **Bio whitespace.** Trim before saving and before counting. A bio of only spaces is empty.
- **Location** is free text, max 60 characters, trimmed. No validation against any place list.
- Focus is trapped in the sheet while open, `role="dialog"` + `aria-modal="true"`, focus returns to the EDIT button on close, background scroll locked.

### Navigation out
There is **no BACK button** on this page. The `FATHOM` wordmark in the header is the route home and hovers to `--glow`. If testing shows that is not discoverable enough, add a HOME item to the avatar dropdown rather than reintroducing a back affordance — this page is a destination, not a detail view.

---

## 6. Accessibility & Responsive

- Every input needs a real associated `<label>`; the mono key doubles as the label — associate it properly rather than relying on proximity.
- Locked fields are static text, not `disabled` inputs — disabled inputs are skipped by some screen reader modes and the value would be lost.
- The character counter is an `aria-live="polite"` region, announcing sparingly (on threshold crossings, not every keystroke).
- Stat numbers need full accessible text — "1,204 followers", not "1,204" next to an abbreviation.
- `prefers-reduced-motion: reduce` collapses transitions to `0.01ms`; the sheet appears instantly and the rule fill snaps.
- Verify `rgba(226,233,247,.40)` metadata against `--bg` meets 4.5:1; raise if not.

**≤620px:** column padding `18px`; masthead wraps (avatar above the text block); POST button loses its label; post cell uses its existing small-screen values; sheet padding `40px 18px 70px`. Respect `env(safe-area-inset-*)`.

---

## 7. Do NOT Build

- A separate account-settings page or route
- Editable email or username fields, or any inline "change email" flow
- A country dropdown, country listbox, or location autocomplete
- A cover/banner image
- Bordered or filled input boxes
- A BACK button
- Inline per-field editing or per-field save buttons
- Placeholder rows for empty optional fields
- Image cropping, rotation or filters
- Verified badges, pronouns, website field, birthday, joined-location
- A tab row on this page (tweets are the only list here)
- Rounded corners, shadows, gradients
- A new colour, typeface, icon or motion curve