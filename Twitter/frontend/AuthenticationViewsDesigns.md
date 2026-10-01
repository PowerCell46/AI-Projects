Look at twitter_api_gateway (-> TESTING.md) to see the endpoints we have to know when implementing the two described views. Use the frontend-code-style when implementng. Make sure to be familiar with the email sending and confirmation


# Implementation Brief — "Hadal Descent" Auth Flow

Build the login and registration experience as a single-field-per-screen descent. This brief is self-contained; implement exactly what is described and nothing beyond it.

---

## 1. Design Rationale

### The problem
Standard auth screens are a centered card with stacked fields and a full-width button. They are instantly forgettable, and on the first screen a new user ever sees, that is a wasted impression. Registration in particular suffers: three or four fields presented at once reads as a chore before the user has any reason to care.

### The solution
Present one field at a time, full-bleed, and frame the sequence as a **descent**. A depth gauge on the left edge and a travelling depth readout respond to progress. Each step down the form is a step further underwater.

### Why it works
- **Progress becomes the theme, not a widget.** The "sea" idea is carried by behaviour and structure rather than by decoration. There is no ambient water animation, no gradient background, no wave graphic. Remove the depth metaphor and the layout stops making sense — that is the test that the concept is structural.
- **Single-field focus lowers perceived effort.** The user answers one question. Registration's extra field costs one more screen instead of visibly lengthening a form.
- **The metaphor absorbs the error state.** A rejected password pushes the diver *back up*. Failure gets a physical, legible consequence instead of a red box.
- **Login and register share one machine.** Same layout, same motion, different step arrays. Login has 2 steps, registration has 3. No divergent design work.

### Known trade-off
Multi-step forms cost more interactions than a single form, and password managers autofill single-screen forms more reliably. Mitigations are specified in §6. Accept the trade-off; do not redesign around it.

---

## 2. Design Tokens

### Colour
| Token | Value | Role |
|---|---|---|
| `--deep` | `#00081F` | Page background. The only background colour. |
| `--glow` | `#4E8BFF` | Accent. **Exactly two roles: (a) active/progress indication, (b) the primary action button fill.** Nothing else. |
| `--pale` | `#DCE6FA` | Primary text. |
| `--alarm` | `#FF7A5C` | **Error state only.** Never used for anything else, anywhere in the app. |
| `#FFFFFF` | `#FFFFFF` | Input value text only (brighter than `--pale` so typed input reads as foreground). |
| `#9CC0FF` | `#9CC0FF` | Primary button hover fill only. |

Derived alphas (use these exact values, all on `--pale` or `--glow`):

```
rgba(220,230,250,.50)  brand wordmark
rgba(220,230,250,.40)  secondary button text, footer link label
rgba(220,230,250,.34)  footer prompt text
rgba(220,230,250,.30)  "DEPTH" label
rgba(220,230,250,.28)  major tick marks
rgba(220,230,250,.26)  tick numerals
rgba(220,230,250,.20)  unfilled step bars
rgba(220,230,250,.18)  input rule (resting)
rgba(220,230,250,.17)  input placeholder
rgba(220,230,250,.12)  minor tick marks
rgba(220,230,250,.10)  ruler right border
rgba(78,139,255,.45)   footer link underline
rgba(78,139,255,.14)   horizon hairline (resting)
rgba(255,122,92,.18)   horizon hairline (error)
```

### Typography
Two families only.

- **IBM Plex Mono** — weights 400, 500. All instrument/system text: labels, tick numerals, depth readout, buttons, messages, brand.
- **Inter Tight** — weight 400. Headline questions only.

| Element | Family | Size | Tracking | Other |
|---|---|---|---|---|
| Brand wordmark | Mono | `11px` | `.44em` | |
| Step eyebrow label | Mono | `10px` | `.2em` | colour `--glow` |
| Headline question | Inter Tight | `clamp(25px, 4.8vw, 42px)` | `-.032em` | line-height `1.04` |
| Input value | Mono | `clamp(17px, 3.2vw, 25px)` | `-.01em` | |
| Depth readout number | Mono | `clamp(28px, 5vw, 44px)` | `-.02em` | `font-variant-numeric: tabular-nums` |
| Depth readout unit (`M`) | Mono | `0.34em` of number | `.2em` | vertical-align top, `9px` left margin |
| "DEPTH" label | Mono | `8.5px` | `.26em` | |
| Tick numerals | Mono | `8px` | `.1em` | tabular-nums |
| Primary button | Mono | `12.5px` | `.14em` | |
| Secondary / back button | Mono | `11px` | `.12em` | |
| Error message | Mono | `10.5px` | `.13em` | |
| Footer text | Mono | `10.5px` | `.11em` | |

`tabular-nums` on the depth readout is mandatory — proportional figures jitter horizontally during the count animation.

### Spacing & layout
```
Ruler column width      78px   (52px at ≤720px)
Content well left edge  = ruler width
Content horizontal pad  clamp(26px, 6vw, 88px)
Content max width       640px
Input max width         560px
Brand top offset        clamp(28px, 4.4vh, 44px)

Eyebrow → headline      22px
Headline → input        36px
Input text → rule       16px (as padding-bottom on the input)
Rule → error message    0 (message block animates 0 → 32px)
Message → button row    34px
Button row gap          28px (18px at ≤720px)
Footer padding          24px vertical
```

### Borders, radii, elevation
- **Border radius: `0` everywhere.** No rounded corners on any element, including the primary button.
- **No box-shadows. No gradients anywhere** (the only exception noted below does not exist — there are none).
- All rules and borders are `1px`, except: input rule `1px`, arrival rule `2px`, step bars `2px`, marker tip `2px` wide.

### Motion
| Curve | Value | Use |
|---|---|---|
| `--e` | `cubic-bezier(.16,.84,.26,1)` | Settling motion: rule fill, message reveal, button nudge, arrival rule |
| `--s` | `cubic-bezier(.34,.02,.18,1)` | Weighted travel: horizon movement, step enter/exit |

| Transition | Duration |
|---|---|
| Horizon travel (marker + hairline + readout) | `1.15s` `--s` |
| Depth number count-up | `950ms`, cubic ease-out (`1 - (1-p)³`), JS-driven |
| Step exit / enter | `600ms` `--s`, opacity `400ms`; swap fires at `400ms` |
| Input rule fill on focus | `720ms` `--e` |
| Error message height reveal | `420ms` `--e`, opacity `300ms` |
| Colour shifts (alarm state) | `500ms` |
| Button hover | `300ms` background, `500ms` `--e` transform |
| Arrival rule draw | `1.3s` `--e`, `150ms` delay |

---

## 3. Structure

Three layers over a full-viewport stage (`height: 100vh; min-height: 600px; overflow: hidden`):

**Layer 1 — the ruler.** Fixed-width column pinned to the left edge, full height, with a hairline right border. Contains 27 tick marks. Every 4th tick is major (longer, brighter, labelled with a depth numeral placed to its left, inside the column).

**Layer 2 — the horizon.** A single full-width element positioned at `top: 0` with zero height, moved by `transform: translateY()`. It carries three children that therefore always move as one unit:
- the marker — a short horizontal bar sitting in the ruler column, with a small vertical tip at its right end
- the hairline — spans from the ruler's right edge to the viewport's right edge, very faint
- the readout — anchored to the right edge, sitting *above* the hairline, containing the "DEPTH" label and the number with its unit

This single-object construction is the core of the design. The gauge, the horizon line and the numeric readout must never move independently.

**Layer 3 — content.** Brand wordmark pinned top-left of the well. The step block vertically centred in the well. The footer (account-switch link) pinned bottom-right of the well.

---

## 4. Flow Data

```
login:
  step 1 — label "IDENTITY", question "Who are you out here?",
           placeholder "email or username", type text,     depth 140
  step 2 — label "KEY",      question "And the password.",
           placeholder "password",          type password, depth 3860
  seafloor depth: 4900

register:
  step 1 — label "IDENTITY", question "Where do we reach you?",
           placeholder "email address",     type email,    depth 140
  step 2 — label "HANDLE",   question "Pick the name you'll be known by.",
           placeholder "username",          type text,     depth 3860
  step 3 — label "KEY",      question "Now seal it.",
           placeholder "password",          type password, depth 9720
  seafloor depth: 10910
```

Login accepts either email or username in a single identifier field. Do not split it.

### Depth-to-position mapping
Tick `i` of `N` (N = 26) sits at:
```
t = i / N
y = 6 + 86 * t^0.78          // percentage of stage height
label = round(11000 * t^1.55 / 50) * 50
```
The horizon's position for a given depth inverts that curve:
```
t = (depth / seafloorDepth)^(1 / 1.55)
y = 6 + 86 * t^0.78
```

**Fix required during implementation:** the prototype positions ticks in `%` but the horizon in `vh`. These only agree when the stage is exactly `100vh`. Position the horizon using the same percentage basis as the ticks (percentage of the stage element's height), so the marker stays aligned with the scale in embedded or short-viewport contexts.

The exponents produce a scale that compresses with depth, like a real sounding chart. The marker's travel between steps is deliberately non-uniform — do not linearise it.

---

## 5. Behaviour

### Advancing
- **Enter** is the primary path. The primary button is the secondary path. Both do the same thing.
- On advance: the current step translates **up** 38px and fades out. At `400ms` the content swaps. The new step is positioned 38px **down**, then released to rest. Total perceived duration ~1s.
- Going back inverts the direction: current step exits downward, new step enters from above.
- The horizon begins travelling at the same moment the swap occurs, over `1.15s` — deliberately slower than the content, so the gauge is still settling after the text has landed. This lag is what gives the descent weight. Do not shorten it to match.
- Autofocus the new field ~60ms after the swap.
- Step bars in the eyebrow fill cumulatively (steps 1..current filled).

### Focus
Focusing an input scales its accent rule from `scaleX(0)` to `scaleX(1)`, left origin, `720ms`. Blur reverses it. Nothing else responds to focus.

### Error
Two cases:

1. **Empty field** — message "NO VALUE ENTERED — HOLDING AT DEPTH". The input rule turns `--alarm` and stays filled. The horizon does **not** move.
2. **Rejected credentials** — message "KEY REJECTED — RISING 420 M". The input rule turns `--alarm`, and the horizon **travels back up** by 420m, with the readout counting *down* to the new value. Marker, hairline and readout all shift to `--alarm` colouring for the duration of the error.

The message block animates its height from `0` to `32px` and is preceded by a small solid `6px` square in `--alarm`. Any keystroke in the field clears the error: colours return, message collapses, and (for case 2) the horizon returns to the step's true depth.

Substitute the real server message for case 2's copy where the API provides one, but keep the format: uppercase, mono, tracked, prefixed by the square.

### Account switching
The footer link swaps the entire flow between login and register: step index resets to 0, the horizon returns to 140m, the step block animates as a forward transition.

### Arrival
On successful final submission the step block is replaced by an arrival state:
- eyebrow — "IDENTITY CONFIRMED" (login) / "ACCOUNT ESTABLISHED" (register)
- headline — "Seafloor reached.<br>Welcome back down." (login) / "You're on the chart now." (register)
- a `2px` `--glow` rule that draws left-to-right across `560px` over `1.3s`
- the horizon drops to the seafloor depth; the readout counts to its final value
- the footer fades out

In production this state holds while the session is established, then routes to the feed. The rule draw doubles as the loading indicator — do not add a spinner.

---

## 6. Accessibility & Practical Requirements

- Each step's input needs a real associated `<label>`; the headline question is not a label. Visually hide the label if it duplicates the eyebrow.
- The whole flow is one `<form>`; each step submits it. Password managers need `autocomplete="username"` / `"current-password"` / `"new-password"` on the correct fields, and the identifier field must persist in the DOM across steps (hidden, not unmounted) so managers can fill both username and password.
- Announce step changes to screen readers via a polite live region: "Step 2 of 3, Key."
- The error message region is `role="alert"`.
- Depth values, tick numerals and the gauge are decorative. Mark them `aria-hidden="true"`. Real progress is announced through the live region.
- `prefers-reduced-motion: reduce` collapses all transitions and animations to `0.01ms`. The layout, the depth readout and the error states all still work statically — the marker jumps rather than travels.
- Minimum contrast: the `--pale` on `--deep` pairing is comfortable; verify the `.34`–`.40` alpha text meets 4.5:1 and raise the alpha if it does not. Do not lower any alpha below the values listed in §2.
- Browser back button should step backward through the flow, not exit it.

### Responsive (≤720px)
Ruler narrows to `52px`; the content well and footer shift with it. Marker shortens to `36px` and moves to `16px` from the left. Tick numerals drop to `7.5px` and sit `24px` from the ruler's right edge. Readout sits `11px` above the hairline. Button row gap tightens to `18px`. Everything else is already fluid via `clamp()`. Respect `env(safe-area-inset-*)` on the root element. On iOS, confirm that the on-screen keyboard does not push the vertically-centred step block off screen — if it does, anchor the step block to a fixed offset from the top rather than centring, only while the keyboard is open.

---

## 7. Do NOT Build

Explicitly out of scope. Do not add these even if they seem like improvements:

- Password strength meters, requirement checklists, or show/hide password toggles
- Social / OAuth buttons
- "Remember me" checkboxes
- Terms-and-conditions checkboxes or marketing consent
- Forgot-password flow (link only, if required — no screens)
- Email verification screens
- Any water, wave, bubble, caustic or particle animation
- An ambient or looping background animation of any kind
- Gradients, box-shadows, glows, blurs, border-radius
- Icon libraries — the marker tip, the error square and the step bars are all CSS-drawn boxes
- A progress bar. The gauge *is* the progress indicator
- Spinners or skeleton loaders. The arrival rule draw covers the wait
- Any use of `--glow` beyond its two assigned roles, or `--alarm` beyond errors
- Zebra striping, pill badges, or uppercase styling applied via `text-transform` to content strings (the labels are authored uppercase deliberately; do not transform arbitrary text)
- A third typeface
- Desktop/mobile layout divergence beyond the values in §6