# Security findings

## Audit — 2026-10-08

**Scope:** gateway `PATCH /api/v1/users/me`; tweet service `GET /internal/v1/tweets/by-author/{authorId}/page` and `GET /api/v1/tweets/count`; timeline `GET /api/v1/author-tweets/{authorId}` and its gateway route (`TimelineRoutesConfiguration`, `CallerIdentityFilters`, `SecurityConfiguration`). Found by reading the code; the stack was not running, so the demonstrations below were not executed.

No Critical or High findings. The identity path is sound: the gateway strips every `X-User-*` header and the cookie/`Authorization`, then sets `X-User-Id` from the verified JWT; both downstream services parse it as a canonical UUID.

### 1. No rate limiting on a fan-out read (Medium)

- **Location:** `AuthorTweetsController.java:26`, `AuthorTweetsServiceImpl.java:31-47`, `TweetItemAssemblyServiceImpl.java:83-90`; no rate-limit code exists in any service.
- **Mechanism:** One authenticated `author-tweets` request with `size=100` costs a gateway → timeline hop, a timeline → gateway `/internal/v1/users` call, a timeline → tweet service page read, and four Postgres queries (views, saved, likes, liked). Registration needs email confirmation but is otherwise open, so an attacker can hold many accounts and repeat this without limit. `/api/v1/tweets/count` is similar and cheaper: `countByAuthorId` scans every tweet of a prolific author.
- **Demonstration:**
  ```
  seq 1 500 | xargs -P50 -I{} curl -s -o /dev/null -b "access_token=$JWT" \
    "http://localhost:8080/api/v1/author-tweets/$AUTHOR?size=100"
  ```
- **Recommendation:** per-user rate limit at the gateway on the proxied read routes. **Status:** accepted 2026-10-10, no rate limiting is planned.

### 2. `PATCH /users/me` accepts values the database rejects (Low)

- **Location:** `UpdateProfileRequestDTO.java:25-29` (`@Past` has no lower bound, `bio`/`location` have no character check); `GlobalExceptionHandler.java:120-124`.
- **Mechanism:** `birthdate` accepts any past date, including ones outside Postgres's `date` range. `bio` and `location` accept `\u0000`, which Postgres text columns refuse. The failure appears at flush as a DB error; if it is a `DataIntegrityViolationException` it is answered as `409 "The request conflicts with existing data."`, which is misleading, otherwise as a logged 500. No data leaks, and the caller can only hurt their own request.
- **Demonstration:**
  ```
  curl -X PATCH localhost:8080/api/v1/users/me -b "access_token=$JWT" \
    -H 'Content-Type: application/json' -d '{"birthdate":"-5000-01-01"}'
  curl -X PATCH localhost:8080/api/v1/users/me -b "access_token=$JWT" \
    -H 'Content-Type: application/json' -d '{"bio":"a\u0000b"}'
  ```
- **Recommendation:** add a lower bound on `birthdate` (for example 1900-01-01) and reject control characters in `bio`/`location`, so these fail as 400 at validation.

### 3. Birthdate is visible to every authenticated user (Low, privacy)

- **Location:** `ProfileMapper.java:toResponse` (`.birthdate(user.getBirthdate())`), served by `UserController.getProfile`.
- **Mechanism:** the value written by `PATCH /users/me` is returned unconditionally by `GET /api/v1/users/{username}` to any logged-in viewer. The profile form should say so, or the field should be limited to the owner.
- **Demonstration:** `curl -b "access_token=$JWT_OF_ANY_USER" localhost:8080/api/v1/users/victim` returns `"birthdate":"…"`.

### 4. Tweet and timeline services trust the network, not a secret (Low, defence in depth)

- **Location:** `InternalTweetController.java` (documented as "not reachable from outside"), `CurrentUserIdArgumentResolver.java`.
- **Mechanism:** `/internal/v1/tweets/**` has no authentication, and `X-User-Id` is accepted from whoever reaches the port. Today `docker-compose.yml` publishes neither service and the gateway forwards only `/api/v1/tweets/**`, so this is not reachable. Any future `ports:` mapping, or a broader gateway route, would expose every tweet and let a caller act as any user. The gateway already has `InternalApiSecretFilter` for its own internal API; the tweet service has no equivalent.
- **Demonstration (only if the port is ever published):** `curl "http://HOST:8081/internal/v1/tweets/by-author/$ANY_UUID/page"`; or add `-H "X-User-Id: $VICTIM"` to `PUT`/`DELETE /api/v1/tweets/{id}`.

### Considered, not a finding

- **Injection:** `authorId` and the cursor reach Mongo only as a typed `UUID` and `Instant`. The cursor is re-encoded and compared, so it accepts only the exact canonical form. The timeline's `RestClient` URI templates encode values (default `TEMPLATE_AND_VALUES`), so `&` in a cursor cannot add parameters.
- **Path tricks to `/internal/**`:** Spring Security's `StrictHttpFirewall` rejects `..`, `%2e`, `%2f` and `;` before the proxy route, and `InternalApiSecretFilter` reads the path the way MVC does. Not exercised at runtime.
- **IDOR:** `PATCH /me` takes the id only from the JWT subject; the DTO has three fields, so there is no mass assignment. Tweets and an author's tweet list are readable by any user by design. `/count` and the author page return no private data.
- **Enumeration:** `author-tweets` answers 404 for unknown/unconfirmed users and 200 otherwise, but ids are random UUIDs, so there is nothing to guess.
- **Info disclosure:** all handlers return fixed messages; stack traces are only logged. Only `health` is exposed by actuator on the services.
- **Races:** `PATCH /me` uses `@DynamicUpdate` with no check-then-act. Paging has no write path.
- **CSRF/XSS:** cookie is HttpOnly + SameSite=Strict, and PATCH requires `application/json`. No `dangerouslySetInnerHTML`, `innerHTML` or `localStorage` in `frontend/src`.
- **Body size and page size:** an 8 KB body cap applies to the PATCH; page size is limited to 1–100.
- **SSRF, file upload, secrets, dependencies:** no user-supplied URLs in these routes; nothing uploaded; no secrets seen in the files read; dependency versions not reviewed.
