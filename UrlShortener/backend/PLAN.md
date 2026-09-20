# URL Shortener — design (implemented)

All steps below are done; this file is kept as the design record, not an active roadmap.
Mid-implementation calls live in `DECISIONS.md`.

## Design

- Persistence: MongoDB. Cache: Redis with a TTL — TTL is Redis-only; cold entries fall out of cache, the
  Mongo document lives forever. Short URLs themselves never expire.
- `POST /api/v1/urls` is idempotent: the **original URL is the Mongo `_id`**, so "does it already exist"
  is a primary-key lookup. Exists → return its stored code. Doesn't → generate a code, insert, return it.
- `GET /{code}` → Redis first, Mongo on miss (then warm Redis), 404 if neither has it → **302** redirect.
- Codes are Snowflake ids (Hutool — `cn.hutool.core.lang.Snowflake`), Base62-encoded. No custom aliases.
- No normalisation — the submitted URL becomes the key verbatim; only trim + control-character rejection.
- Original URLs are capped at **1000 bytes** — keeps the Mongo `_id` index key inside its ~1024-byte
  limit, and doubles as an input-size guard.
- No auth, no hit counting, no stats endpoint, no expiry.

Conventions (layering, package layout, service interface + impl, constructor injection, `/api/v1`,
snake_case test names) are in `CLAUDE.md` and the `java-code-style` / `java-junit` skills.

### Why 302 and not 301

A 301 is cached by browsers effectively forever — once a code is handed out it can never be repointed or
killed, and a single bad insert is permanent for everyone who hit it. 302 keeps every hit coming through
us, so a link stays revocable. The cost is one round trip per hit, and Redis is there precisely to absorb
that.

## Steps (all done)

1. Build baseline — pom deps (Mongo, Redis, Hutool, Testcontainers), env-overridable config.
2. Entity layer + repository — `CommonEntity`/`ShortUrl`, unique `code` index, `ShortUrlRepository`.
3. Code generation — `Base62Encoder` + `SnowflakeShortCodeGenerator`.
4. Cache layer — `ShortUrlCache` (Redis), TTL, degrades to Mongo on outage.
5. Create endpoint — `POST /api/v1/urls`, validation, idempotent insert.
6. Redirect endpoint — `GET /{code}`, cache-then-Mongo resolution, 302.
7. Error handling — `GlobalExceptionHandler`, uniform error body.
8. Exploit-hunting skill — `.claude/skills/exploit-hunter/SKILL.md`.
9. Docker — multi-stage `Dockerfile` + `docker-compose.yml`.
10. Swagger / OpenAPI — springdoc, UI at `/swagger-ui/index.html`.

## Testing rules (applied throughout)

Unit tests mock the layer below; integration tests use Testcontainers, never an embedded fake. No
`Thread.sleep`, no timing assertions, no dependence on execution order, no shared mutable state.
