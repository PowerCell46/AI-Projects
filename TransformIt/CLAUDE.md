## Project

**TransformIt** — a JavaFX desktop image-transformation app (not a Spring/web app). A user opens an
image, stacks playful transformations (grayscale, flip, color remap, ASCII), and exports a PNG or
`.txt`. See `PROJECT.md` for the full architecture and decisions.

---

## Running the project

`JAVA_HOME` must point at Java 25:

```powershell
# PowerShell
$env:JAVA_HOME = "C:\Users\HP ZBook 17 G5\.jdks\openjdk-25"
mvn clean javafx:run
```

```cmd
:: cmd
set JAVA_HOME=C:\Users\HP ZBook 17 G5\.jdks\openjdk-25
mvn clean javafx:run
```

Run in dev:

```powershell
mvn clean javafx:run
```

---

## Code style (applies everywhere)

1. Write clean, maintainable, well-ordered (horizontally and vertically), spaced-out code, following Uncle Bob's principles.
2. Don't write long methods/functions — split them up.
3. Leave **two** blank lines, instead of one, between the last import and the first actual line of code, for clearer separation.
4. If a method contains complex logic, write a short and concise doc comment (e.g. JavaDoc, JSDoc).
5. When chaining, put each call on a new line so it's easier to read.
6. Take effort when naming variables, classes, interfaces, etc. The name should be cognitive — **readability is the end goal.**

---

## Layering

`bg.transformit.core` (transforms, pipeline) must **not** import JavaFX or any UI type — it operates
only on `BufferedImage` / plain data, so the algorithms stay testable and decoupled. UI lives in
`bg.transformit.ui`, file I/O in `bg.transformit.io`.

---

## Tests

Out of scope for now (see `PROJECT.md` → "Deferred"). Don't auto-generate a test suite; the `core`
package is kept UI-free so tests can be added there later if asked.

---

### Lombok

Not used — Lombok's annotation processor crashes on Java 25 (`TypeTag.UNKNOWN` incompatibility).
Write explicit getters/setters instead.

---

## Working with me

- **Push back when you have reason.** These are strong preferences, not commandments. If a rule would make the code worse in a specific case, say so and explain.
- **No filler.** Skip "Great question!", "Here's a summary:", "I hope this helps." Get to the point.
- **No silent assumptions.** If a request is ambiguous, ask one focused question before guessing.
- **Don't auto-run tests/lint/builds.** Suggest the command if it matters; let me run it.