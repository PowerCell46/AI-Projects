# Security findings

## Audit — 2026-10-08

**Scope:** gateway `PATCH /api/v1/users/me`; tweet service `GET /internal/v1/tweets/by-author/{authorId}/page` and `GET /api/v1/tweets/count`; timeline `GET /api/v1/author-tweets/{authorId}` and its gateway route (`TimelineRoutesConfiguration`, `CallerIdentityFilters`, `SecurityConfiguration`). Found by reading the code; the stack was not running, so the demonstrations below were not executed.

No Critical or High findings. The identity path is sound: the gateway strips every `X-User-*` header and the cookie/`Authorization`, then sets `X-User-Id` from the verified JWT; both downstream services parse it as a canonical UUID.

0 findings fixed; the open ones are kept privately.

### 2. (moved out of the public repo, 2026-10-10)

### 3. (moved out of the public repo, 2026-10-10)

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
