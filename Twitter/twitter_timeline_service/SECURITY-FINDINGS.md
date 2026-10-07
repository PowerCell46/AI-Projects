# Security findings

## Audit — 2026-10-07

**Scope:** the timeline service's `GET /api/v1/tweet-details/{tweetId}` (`TweetDetailsController`,
`TweetDetailsServiceImpl`, `TweetItemAssemblyServiceImpl.assembleFetched`, `TweetItemMapper`, the two lookups), its
gateway route (`TimelineRoutesConfiguration.TWEET_DETAILS_PATH`, uncommitted at audit time) and the two internal
endpoints it calls. Read from source only; nothing was executed (no service, Kafka or database was running), so each
demonstration below is a request to run, not an observed result. Dependencies were not scanned for CVEs.

No Critical, High or Medium finding. Two Low, two Info.

### 1. Sequential downstream calls can outlive the gateway's read timeout — Low

- **Location:** `services/implementations/tweetdetails/TweetDetailsServiceImpl.java:28-40`;
  `src/main/resources/application.properties` (the comment above `spring.http.clients.*`).
- **Mechanism:** the properties file promises downstream timeouts "stay under the gateway's 10s read timeout", which
  holds for the feed (two parallel calls, one 5 s bound). The details read calls the tweet service and then the gateway
  one after the other, each with a 2 s connect and a 5 s read timeout, so a slow pair can hold the request for up to
  ~14 s. The gateway gives up at 10 s (`UPSTREAM_READ_TIMEOUT`) and answers 504, while the timeline request thread keeps
  working and then writes to a closed connection. The sequence is a recorded choice (`DECISIONS.md`), the budget
  arithmetic is not.
- **Demonstration:** stall the tweet service's `/internal/v1/tweets` and the gateway's `/internal/v1/users` for ~4.9 s
  each (below the 5 s read timeout, so neither call fails), then
  `curl -i -b "<auth cookie>" http://localhost:8080/api/v1/tweet-details/<existing-tweet-uuid>`. Expected: `504` at
  ~10 s from the gateway; the timeline service's log shows the request finishing at ~9.8 s with no error.
  Many such requests keep Tomcat threads busy past what the caller sees.
- **Needs:** a downstream that is slow, not an attacker-controlled one. An authenticated caller can only add load.

### 2. No rate limit on the costliest single-tweet read — Low

- **Location:** `TweetDetailsController.java:22-25`.
- **Mechanism:** any authenticated user can repeat the call. A tweet that exists costs one Mongo read at the tweet
  service, one Postgres `IN` at the gateway and four queries here (views, saved, like counts, liked), and, unlike the
  feed, nothing is batched across ids. A random UUID is cheaper (one call, then 404). Same posture as every other
  authenticated route (`PLAN.md`, "No rate limiting").
- **Demonstration:** `for i in $(seq 500); do curl -s -o /dev/null -b "<auth cookie>" http://localhost:8080/api/v1/tweet-details/<existing-tweet-uuid> & done; wait`
  and watch the tweet service, gateway and `postgres-timeline` load.

### 3. 404 body tells "tweet gone" from "author disabled" — Info

- **Location:** `TweetNotFoundException.java` ("Tweet not found.") vs `AuthorNotFoundException.java`
  ("Author not found."); the gateway's `getUsers` drops users that are not enabled (`.filter(User::isEnabled)`).
- **Mechanism:** a tweet whose author's account is not enabled answers a different 404 body from a tweet that does not
  exist, so a caller who knows a tweet id learns that the tweet exists and its author is disabled. Tweet ids are
  random UUIDs, so this cannot be used to enumerate; it only confirms an id the caller already has. The tweet itself is
  readable by any authenticated user through `GET /api/v1/tweets/{id}` anyway.
- **Demonstration:** `curl -b "<auth cookie>" http://localhost:8080/api/v1/tweet-details/<id-of-a-disabled-authors-tweet>`
  returns `{"status":404,"messages":["Author not found.",...]}`; the same call with a random UUID returns
  `"Tweet not found."`.

### 4. A malformed downstream answer ends as a 500 with a stack trace in the log — Info

- **Location:** `TweetItemMapper.java` (`tweet.getImages().stream()`), `TweetDetailsServiceImpl.java:36`
  (`List.of(tweet.getAuthorId())`), `TweetLookupServiceImpl.findByIds` (`toMap` on a null id).
- **Mechanism:** a tweet-service answer with `images` or `authorId` missing, or an item without `id`, throws an NPE.
  `GlobalExceptionHandler.handleUnexpectedError` returns the fixed 500 body (nothing leaks to the client) and logs the
  stack trace at ERROR. The tweet service never sends this today; it needs a misbehaving or replaced downstream, so the
  caller cannot trigger it.
- **Demonstration:** stub `GET /internal/v1/tweets?ids=...` to answer `[{"id":"<uuid>","authorId":"<uuid>"}]` and call
  the endpoint: `500 "An unexpected error occurred."`.

### Also considered

- **Injection:** `tweetId` is bound to `UUID` (non-UUID → fixed 400, nothing logged); the downstream URL is a
  template variable built from `UUID.toString()`; `findSavedTweetIds` / `findLikedTweetIds` are JPQL with bound
  parameters and the count reads use `findAllById`. Log lines in this path carry statuses and exception types only.
- **SSRF / open redirect:** N/A, downstream URLs come from configuration; the caller supplies only a UUID.
- **Access control:** any authenticated user may read any tweet, the same as `GET /api/v1/tweets/{id}`; there is no
  private-tweet or block concept. `savedByMe` / `likedByMe` come from the `X-User-Id` header, which the gateway replaces
  with the JWT `sub` (it strips every `X-User-*` first); no endpoint here names another user's state. Direct access to
  :8083 trusts the header, the already accepted gap. Replies live in their own collection, so a reply id answers 404.
- **Resource limits:** a single path variable, no body; `GET` bodies are ignored. Rate limiting: see 2.
- **Information disclosure:** error bodies are fixed strings; the response carries `username` and a server-built
  `/files/<id>` picture URL, no email; the secret goes only to the gateway client and the tweet-service client sends none.
- **Caching:** the service sets no cache entries and the response holds per-viewer flags; no shared cache sits in the
  chain in this repo.
- **Secrets / config, dependencies:** nothing new on this route; dependency CVEs not scanned.
- **Browser-side:** the endpoint returns JSON and sets no cookies; no raw-HTML sink consumes it in `frontend/src`
  (no `dangerouslySetInnerHTML` / `innerHTML` outside tests). CSRF: `GET`, read-only.
- **Gateway route:** `/api/v1/tweet-details/*` matches one segment and sits behind `anyRequest().authenticated()`;
  the route has no method restriction, so `POST` / `PUT` / `DELETE` are forwarded and answered `405` with a fixed body.
  A trailing-slash variant (`.../<uuid>/`) may pass the gateway's path predicate and reach the service's
  `NoResourceFoundException` log line (the existing unconfirmed "unescaped path at WARN" item); the path is UUID-shaped
  or firewall-rejected on the way through, so it is not a new forged-log-line path.
