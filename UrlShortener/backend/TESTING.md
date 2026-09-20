# E2E test catalog

Scope: HTTP-layer tests only — full stack through `DispatcherServlet` via `RestTestClient` against
Testcontainers Mongo/Redis. Repository- and cache-level integration tests (`ShortUrlRepositoryTest`,
`RedisShortUrlCacheIntegrationTest`) exercise real infra too but skip the HTTP layer, so they're not
listed here.

Hand-maintained — see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

## `POST /api/v1/urls`

`ShortUrlControllerIntegrationTest.CreateShortUrl`

- Creates a new short URL and returns it (`should_create_a_new_short_url_and_return_it`)
- Posting the same URL twice returns the same code — idempotency (`should_return_the_same_code_when_the_same_url_is_posted_twice`)
- Rejects a URL over the 1000-byte cap (`should_reject_a_url_over_the_1000_byte_cap`)
- Rejects a non-http(s) scheme (`should_reject_a_non_http_scheme`)
- Rejects an internal host — metadata IP (`should_reject_an_internal_host`)
- Rejects a blank URL (`should_reject_a_blank_url`)
- Invalid URL gets an error body naming the field, no exception/package leakage (`should_return_an_error_body_for_an_invalid_url`)
- Malformed JSON body returns 400 with a clean error body (`should_return_400_for_a_malformed_request_body`)

## `GET /{code}`

`RedirectControllerIntegrationTest`

- Redirects (302) to the original URL and warms the Redis cache (`should_redirect_to_the_original_url_and_warm_the_cache`)
- Unknown code returns 404 with an error body, no code/exception/package leakage (`should_return_404_with_an_error_body_for_an_unknown_code`)

## Known gaps

Accepted risks, not covered by design (see `DECISIONS.md` for the reasoning):

- Codes are enumerable — worker/datacenter id fixed, only a 12-bit sequence counter varies per millisecond.
- No rate limiting on `POST /api/v1/urls`.
- DNS-rebinding: a hostname that itself resolves to an internal address only after create-time validation is not caught (redirect-time re-resolution wasn't taken on).
