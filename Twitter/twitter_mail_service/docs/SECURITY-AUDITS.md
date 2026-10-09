# Security audits

Last updated: 2026-10-09

The `exploit-hunter` audits of the mail service, one section per phase, condensed from the original per-audit reports
(dates are the audit dates). Each "accepted" item is listed in `PLAN.md` under its accepted gaps, with a trigger.

| Phase | Audited | Scope | Result |
| --- | --- | --- | --- |
| 1 | 2026-10-01 | Confirmation-email path: consumer, validation, Redis inbox, rendering, SMTP, DLT, health, compose, Dockerfile | 4 fixed (1 Medium, 3 Low), 3 accepted (2 Low, 1 Info) |
| 2 | 2026-10-02 | Follow-email path: consumer, pair-keyed window, renderer, shared dispatch, second DLT | 1 Low fixed, 2 Medium, 1 Low and 1 Info accepted |

The only untrusted-input entry point is the Kafka topics; the service has no HTTP surface beyond
`GET /actuator/health`.

## Phase 1: confirmation email

- **STARTTLS could be silently downgraded and the server identity was not checked (Medium), fixed.**
  `mail.smtp.starttls.enable` only makes JavaMail *try* STARTTLS; if the EHLO reply leaves it out, `AUTH` (the Gmail
  app password) and every body (confirmation links) went in plaintext, and `ssl.checkserveridentity` defaulted to
  `false`. `starttls.required` now follows `starttls.enable` and `checkserveridentity=true`; plaintext SMTP (Mailpit)
  works only by turning STARTTLS off on purpose. Verified live: against a server without STARTTLS nothing is
  delivered, and the record is retried then dead-lettered. Covered by `MailTlsConfigurationIntegrationTest`.
- **The e2e Mailpit API was published on every interface (Low), fixed.** The unauthenticated UI and API exposed live
  confirmation links to the LAN. Now `127.0.0.1:8125:8025`, and the fixtures call `127.0.0.1` so a resolver preferring
  `::1` can't miss the loopback-only binding.
- **Event validation was looser than the gateway's (Low), fixed.** Hibernate's `@Email` accepts quoted local parts,
  address literals, `a@localhost` and `a%b@example.com`, which the gateway's `@Pattern` rejects. Header injection was
  never possible (CR/LF, commas, display names and angle brackets are rejected here and again in delivery). The
  gateway's `@Pattern` now sits next to `@Email`, so both services accept the same addresses
  (`UserConfirmationRequestedEventDTOValidationTest`).
- **Invalid records were dropped without a log line (Low), fixed.** A malformed or invalid record went straight to the
  DLT and only a permanent SMTP rejection logged. The notification service now logs a WARN with `eventId`, `userId`
  and the violated field names and messages, never the values (a test checks the address and token stay out). A
  malformed-JSON record has no id to log, so it shows only in the DLT.
- **`/actuator/health` shows details to anyone who reaches port 8082 (Low), accepted.** `show-details=always` returns
  the working directory, free disk and the Redis version; no compose file publishes 8082. Trigger: reachable from
  outside the internal network → `when-authorized` with a security starter, or bind the management port to loopback.
- **Fixed dev and e2e credentials and floating base-image tags (Low), accepted.** the e2e compose ships a fixed
  Redis password (`twitter-e2e-redis-secret`), `Dockerfile` uses `eclipse-temurin:25-jdk` / `25-jre`, and the e2e Postgres is published on
  `5452:5432` on every interface (a leftover from before this service). Same posture as the gateway and tweet
  service. Trigger: any shared environment → generated secrets, pinned digests, no published e2e Postgres port.
- **A deserialization failure carries the raw payload in a Kafka header (Info), accepted.** Spring Kafka stores the
  bytes and the Jackson message in `springDeserializerExceptionValue`; the recoverer publishes the original bytes to
  the DLT anyway, so there is no extra exposure, and no log line carries it (the full `mvn verify` log was grepped for
  `@example.com` and `token=`). The DLT is internal and must hold the payload to be replayable.
- **Clean:** logs carry only `eventId`, `userId`, the exception class and the SMTP code, and the delivery exceptions
  have no cause (a test asserts a recipient never reaches their message); the subject is a constant with CR/LF
  stripped; every HTML value goes through `HtmlUtils.htmlEscape` in one pass; Redis keys are `mail:confirmation:` plus
  a typed `UUID` and the release script takes `KEYS`/`ARGV`; Redis and Mailpit bind `127.0.0.1`, Redis needs a
  password, `.env` is git-ignored, the container is non-root; `email` ≤ 254, `confirmationUrl` ≤ 2048, no regex over
  free text. Dependency CVEs were not scanned (every version comes from the Spring Boot 4.1.1 BOM).

## Phase 2: follow email

- **One account can make the service send a follow email to every user, with no volume cap (Medium), accepted.** The
  24h window stops a repeat for one pair, nothing else: an account that follows N users sends N emails from the single
  Gmail sender, and follow emails share the daily quota (about 500 recipients) with confirmation emails, so once it is
  spent new sign-ups get no mail. Registration is unthrottled at the gateway. The cap belongs on the gateway's follow
  endpoint (its "no rate limiting" gap). Trigger: abuse, or the quota is hit.
- **A failed send leaves no window (Low), accepted.** The window is written only after a successful send, so a
  recipient whose mailbox answers a permanent `550` is retried on every new follow event: a follow/unfollow loop from
  a second account costs one SMTP attempt, one bounce and one DLT record each, and bounces hurt sender reputation.
  Trigger: bounce complaints → a short negative window after a permanent failure.
- **A recipient that answers with a temporary SMTP error stalls its partition for about 4 minutes per event
  (Medium), accepted.** Blocking retries (2s doubling to 60s, 8 retries) hold the partition 242s. One account on a
  domain that always answers `4xx` holds the partition its `followeeId` hashes to, and a follow/unfollow stream keeps
  it blocked for everyone else on it. Same root as the "blocking retries" gap. Trigger: latency complaints →
  `@RetryableTopic`.
- **A zero or negative `FOLLOW_MAIL_WINDOW` silently turned the 24h dedupe into a 5-minute one (Low), fixed.** Redis
  refuses a zero expiry, the dispatch service swallowed the failure, and only the claim key's 5m TTL remained.
  Both notification services now throw `IllegalStateException` at startup through `Durations.requirePositive`, for
  this window and for `CONFIRMATION_MAIL_SENT_TTL`, which had the same hole (a `Constructor` test in each).
- **The `user.followed` topic and its DLT hold the social graph and the followee's email in plaintext (Info),
  accepted.** Kafka has no TLS or SASL; the gateway's existing gap, restated. Trigger: Kafka reachable beyond a
  private network.
- **Clean:** the subject holds a username matching `^[A-Za-z0-9_]{3,15}$` over the whole value (25 validation cases,
  CR/LF stripped again in delivery); both usernames and the feed URL are escaped in the HTML part and the only link is
  `APP_BASE_URL` + `/feed`, from configuration; the email carries only public and the recipient's own data; the Redis
  key is two typed UUIDs and the value is `SENT`, so no collision with another pair or a `mail:confirmation:` key; the
  claim is one atomic `SET NX` and the pair key is directional; the follow logs hold ids, the violated field and the
  exception class (0 lines with an address or `token=` in the full log); the DTO caps email at 254 and usernames at 15.
