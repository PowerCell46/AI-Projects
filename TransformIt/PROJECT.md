# TransformIt — Project Plan

## Context

TransformIt is a cross-platform **desktop image-transformation playground** (per `context.md`). The
goal is *creative exploration*, not precise photo editing: a user opens an image, stacks playful
transformations (grayscale, flip/mirror, color remapping, ASCII art), tweaks their parameters, and
exports the result as a PNG or `.txt`. It must feel smooth, never freeze on heavy work, and always
display images at the correct aspect ratio.

This plan exists to lock the architecture **before** any code is written. The developer is
experienced in the **Java/Spring ecosystem but new to JavaFX/desktop development**, so the design
favors familiar tools (Java, Maven), clear module boundaries, and heavy inline documentation, while
isolating the one genuinely new layer (JavaFX UI) behind a small, well-defined surface.

> Note: `context.md` line 63 ("after a successful login, redirect to profile page") is a copy-paste
> artifact from an unrelated spec. TransformIt is a **local, offline desktop app with no accounts or
> login** — that line is intentionally ignored.

## Decisions (locked with the user)

| Area | Decision |
|------|----------|
| Stack | **JavaFX 25** UI, **Java 25 LTS**, **Maven** build, **jpackage** for native installers |
| Processing representation | `java.awt.image.BufferedImage` (full pixel access + free `ImageIO` PNG export); converted to JavaFX `Image` for display via `SwingFXUtils.toFXImage` |
| Transform model | **Non-destructive pipeline**: ordered list of `Transform` instances; always re-run from the original image when any param/order/toggle changes |
| Responsiveness | Heavy renders run on a JavaFX `Task` (background thread) with a progress overlay; slider changes are **debounced** before triggering a re-render |
| Transforms shipped | Grayscale, Flip (H/V), **Color Remap** (two modes), **ASCII** (terminal), **Brightness/Contrast** |
| Color Remap | Mode A = auto frequency-swap (median-cut quantize → rank by frequency → map most↔least frequent); Mode B = palette map (nearest color to a chosen preset) |
| ASCII | **Terminal** step (runs last). Produces BOTH a char grid (→ `.txt`, monospace view) AND a rendered `BufferedImage` (→ `.png`, mono or per-block colored). Monospace **aspect correction** applied so shapes aren't vertically squashed |
| Preview UX | Center workspace with a **draggable split slider** (original \| result), fit-to-workspace preserving aspect (letterboxed). Falls back to a **Before/After toggle** when ASCII (text) is the active result |
| Layout | Top toolbar (Open / Export) · left panel (transform palette + active pipeline list with per-transform params) · center workspace |
| Export | PNG via `ImageIO` at **original resolution** (never the scaled preview); ASCII `.txt` (raw grid); colored ASCII PNG |
| Tests | **Out of scope for now.** Pure algorithm functions are structured to be easily unit-testable later (see note at end) |
| Extras | Bundle 1–2 sample images for instant first-run demo |

## Architecture

```
                         ┌──────────────────────────────────────┐
   ui (JavaFX)           │  MainView (toolbar, panel, workspace) │
   - the only "new" tier │  PipelinePanel · ParamControls         │
                         │  WorkspaceView (split slider/toggle)   │
                         └───────────────┬──────────────────────┘
                                         │ calls (on a Task)
                         ┌───────────────▼──────────────────────┐
   core (plain Java,     │  TransformPipeline                     │
   no JavaFX imports)    │   apply(original) -> result            │
                         │  Transform (interface)                 │
                         │   ├─ GrayscaleTransform                │
                         │   ├─ FlipTransform                     │
                         │   ├─ BrightnessContrastTransform       │
                         │   ├─ ColorRemapTransform (AUTO|PALETTE)│
                         │   └─ AsciiTransform (terminal)         │
                         └───────────────┬──────────────────────┘
                         ┌───────────────▼──────────────────────┐
   io                    │  ImageLoader · PngExporter · TxtExporter│
                         └──────────────────────────────────────┘
```

**Key rule:** the `core` package imports **no JavaFX and no UI types** — it operates purely on
`BufferedImage` / data objects. This keeps the transform algorithms testable and lets the UI stay a
thin shell. (Mirrors the Spring habit of keeping service/domain logic free of web-layer types.)

### The pipeline

```java
public interface Transform {
    /** Pure: returns a new image, never mutates src. */
    BufferedImage apply(BufferedImage src);
    String displayName();
}
```

`TransformPipeline` holds an ordered, mutable `List<Transform>` plus an enabled flag per entry. Its
`apply(BufferedImage original)` folds the list left-to-right, **always starting from the untouched
original**. Any UI change (param edit, reorder, enable/disable, add/remove) → debounce → submit a
`Task<RenderResult>` that calls `pipeline.apply(original)` off the FX thread → on success, push the
result image (and ASCII grid, if any) to the workspace.

`AsciiTransform` is special: because it is **terminal**, the pipeline validates that nothing is
ordered after it (UI prevents it). Its output is a small `AsciiResult { char[][] grid; BufferedImage
rendered; }` carried alongside the image in `RenderResult` so both export paths work.

## Project layout

```
TransformIt/
├─ pom.xml                         # JavaFX + maven-jpackage; mainClass = ...App
├─ PROJECT.md                      # this plan
├─ src/main/java/bg/transformit/
│  ├─ App.java                     # JavaFX Application entry point
│  ├─ ui/
│  │  ├─ MainView.java             # builds toolbar + panel + workspace
│  │  ├─ WorkspaceView.java        # split-slider / toggle preview, aspect-fit
│  │  ├─ PipelinePanel.java        # palette + active pipeline list
│  │  ├─ ParamControls.java        # per-transform sliders/toggles (debounced)
│  │  └─ RenderService.java        # wraps Task<RenderResult>, progress, debounce
│  ├─ core/
│  │  ├─ Transform.java
│  │  ├─ TransformPipeline.java
│  │  ├─ RenderResult.java
│  │  └─ transforms/               # the 5 transforms + AsciiResult, palettes
│  └─ io/
│     ├─ ImageLoader.java          # FileChooser + ImageIO.read
│     ├─ PngExporter.java          # ImageIO.write(result, "png", file)
│     └─ TxtExporter.java          # writes char grid
├─ src/main/resources/
│  ├─ css/transformit.css          # JavaFX styling (clean, modern, accessible)
│  └─ samples/                     # 1–2 bundled demo images
└─ ...
```

## Algorithm notes (the meaty bits)

- **Grayscale**: per-pixel luminance `0.299R + 0.587G + 0.114B`, written to R=G=B.
- **Flip**: horizontal and/or vertical mirror via index remap (no interpolation needed).
- **Brightness/Contrast**: `out = clamp((in - 128) * contrast + 128 + brightness)` per channel.
- **Color Remap — AUTO_SWAP**: median-cut quantize into `K` buckets (K = slider), build a
  per-bucket pixel-count histogram, sort buckets by frequency, build a remap so the *i*-th most
  frequent bucket color → the *i*-th least frequent, then remap every pixel to its bucket's swapped
  color. `K` controls granularity.
- **Color Remap — PALETTE**: for each pixel, find nearest color (squared RGB distance) in the chosen
  preset palette (e.g. `SEPIA`, `GAMEBOY`, `NEON`, `MONO2`); palettes are small static arrays.
- **ASCII**: divide the (already-transformed) image into `N×N` blocks (`N` = density slider:
  1, 2, 5, 10, …). Per block, average brightness → index into a brightness-ordered ramp
  (e.g. `" .:-=+*#%@"`, with an **invert** toggle). **Aspect correction:** monospace cells are ~2×
  taller than wide, so rows are derived from columns using the font cell aspect ratio (≈0.5) to keep
  shapes proportional. Colored mode paints each char in its block's average color when rendering to
  the `BufferedImage`. Grid stored for `.txt` export; bitmap stored for `.png` export.

## Responsiveness & memory

- All `pipeline.apply` work runs inside a `Task` submitted by `RenderService`; the FX thread only
  receives the finished image. A translucent progress overlay shows during renders > a small
  threshold.
- Slider drags are **debounced** (~150 ms idle) so a single drag fires one render, not dozens.
- Memory: keep exactly two long-lived images — the **original** and the **current result**. Transforms
  allocate one working buffer at a time; intermediates are eligible for GC immediately. (Caching of
  intermediate stages is intentionally deferred — add only if a large image proves sluggish.)
- Display images are scaled to the workspace for rendering only; full-resolution buffers are used for
  export.

## Build & run (beginner-friendly)

```bash
# Run in dev:
mvn clean javafx:run

# Build a runnable jar / native installer (per OS, run on each target OS):
mvn clean package          # produces the app jar
jpackage ...               # wrapped via the maven plugin -> .msi (Win) / .dmg (mac) / .deb (linux)
```

`pom.xml` will pin Java 25, the `javafx-controls`/`javafx-swing` modules (swing for
`SwingFXUtils`), the `javafx-maven-plugin` (for `javafx:run`), and a jpackage step. Exact coordinates
are filled in during implementation.

## Verification (end-to-end)

1. `mvn clean javafx:run` launches the window.
2. **Open** a bundled sample image → it renders in the workspace at correct aspect ratio (letterboxed,
   no crop/overflow/stretch), original on both sides of the split slider.
3. Add **Grayscale** → result side updates; drag the split slider to compare.
4. Add **Color Remap**, switch AUTO ↔ PALETTE, drag the `K`/palette controls → result re-renders
   smoothly with a progress overlay on a large image, UI never frozen.
5. Add **Brightness/Contrast**, drag sliders → debounced, single render per drag.
6. Add **ASCII** (only allowed as last) → preview switches to monospace pane (toggle replaces the
   slider); change density `N` and invert → shapes stay recognizable and proportional.
7. **Export PNG** (non-ASCII result) → file opens in any viewer at original resolution.
8. **Export TXT** (ASCII active) → opens as readable ASCII art; **Export PNG** of colored ASCII → image.
9. Resize the window → workspace re-fits image without distortion.
10. Build a native installer with jpackage on the host OS → installed app launches and repeats step 2.

## Deferred / later (not in this plan)

- **JUnit 5 tests** for the pure functions in `core/transforms` (grayscale luminance, flip index
  remap, quantization + frequency-swap mapping, ASCII brightness→char mapping). The `core` package is
  deliberately JavaFX-free precisely so these are trivial to add when desired.
- Intermediate-stage render caching (only if large-image re-preview feels slow).
- Additional palettes / transforms.
