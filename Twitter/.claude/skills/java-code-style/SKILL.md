---
name: java-code-style
description: Java/Spring code style and conventions for this codebase — naming, class layout, field/annotation ordering, blank-line rules, interface-over-implementation, DRY, JavaDoc policy, DTO/package layout, Lombok, JPA, transactions, utility classes, exceptions, logging. Use before writing or editing any Java file, and when judging existing code for style.
---

# Java code style

**Precedence:** the project's `CLAUDE.md` hard rules → this skill → the surrounding code. Match the surrounding code
wherever this skill is silent; where existing code breaks a rule here, follow the rule, not the code.

Examples use `// flag` for what to avoid and `// prefer` for what to write. When writing code, never produce the
`flag` form. When reviewing, report every `flag` form you find.

---

## Principles

- **Readability is the end goal.** Write clean, easy-to-test code: small single-purpose methods, no hidden side effects,
  one level of abstraction per method.
- **Keep methods short.** When a method does more than one thing, or needs a comment to separate its steps, split it into
  well-named private methods.
- **DRY.** Don't duplicate logic. Extract shared behaviour into a common method, class or abstraction instead of
  copy-pasting it.
- **Match the existing style before writing new code.** Where this skill is silent, look at the surrounding class,
  package or a similar existing service for its conventions (method/variable naming, parameter ordering, spacing) and
  follow them. This applies to hand-written and AI-generated code alike.

## Naming

- **Names must be self-explanatory.** Take effort naming variables, methods, classes and interfaces. A reader should know
  what something is or does from its name alone, without opening the body.
- Follow the naming convention already used in the surrounding code.
- Constants are `UPPER_SNAKE_CASE` (`MAX_RETRIES`, `INITIAL_INTERVAL_MILLIS`). Put the unit in the name when the type
  doesn't carry it.
- **Magic numbers:** name them or extract them to a constant. Skip this when the meaning is obvious from context (for
  example `0`/`1` as a loop bound or counter step).

## Class layout

Members appear in this order, with no interleaving:

1. constants (`static final`)
2. other static fields
3. instance fields
4. constructors
5. static factory methods
6. public methods (including `@Override`s)
7. protected / package-private methods
8. private methods
9. nested types

Within each field group, apply **Field ordering and spacing** below.

## Formatting & whitespace

### Blank lines

- **Exactly one** blank line after a class, interface or enum opening brace, before the first member.
- **Exactly one** blank line between methods.
- No blank line before a class's closing brace.
- `try`/`catch`: see `references/try-catch.md`.
- `if`/`else`: see `references/if-else.md`.
- Enum constants: see `references/enums.md`.

```java
// flag
public class DatabaseLoader implements CommandLineRunner {
    private final String adminEmail;

// prefer
public class DatabaseLoader implements CommandLineRunner {

    private final String adminEmail;
```

### Field ordering and spacing

Applies to **every field** (constants, injected dependencies, entity columns):

- Put exactly one blank line between fields.
- Within a group (see **Class layout**), order fields by the length of the declaration line, shortest first, so they
  read as a staircase. Only the declaration line counts: a field's annotations move with it and don't affect the sort.
- Fields with the same length keep their existing relative order.

```java
// flag
private final AuthService authService;
private final String adminEmail;
private final String adminPassword;

// prefer
private final String adminEmail;

private final String adminPassword;

private final AuthService authService;
```

```java
// flag — entity columns in arbitrary order
@Column(name = "topic_name", nullable = false)
private String topicName;

@Column(name = "news_id", nullable = false)
private UUID newsId;

@Column(nullable = false)
@Enumerated(EnumType.STRING)
private NotificationOutboxStatus status;

// prefer — sorted by the declaration line; annotations travel with their field
@Column(name = "news_id", nullable = false)
private UUID newsId;

@Column(name = "topic_name", nullable = false)
private String topicName;

@Column(nullable = false)
@Enumerated(EnumType.STRING)
private NotificationOutboxStatus status;
```

### Annotation stacking

When a class, field, method or parameter has more than one annotation, order the stack by line length, shortest
first. Annotations with the same length keep their existing order. A multi-line annotation (for example `@Table(...)`
spread over several lines) goes last. Every stack you touch gets re-sorted: if you add an annotation to an existing
stack, re-sort the whole stack.

```java
// flag
@NotNull
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private NotificationOutboxStatus status;

// prefer
@NotNull
@Column(nullable = false)
@Enumerated(EnumType.STRING)
private NotificationOutboxStatus status;
```

### Method chaining

When an expression chains two or more calls, put each call on its own line, indented 8 spaces from the start of the
statement. A single call stays on one line.

```java
// flag
redisTemplate.opsForValue().set(code, originalUrl, ttl);

// prefer
redisTemplate
        .opsForValue()
        .set(code, originalUrl, ttl);
```

## Types & APIs

### Declare to the interface, not the implementation

Fields, locals, parameters and return types use the widest type the code actually needs. Use the concrete type only
when it's genuinely required (for example `LinkedHashMap` for guaranteed insertion order, or `ArrayDeque` for `Deque`
semantics).

```java
// flag
ArrayList<UUID> interestTopicIds = new ArrayList<>();
HashMap<UUID, Subscription> subscriptionsByTopicId = new HashMap<>();
public ArrayList<FeedTopicResponseDTO> buildFeedTopics() { ... }

// prefer
List<UUID> interestTopicIds = new ArrayList<>();
Map<UUID, Subscription> subscriptionsByTopicId = new HashMap<>();
public List<FeedTopicResponseDTO> buildFeedTopics() { ... }
```

### Don't hand-roll what a predefined method already does

If code spends several lines, a temp variable, a null/empty check or a loop on something the JDK (or a library already
on the classpath, such as Lombok or Spring's `StringUtils`/`CollectionUtils`) does in a single call, use that call.
When reviewing, name the exact replacement.

```java
// flag — manual get-or-zero-then-increment
Integer count = countsByTopicId.get(topicId);
if (count == null) {
    count = 0;
}
countsByTopicId.put(topicId, count + 1);

// prefer
countsByTopicId.merge(topicId, 1, Integer::sum);
```

Readability wins: the one-liner has to be *clearer*, not just shorter. Don't collapse a readable loop into an
unreadable stream chain.

### Builder over setter chains

See `references/builder.md`.

## Comments & JavaDoc

- **Prefer naming over JavaDoc.** This applies to methods and classes alike. Before writing a JavaDoc, check whether
  renaming the method or class makes it unnecessary; that is the preferred fix. Write one only when a well-named
  method or class still hides something its name can't express: why it exists, a non-obvious constraint, a subtle
  invariant, a workaround for a specific bug, or a surprising side effect.
- **Keep comments few and short.** A comment explains *why*, never *what*; the code already says what.
- **Never reference a `.md` file from a JavaDoc or comment.** Files like `PLAN.md` and `DECISIONS.md` change
  independently of the code they once explained, so a pointer to them goes stale without anyone noticing. State the
  reasoning in the comment itself. If it's worth citing, it's worth restating.

## Packages & DTOs

### DTO direction and naming

DTOs live in a package that states their direction, and their name ends with a matching suffix:

| Package          | Suffix        | Holds                        |
| ---------------- | ------------- | ---------------------------- |
| `/DTOs/request`  | `RequestDTO`  | inbound request bodies       |
| `/DTOs/response` | `ResponseDTO` | anything the API returns     |
| `/DTOs/event`    | `EventDTO`    | Kafka message payloads       |

Don't double a suffix that is already part of the name (`ErrorResponseDTO`, not `ErrorResponseResponseDTO`). One DTO
never serves both directions. Split it even if the fields currently match, so a request field can't leak into the
response contract by accident.

```java
// flag
DTOs/InterestTopicDTO.java
DTOs/SubscribeDTO.java

// prefer
DTOs/response/interesttopics/InterestTopicResponseDTO.java
DTOs/request/SubscribeRequestDTO.java
```

### Grouping into subpackages

- **Once a package holds more than 5 `.java` files** (subpackages don't count), group its contents into subpackages by
  domain or feature, for example `response/auth`, `response/feed`, `exceptions/subscriptions`, `configurations/kafka`.
  This applies to every package, including `services/interfaces` and `services/implementations`.
- Subpackage names are lowercase, plural where the domain is a noun, with words run together
  (`interesttopics`, `subscriptions`).
- A class used across every domain (`ErrorResponseDTO`, `RequestBodyTooLargeException`) stays at the package root
  instead of being forced into one feature's subpackage.

### Enums

Enums in `/entities` live in `entities/enums` (`entities/enums/Role.java`, not `entities/Role.java`). That keeps
`/entities` easy to scan for the actual persisted aggregates.

## Lombok

Used freely: `@RequiredArgsConstructor`, `@Data`, `@Builder`, `@Slf4j`, `@Getter`/`@Setter`, Lombok's `@Value`
(`lombok.Value`, the immutable-class annotation, not Spring's `@Value` property injection), etc.

No records. Model data with plain classes and Lombok annotations, even where a record would otherwise fit. This applies
to DTOs and entities alike.

## JPA entities

Always set `nullable = true` or `nullable = false` explicitly on `@Column`, even when it matches the default.
Nullability should be a visible, explicit decision, not something implied by leaving it out.

## Transactions

Put `@Transactional` on the service methods that actually need it: multi-step writes, read-modify-write flows, anything
that must be atomic. Don't annotate every public service method by default.

Exception: `@Modifying` repository methods carry their own `@Transactional`, because a bulk update or delete needs an
active transaction and its callers may not be transactional. Keep it on the repository method; don't move it to the
service layer.

## Utility classes

Stateless, no Spring dependencies, pure functions. Always add a private constructor to prevent instantiation. Logic
that needs a Spring dependency belongs in a service, not a utility.

## Exceptions

- Custom exceptions extend `RuntimeException` and are named `<Subject><Problem>Exception`
  (`InterestTopicNotFoundException`, `DuplicateEmailException`, `SubscriptionLimitExceededException`).
- Create one exception type per distinct failure the caller or the exception handler has to tell apart. Don't reuse a
  generic one with different messages.
- Map them to HTTP responses in the `@RestControllerAdvice`, not with try/catch in controllers.

## Logging

Use `@Slf4j` in Spring-managed beans (services, controllers, jobs, listeners, filters, configurations). Never log from
entities, DTOs or utilities.

- **INFO**: meaningful business events (subscription created, news event consumed, notification email sent).
- **WARN**: recoverable, unexpected situations (retrying a connection, skipping a malformed event).
- **ERROR**: failures that affect the outcome. Always pass the exception: `log.error("...", e)`.
- **DEBUG**: never commit debug logs. Use them locally and remove them before pushing.
- Use `{}` placeholders, never string concatenation, in log calls.
- Never log secrets: passwords, JWTs, API keys, cookie values.
- Write log and exception messages as full sentences ending in terminal punctuation. A message that ends with a
  concatenated value (`"Permanent mail delivery failure: " + reason`) is fine as it is.

---

## Review checklist

When judging existing code, check in this order:

1. Class layout order, one blank line after the opening brace, one blank line between methods.
2. Fields: one blank line between each, ordered shortest line first within each group.
3. Annotation stacks ordered shortest first, with a multi-line annotation last.
4. Chains of two or more calls split one call per line.
5. `try`/`catch`, `if`/`else` and enum blank lines (see `references/`).
6. Declared types are interfaces. No hand-rolled JDK/library one-liners. No unnamed magic numbers.
7. Names are self-explanatory. JavaDoc only where the name can't say it. No `.md` references in comments.
8. DTO package and suffix. Subpackages once a package has more than 5 files. Entity enums in `entities/enums`.
9. `@Column` nullability is explicit. `@Transactional` only where it's needed. Builder instead of 2+ setter calls.
10. Log levels, `{}` placeholders, no secrets, sentence punctuation.
