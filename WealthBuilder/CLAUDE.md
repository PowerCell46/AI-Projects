# Project Conventions

Spring Boot backend + React TypeScript frontend. These are **strong preferences** — push back when you have reason, don't follow them mechanically.

## Stack
- **Backend**: Java 25, Spring Boot, Maven
- **Frontend**: React + TypeScript LSV, npm

---

## Running the project

### Backend

`JAVA_HOME` must point at Java 25:

```powershell
# PowerShell
$env:JAVA_HOME = "C:\Users\HP ZBook 17 G5\.jdks\openjdk-25"
mvn spring-boot:run
```

```cmd
:: cmd
set JAVA_HOME=C:\Users\HP ZBook 17 G5\.jdks\openjdk-25
mvn spring-boot:run
```

Other Maven goals: `mvn clean install`, `mvn test`, `mvn verify`.

### Frontend

```bash
npm install
npm run dev        # dev server
npm run build      # production build
npm test           # tests
```

> If the actual script names in `package.json` differ, use those instead.

## Code style (applies everywhere)

1. Write clean, maintainable, well-ordered (horizontally and vertically), spaced-out code, following Uncle Bob's principles.
2. Don't write long methods/functions — split them up.
3. Leave **two** blank lines, instead of one, between the last import and the first actual line of code, for clearer separation.
4. If a method contains complex logic, write a short and concise doc comment (e.g. JavaDoc, JSDoc).
5. When chaining, put each call on a new line so it's easier to read.
6. Take effort when naming variables, classes, interfaces, etc. The name should be cognitive — **readability is the end goal.**
7. Don't forget to initialize and update .gitignore files, so only worhy files are commited to Git.

---

## Backend (Spring Boot)

### Architecture

Layered: `controller → service → repository`. Controllers don't touch repositories directly. Services own business logic; controllers stay thin (validate input, delegate, shape response).

**Package structure — layer-first, NOT feature-first.** Group by technical layer at the root package (e.g. `com.wealthbuilder.backend`), never by feature/domain. Do not create per-entity packages (e.g. no `users/` containing its own `config`, `entities`, `repositories`).

Root-level packages:
- `/config` — Spring configuration
- `/controllers`
- `/entities`
- `/repositories`
- `/services`
  - `/interfaces` — service interfaces
  - `/implementations` — service implementations
- `/dtos`
- `/utils`
- `/exceptions`

All classes for a given layer live under that layer's package regardless of which domain they belong to (e.g. `UserService` and `OrderService` both go in `/services/interfaces` + `/services/implementations`).

### Dependency injection

- **Constructor injection only.** Declare dependencies as `private final` fields and put `@RequiredArgsConstructor` on the class.
- **No `@Autowired` on fields. Ever.**
- **No setter injection.**

### Lombok

Used freely: `@RequiredArgsConstructor`, `@Data`, `@Builder`, `@Slf4j`, `@Value`, `@Getter`/`@Setter`, etc. Pick the narrowest annotation that does the job — prefer `@Getter` + `@RequiredArgsConstructor` over `@Data` on JPA entities.

### Transactions

Apply `@Transactional` on service methods where they're actually needed — multi-step writes, read-modify-write flows, anything requiring atomicity. Don't blanket-annotate every public service method.

- Do not use records anywhere. Use normal Java classes with Lombok annotations instead. This is a hard style preference, not a suggestion.
- Empty lines between methods should be EXACTLY 1.

---

## Frontend (React + TypeScript)

### Folder organization

By type:

```
src/
  components/   reusable UI
  hooks/        custom hooks
  services/     API clients, business logic
```

### Components

- **Functional components only.** No class components.
- **Named exports only** — `export const Foo = ...`. No `export default`.

### TypeScript

- **No `any`.** Use `unknown` + narrowing, or define the type properly. If something legitimately can't be typed, justify it in a comment.
- Prefer inference for locals; be explicit on function signatures and exported APIs.

---

## Working with me

- **Push back when you have reason.** These are strong preferences, not commandments. If a rule would make the code worse in a specific case, say so and explain.
- **No filler.** Skip "Great question!", "Here's a summary:", "I hope this helps." Get to the point.
- **No silent assumptions.** If a request is ambiguous, ask one focused question before guessing.
- **Auto-run tests, lint, and builds.** Run lint frequently, run builds after meaningful changes, and run tests for serious changes or any changes to tests.