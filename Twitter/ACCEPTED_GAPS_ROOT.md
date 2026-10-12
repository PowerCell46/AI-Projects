# Accepted gaps across services

Consolidated open and accepted gaps with their triggers, moved from service-level `PLAN.md` files.

---

## Twitter API Gateway

(Audit references point at `security-private/gateway-SECURITY-AUDITS-open.md`, which holds the open and accepted findings; the public `twitter_api_gateway/docs/SECURITY-AUDITS.md` keeps only fixed findings.)

- **One `enabled` flag means "confirmed"; no roles/admin.** Trigger: first ban, or moderation features.
- **No rate limiting** except the resend cooldown (login capped only by bcrypt-12; register, uploads, follows and the tweet routes unthrottled). Trigger: abuse.
- **Register leaks whether an email/username exists** (explicit `409`s). Login's `403` reveals "unconfirmed" only to someone holding the password.
- **Squatting:** a pending account holds an email/handle up to 7 days plus cleanup; a real owner clicking the squatter's link confirms an account the squatter controls. Trigger: password reset exists, or first report.
- **Email and live confirmation token travel through Kafka in plaintext.** Trigger: Kafka exposed → TLS/SASL.
- **Outbox is at-least-once** (the mail service dedupes on `eventId`); **`FAILED` rows need manual handling** (trigger: first one that matters). Two instances are covered since 2026-10-10: the poller claims rows with `SKIP LOCKED`, and the cleanup job and the seeder take a Postgres advisory lock (see `twitter_api_gateway/DECISIONS.md`).
- **Dev compose has default credentials and plaintext Kafka** (ports bound to `127.0.0.1`). Trigger: any shared environment.
- **Log lines can carry attacker-chosen or personal text** (unknown-path WARN, constraint-violation WARN with email). Trigger: logs shipped to a shared system.
- **`ddl-auto=update`, no migrations.** Trigger: second environment or first destructive change → Flyway.
- **JWT 1h, no refresh, no revocation.** Trigger: hourly logouts annoy, or need to kill sessions.
- **Orphan MinIO objects** when the post-commit delete of an old object fails. Trigger: storage growth → sweep job.
- **Images stored as uploaded: no resize, EXIF (incl. GPS) retained.** Decision 2026-10-10: accepted, no stripping or resizing is planned, including for a public deployment. Anyone who can load an image also gets its EXIF (GPS, camera model).
- **Images (profile and tweet) are proxied through the gateway.** Trigger: image traffic dominates → presigned URLs/CDN.
- **Any logged-in user can fetch any file id** (all files are profile images). Trigger: first private media type.
- **No restrictive `Content-Security-Policy` on served files** (phase 2 audit, Low; not exploitable while only JPEG/PNG/WebP are accepted). Trigger: loosening the type check (e.g. SVG).
- **Follower and following lists are readable by any logged-in user** (phase 3 audit, Low): no private accounts by design. Trigger: private accounts → check the viewer against the target's visibility.
- **State-changing routes rely on `SameSite=Strict` alone, with CSRF disabled** (phase 3 audit, Low): it doesn't stop a request from a sibling subdomain of the same site. Trigger: untrusted content on a sibling subdomain → a CSRF token.
- **Hot-row contention on a popular account's counters.** Trigger: measurable follow latency → sharded counters.
- **No counter reconciliation job** (`@Check >= 0` catches only negative drift). Trigger: any observed drift.
- **Any logged-in user can page through every confirmed account** (phase 5), with each person's `followersCount`; profiles were already readable by username, and this makes them enumerable. Trigger: abuse → rate limit on `GET /api/v1/users`.
- **No popularity ordering** (phase 5): newest first, because counters move while a reader pages and would repeat or skip people. Trigger: newest-first stops being useful → a ranked list from a snapshot or a stored score, which needs its own design.
- **No search or filter on the people list** (phase 5). Trigger: the first request to find someone by name → a `q` parameter on the same endpoint.
- **User and file ids are time-based UUIDs** (`@UuidGenerator(style = TIME)` in `CommonEntity`): they embed the host's IPv4 address, JVM start time and a counter (phase 5 audit, Low). No id is a secret today. Trigger: a deployment whose network addresses matter, or any use of an id as a secret → a random or v7 generator.
- **The three follow-list queries still use the `OR` cursor predicate:** a page reads the rows newer than the cursor within one account's follows. Trigger: an account with six-figure follows or a slow list → the row-value form the people list uses.

### Gateway Phase 4 gaps
- **The tweet service trusts `X-User-Id`** and is safe only while nothing but the gateway can reach it. Trigger: any shared or deployed network → a shared internal secret or mTLS.
- **One 10s read timeout for every route.** Trigger: slow uploads start hitting `504` → a per-route timeout.
- **Only `X-User-*` headers are stripped;** look-alikes (`X-UserId`, `X-Original-User-Id`) reach the tweet service (audit finding 2, Low). Trigger: any downstream that trusts another identity-like header → forward an allowlist.
- **Path segments `;x=y`, `/./` and `//` answer `401`** even with a valid cookie (audit finding 3, Info); it fails closed. Trigger: a client that legitimately sends such paths.
- **Tomcat's HTML `400` page for paths it rejects** (audit finding 4, Info; gateway-wide). Trigger: a public deployment → an error page or filter that answers JSON.

---

## Twitter Tweet Service

- **The service trusts `X-User-Id` blindly.** Anyone who reaches port 8081 can act as any user; mitigated by the gateway re-setting the header and the port being private. It also holds `INTERNAL_API_SECRET`. **Trigger:** reachable by anything but the gateway (a published port, a broader gateway route, a service on the network that is not trusted) → a shared internal secret, or mTLS. A private Docker network on one host does not trigger it.
- **No `tweet.updated` event.** **Trigger:** the first consumer that stores tweet text (search, timeline cache).
- **No edit window and no edit history.** **Trigger:** misuse of edits → a 1h window and/or stored versions.
- **Images can't be edited**; delete and repost instead.
- **Orphan MinIO objects** when a best-effort delete fails. **Trigger:** noticeable storage growth → a sweep job.
- **Images stored as uploaded: no resize, EXIF (including GPS) retained.** **Decision 2026-10-10: accepted, no stripping or resizing is planned**, including for a public deployment. Anyone who can load an image also gets its EXIF (GPS, camera model).
- **Any caller can fetch any tweet image** by id (all tweets are public). **Trigger:** private accounts or media.
- **No rate limiting on tweeting or replies.** **Trigger:** abuse, or a second instance.
- **Outbox is at-least-once**, has **no row-claiming** (trigger: second instance → claim with `findAndModify` on a `claimedBy`/`claimedAt` field), and **`FAILED` messages need manual handling** (trigger: the first one that matters).
- **Scheduled jobs run on every instance.** **Trigger:** a second instance → a lock (e.g. ShedLock's Mongo provider).
- **Dev Mongo has no auth** (bound to `127.0.0.1`). **Trigger:** any shared environment → auth plus a replica-set keyfile.
- **A large `content` part is read into memory before it is rejected** (up to the 21 MB create cap), and **up to 10 file parts are parsed before the 4-image limit applies** (`security-private/tweet-SECURITY-AUDITS-open.md`, 2026-09-30). **Trigger:** a caller path that isn't authenticated and metered → a per-part size cap and Tomcat's `max-part-count` set to 5.
- **Tweet text keeps control and bidirectional characters.** **Trigger:** the first consumer that renders `content` → sanitise there, or reject control characters on write.
- **Mongo schema is enforced only by the application.** **Trigger:** a second writer to the collection.
- **Large tweet delete:** very many replies make one large transaction. **Trigger:** slow or failing deletes → delete the tweet, sweep its replies afterwards.
- **Hot tweet document:** replies on a viral tweet conflict on `replyCount` and retry; past ~2 s the caller gets `503`. **Trigger:** `503 BUSY` in practice → a counter outside the tweet document.
- **One gateway call per replies page,** no cache. **Trigger:** details p95, or gateway load → cache authors.
- **No `reply.created` event.** **Trigger:** notifications → an event through the outbox.
- **`replyCount` can exceed the visible replies** if an author disappears. **Trigger:** account deletion → remove or anonymise their replies.
- **Counts on a list post go stale after the details page:** reply, press Back, and the feed post shows the old count (and like/save state) until a reload. **Trigger:** users noticing → the details page reports changes to `Shell`.
- **`/saved` and `/liked` reload from the top on Back** from a details page. **Trigger:** long saved or liked lists → keep those pages mounted too.
- **Your new reply shows under the composer, not in order,** until a reload; **other people's replies appear only after a reload.** **Trigger:** live conversations → polling or a push channel.

---

## Twitter Mail Service

(Audit references point at `security-private/mail-SECURITY-AUDITS-open.md`, which holds the open and accepted findings; the public `twitter_mail_service/docs/SECURITY-AUDITS.md` keeps only fixed findings.)

- **One duplicate email per crash or Redis blip between send and mark.** The at-least-once window. **Permanent.**
- **Blocking retries hold a partition** for up to about 4 minutes while SMTP is down. **Trigger:** latency complaints → `@RetryableTopic`, with a schedule that covers outages. One recipient can cause it, not only an outage: an account on a domain that always answers `4xx` holds the partition its `followeeId` hashes to for about 242s per follow event (`twitter_mail_service/docs/SECURITY-AUDITS.md`, phase 2).
- **Redis AOF is `everysec`**, so a crash can lose about 1s of dedupe keys, which can only cause duplicates. **Permanent.**
- **No DLT replay tool (Q8).** Dead-lettered records are only logged. **Trigger:** the first dead-lettered record someone wants delivered → port SignalFlow's `dlt-replay` profile.
- **`confirmationUrl`, `followeeEmail` and both usernames are trusted as sent (Q7).** A writer to Kafka could make the account send any link or email any address, and can read the follow graph and recipient addresses from `user.followed` and its DLT (`twitter_mail_service/docs/SECURITY-AUDITS.md`). **Trigger:** Kafka reachable beyond a private network → a prefix check against `CONFIRMATION_LINK_BASE_URL`, plus Kafka TLS/SASL (the gateway's existing gap).
- **Gmail sending quota** (about 500 recipients a day), shared by both emails. **Trigger:** it is hit → SignalFlow's quota pause-and-resume, including the `5.4.5`-is-transient fix.
- **No volume cap on follow emails.** The 24h window stops repeats for one pair, not fan-out: one account that follows N users, or N accounts that follow one user, send N emails (`twitter_mail_service/docs/SECURITY-AUDITS.md`). The cap belongs on the gateway's follow endpoint (its "no rate limiting" gap). **Trigger:** abuse, or the Gmail quota is hit.
- **A failed send leaves no window.** A recipient whose mailbox bounces is retried on every new follow event, so a follow/unfollow loop costs one SMTP attempt, one bounce and one DLT record each (`twitter_mail_service/docs/SECURITY-AUDITS.md`). **Trigger:** bounce complaints or a sender-reputation hit → a short negative window after a permanent failure.
- **No unsubscribe or opt-out for follow emails**, and no `List-Unsubscribe`. **Trigger:** a complaint, or approaching the bulk-sender thresholds → a notification setting in the gateway plus a signed one-click link.
- **A follow email can arrive after an unfollow**, because the event was already queued. **Trigger:** a complaint.
- **Expiry is shown in one fixed zone (Q6).** **Trigger:** users outside that zone → a time zone on the account, carried in the event.
- **The follow email links to `/feed`, not to the follower's profile (Q4).** **Trigger:** the SPA gets a profile page.
- **A fully dark HTML email can be inverted or flattened** by some clients' dark mode (Outlook, the Gmail app). **Trigger:** rendering complaints → a light variant.
- **Dev compose has fixed credentials and plaintext SMTP to Mailpit** (bound to `127.0.0.1`), the `Dockerfile` uses floating `eclipse-temurin` tags, and the e2e Postgres is published on every interface. **Trigger:** any shared environment → generated secrets, pinned digests, no published e2e Postgres port.
- **`/actuator/health` shows details** (`show-details=always`: working directory, free disk, Redis version) on port 8082, which no compose file publishes. **Trigger:** reachable from outside the internal network → `when-authorized` with a security starter, or bind the management port to loopback.

---

## Twitter Timeline Service

(Audit references point at `security-private/timeline-SECURITY-AUDITS-open.md`, which holds the open and accepted findings; the public `twitter_timeline_service/docs/SECURITY-AUDITS.md` keeps only fixed findings.)

- **Tweets are fetched over HTTP on every read, and a read needs both downstreams.** **Trigger:** feed p95 above ~200 ms or availability complaints → cache tweets by id (Redis, evicted on `tweet.deleted`, plus a `tweet.updated` event), or serve tweets without authors.
- **Fan-out on write for big accounts,** at processing time: N followers = N/1,000 gateway calls and N rows, holding a partition; a delayed event goes to whoever follows then. **Trigger:** fan-out lag, or an account past ~10k followers → fetch big accounts' tweets at read time.
- **Unfollow race:** a tweet fanned out during an unfollow can still land (gone within 7 days); follow, unfollow, follow can lose the second back-fill.
- **Orphans from cross-topic order:** `tweet.deleted` before `tweet.created`, or a save / view / like racing a delete, leaves rows and counters for a dead tweet. Feed orphans expire in 7 days; the rest are hidden, never removed. **Trigger:** noticeable growth → a sweep job checking ids against the tweet service.
- **`X-User-Id` trusted blindly:** anyone who reaches port 8083 can mint views and likes, inflate counts and read any user's private liked list (audit 2026-10-06, Low). **Trigger:** anything but the gateway able to reach :8083 → Medium; the shared secret on this service too, or mTLS.
- **One static internal secret on the gateway's public port, no rotation.** **Trigger:** any deployment → a private port or mTLS for `/internal/**`, and rotation.
- **No rate limiting** on feed reads, the details read, saves, likes (each a tweet-service call and an INFO line) or view reports (up to 50 new rows). **Trigger:** abuse, or a second instance.
- **Growth:** view rows kept forever; saved tweets and likes uncapped per user; a hot counter row on a viral tweet; `tweet.deleted` deletes a tweet's likes, saved rows and views in one transaction. **Trigger:** table size, latency or a slow `tweet.deleted` → HyperLogLog for views, caps, sharded counters, batched deletes.
- **The author's own views and likes count.** **Trigger:** a complaint → skip the author.
- **No `tweet.liked` event, no "who liked this" list, only your own liked list.** **Trigger:** a notification consumer → an outbox here; a likers page → index `(tweet_id, liked_at, user_id)`; profile pages → `GET /api/v1/users/{id}/likes` after deciding whether likes are public.
- **A forged `tweet.deleted` wipes the tweet's likes and counter** (audit 2026-10-06, Info); **`tweet.created` takes any `createdAt`,** so a far-future time pins the entry atop feeds. Both need write access to the topic. **Trigger:** a second producer or a non-loopback broker → Kafka ACLs, confirm deletes with the tweet service, reject a `createdAt` more than a few minutes ahead.
- **The details read's `404` body tells a gone tweet from a disabled author** (audit 2026-10-07, Info). **Trigger:** private tweets or accounts → one `404` body for both.
- **Counts on screen are a snapshot,** and the count and `likedByMe` come from two queries (a like between them can show `likes: 0, likedByMe: true` once; the UI never shows below `0`). **Trigger:** stale-count complaints → re-read counts for visible posts. The two-query part is **permanent**.
- **Start order:** a listener started before the producer auto-creates its topic with 1 partition.
- **DLT names are `<topic>-dlt`,** except `user.followed-timeline-dlt` (the mail service owns `user.followed-dlt`); **no DLT replay tool.** **Trigger:** a second consumer of another of these topics → a service-specific name; the first dead-lettered record someone wants processed → a replay tool.
- **Scheduled retention runs on every instance.** **Trigger:** a second instance → ShedLock.
- **`ddl-auto=update`, no migrations.** **Trigger:** a second environment or the first destructive change → Flyway.
- **Rejected `/internal/**` calls log one WARN line each.** **Trigger:** log volume from probing → rate limit or sample.
- **A client's `X-Internal-Secret` is forwarded by the tweet and feed routes;** ignored downstream. **Trigger:** a downstream starts reading it → strip it in `CallerIdentityFilters`.
- **Dev compose has fixed credentials** (bound to `127.0.0.1`). **Trigger:** any shared environment.
- **Stale `views` field in existing dev tweet documents.** **Permanent** unless someone wants it gone.
