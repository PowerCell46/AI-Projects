# Decisions

Non-obvious calls made *during* implementation - the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Step 1 - `mongo:8.2`, not `mongo:8`

The plan says `mongo:8`. On the dev machine's Docker VM (kernel 7.0.12) `mongo:8` and `mongo:8.0` refuse to
start: "Linux kernel versions 6.19 and newer has a known incompatibility with this version of MongoDB"
(SERVER-121912). `mongo:8.2` (8.2.12) and `mongo:7` both start. Chosen: `8.2`, pinned in the compose service
and in `AbstractMongoIntegrationTest`, over `7` (a further step from the plan's major). It's a short-lived
release line, so revisit and move back to `mongo:8` once the fix ships in it.

## Step 1 - `SERVER_PORT` stays out of the root `.env`

The plan makes the port `SERVER_PORT` (default 8081). Boot binds that env var to `server.port` for *every*
app started from the shared root `.env`, so setting it there would move the gateway too. The property default
is 8081 and `.env.example` carries the variable commented out, with the reason.

## Step 1 - the body-size filter starts with a single cap

Ported as the gateway's filter minus its upload-route special case. The tweet-create route's own cap
(`app.request.max-tweet-body-bytes`) and the derived multipart limits arrive with the create endpoint
(step 5), where they're first used.

## Step 1 - only the exception handlers that exist are ported

`GlobalExceptionHandler` keeps the gateway's Spring MVC overrides (fixed messages, no internals echoed) and
the 413 / 500 handlers. The gateway's domain handlers, and its `DataIntegrityViolationException` fallback
(no unique constraint exists here), are left out; each domain exception adds its handler in the step that
introduces it.

## Step 1 - `X-User-Id` must be the canonical UUID form

`UUID.fromString` accepts shortened input such as `1-1-1-1-1`. The resolver only accepts a value that
round-trips to the canonical 36-character form (case-insensitive), so a malformed header is a `400` instead
of a bogus caller id.

## Step 3 - the test Mongo container needs `.withReplicaSet()`

Testcontainers 2.x's `org.testcontainers.mongodb.MongoDBContainer` starts a standalone server unless
`withReplicaSet()` is called (the 1.x class was a replica set by default). Standalone made every write in a
transaction fail with "does not support retryable writes", which the transaction-rollback test caught. The
base class now always asks for the replica set.

## Step 3 - repositories use `insert`, not `save`, for new documents (from step 5)

The ids are assigned, so `save` on a document with an id is an upsert and would silently overwrite a
colliding id. Tests that need a plain write use `save`; the services will use `insert`.

## Step 3 - custom repository operations return `boolean` / `Optional`

`updateContentIfAuthor` and `deleteIfAuthor` return whether a document matched or was deleted, and
`findAndIncrementViews` returns the post-increment tweet or empty. The services map "false" to `404`, which
is what the plan's conditional-update and conditional-delete steps need and nothing more.

## Step 4 - image exceptions live in `exceptions/images`

The gateway keeps them in `exceptions/files` because it stores profile pictures as files. Here the only stored
thing is a tweet image, so the subpackage is named for that. `EmptyUploadException` isn't ported yet: nothing
uses it until create (step 5).

## Step 4 - the try/catch blank line, unlike the gateway

`ObjectStorageServiceImpl` follows the try/catch rule in `java-code-style` (a blank line before the closing
brace of the `try` block), which the gateway's copy doesn't.

## Step 4 - MinIO keys are required at startup

`app.minio.access-key` / `secret-key` have no default, as in the gateway, so `spring-boot:run` now needs
`MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` exported from the root `.env` (and MinIO up: the bucket initializer
fails startup when it is unreachable).

## Step 5 - the Identity group covers only the endpoints that exist

The catalog parameterizes the identity tests over all five endpoints, but an endpoint that isn't built yet
answers 404, not 400. `endpoints()` returns `POST /api/v1/tweets` only; steps 6, 7 and 8 each add their
endpoint to it, so the group is green now and reaches five by the end.

## Step 5 - "the body over the tweet cap" runs through MockMvc, the multipart limits over real HTTP

MockMvc applies the body-size filter to a raw body with a declared length, so the 413 for a body over the
tweet cap is a MockMvc scenario. It never applies Spring's multipart limits, so the "one byte over 5 MB"
and "4 images at exactly 5 MB" checks are in `TweetUploadLimitsIntegrationTest` (a real port), as the gateway
does for its picture routes. The catalog's "image over 5 MB" scenario in the MockMvc class exercises the
service's own size check.

## Step 5 - text is trimmed with `strip()`

`String.strip()` removes Unicode whitespace, `trim()` only ASCII control characters and spaces. A tweet of
only a no-break-space is not "blank" to `strip()`: `isWhitespace` is false for U+00A0. Accepted, since it is
a visible-length-1 tweet either way.

## Step 5 - `app.kafka.tweet-created.name` arrives now, the topic bean in step 9

The outbox message needs the topic name, so the property and `TWEET_CREATED_TOPIC_NAME` exist from step 5.
The `NewTopic` bean and the partition count come with the Kafka producer configuration in step 9.

## Step 5 - `EmptyUploadException` and the three tweet-validation exceptions

`EmptyUploadException` is ported into `exceptions/images`. The text and image-count rules are three
exceptions in `exceptions/tweets` (`EmptyTweetException`, `TweetContentTooLongException`,
`TooManyImagesException`), each with its own `400` handler, since the caller tells them apart by message.

## Step 6 - the GET routes declare `@CurrentUserId` without using it

Every route must answer 400 without `X-User-Id`, and the resolver only runs for a declared parameter.
The unused parameter is the smallest way to enforce that; a filter would need its own path rules.

## Step 6 - a missing image is its own 404 ("Image not found.")

Unknown tweet is "Tweet not found."; a tweet without that image id (including another tweet's image) is
`TweetImageNotFoundException`, so the two cases stay distinguishable in tests and logs, both `404`.

## Step 7 - update re-reads the tweet after the conditional write

`updateContentIfAuthor` only says whether a document matched, and the tweet loaded for the checks has a
stale `views` by then. The response comes from a second `findById`, so `views` is current; if a delete
slipped in between, that read is a `404` too.

## Step 7 - one `NotTweetAuthorException` for both 403s

It takes the message, with `EDIT_MESSAGE` as a constant; step 8 adds the delete message. One type because the
handler and the status are the same and only the wording differs.

## Step 7 - a missing `content` field is treated as blank

`{}` or `{"content": null}` goes through the same blank rules as `""`, so it is a 400 on a text-only tweet
and accepted on a tweet with images.

## Step 8 - a delete write conflict is retried unless the tweet is gone

The plan maps a Mongo write conflict from a concurrent delete to `404`. A conflict can also come from a
concurrent *edit* (a non-transactional write that landed after the delete's snapshot), and answering `404`
then would leave the tweet alive. So on a conflict (error label `TransientTransactionError` or code 112) the
service checks whether the tweet still exists: gone -> `404` (another delete won); still there -> the
transaction is tried again, at most 3 attempts, then the conflict is rethrown (`500`). This keeps "edit ∥
delete always ends with no document" true.

## Step 8 - `app.kafka.tweet-deleted.name` and the deleted event

The property (`TWEET_DELETED_TOPIC_NAME`) and `TweetDeletedEventDTO` arrive now, like the created topic in
step 5; the `NewTopic` beans come in step 9.

## Step 9 - the publisher's Kafka tests prove absence with a sentinel

"A FAILED message is never published" publishes a fresh sentinel message after the action, reads the topic
until the sentinel shows up, and asserts the failed key is not among the records read before it, instead of
the gateway's fixed 2-second poll. The publisher's failing-send and `FAILED`-at-3 paths are mocked unit tests
(no broker needed to fail a send), as in the gateway.

## Step 9 - a message is saved back with `save`, deleted with `delete`

Recording a failed attempt uses `save` on an assigned id, which is an upsert. With one instance and the
message just read, that is safe; with a second instance it could resurrect a message the other instance just
deleted. It is covered by the existing "no row-claiming" accepted gap.

## Step 10 - the audit's findings are logged, not fixed

The `exploit-hunter` audit (`exploit-report-2026-09-30.md`) found nothing above Low except the known
`X-User-Id` trust. The four findings are in `PLAN.md` Accepted gaps with their triggers. The concurrency
suite runs through MockMvc (every scenario overlaps its threads on one latch) rather than a real port, since
the contended operations are Mongo writes, not the servlet layer.


## Timeline plan step 1 - internal batch read reuses `findAllById`

`GET /internal/v1/tweets?ids=` calls Spring Data's `findAllById`, which is already one `_id $in` query, so no
custom repository method was added. The 1-100 limit counts the ids as sent, before repeats collapse, so the
request size is bounded; a list outside it answers 400 "Provide between 1 and 100 tweet ids." through its own
`TweetIdsOutOfRangeException`. A non-UUID id, an empty `ids` and a missing `ids` are all 400 through the
existing Spring MVC handlers. `TweetResponseDTO` is returned unchanged, `views` included, until timeline phase 3
removes it.


## Timeline plan step 12 - the collections are created at startup

The timeline e2e posted three first-ever tweets at once on a fresh Mongo and one answered 500: each write
transaction tried to create the empty `tweets` collection and Mongo failed all but one with a `WriteConflict`
("Collection namespace ... is already in use"). `MongoCollectionInitializer` now creates `tweets` and `outbox`
if missing, as a `SmartInitializingSingleton`, so it runs before the web server accepts a request (an
`ApplicationRunner` like the MinIO bucket initializer would leave a short window). If another instance creates
one first, the failure is swallowed once the collection is seen to exist. Chosen over a retry of the transaction,
which would hide the cause; the `TransientTransactionError` label stays unhandled for other conflicts.
