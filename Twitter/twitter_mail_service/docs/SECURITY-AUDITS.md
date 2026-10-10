# Security audits

Last updated: 2026-10-10

The `exploit-hunter` audits of the mail service, one section per phase, condensed from the original per-audit reports
(dates are the audit dates). Each "accepted" item is listed in `PLAN.md` under its accepted gaps, with a trigger.

| Phase | Audited | Scope | Result |
| --- | --- | --- | --- |
| 1 | 2026-10-01 | Confirmation-email path: consumer, validation, Redis inbox, rendering, SMTP, DLT, health, compose, Dockerfile | 4 findings fixed; the open ones are kept privately. |
| 2 | 2026-10-02 | Follow-email path: consumer, pair-keyed window, renderer, shared dispatch, second DLT | 1 finding fixed; the open ones are kept privately. |

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
- **Clean:** logs carry only `eventId`, `userId`, the exception class and the SMTP code, and the delivery exceptions
  have no cause (a test asserts a recipient never reaches their message); the subject is a constant with CR/LF
  stripped; every HTML value goes through `HtmlUtils.htmlEscape` in one pass; Redis keys are `mail:confirmation:` plus
  a typed `UUID` and the release script takes `KEYS`/`ARGV`; Redis and Mailpit bind `127.0.0.1`, Redis needs a
  password, `.env` is git-ignored, the container is non-root; `email` ≤ 254, `confirmationUrl` ≤ 2048, no regex over
  free text. Dependencies were scanned on 2026-10-10 (see the end of this file).

## Phase 2: follow email

- **A zero or negative `FOLLOW_MAIL_WINDOW` silently turned the 24h dedupe into a 5-minute one (Low), fixed.** Redis
  refuses a zero expiry, the dispatch service swallowed the failure, and only the claim key's 5m TTL remained.
  Both notification services now throw `IllegalStateException` at startup through `Durations.requirePositive`, for
  this window and for `CONFIRMATION_MAIL_SENT_TTL`, which had the same hole (a `Constructor` test in each).
- **Clean:** the subject holds a username matching `^[A-Za-z0-9_]{3,15}$` over the whole value (25 validation cases,
  CR/LF stripped again in delivery); both usernames and the feed URL are escaped in the HTML part and the only link is
  `APP_BASE_URL` + `/feed`, from configuration; the email carries only public and the recipient's own data; the Redis
  key is two typed UUIDs and the value is `SENT`, so no collision with another pair or a `mail:confirmation:` key; the
  claim is one atomic `SET NX` and the pair key is directional; the follow logs hold ids, the violated field and the
  exception class (0 lines with an address or `token=` in the full log); the DTO caps email at 254 and usernames at 15.

## Dependency scan (2026-10-10)

`osv-scanner` (Docker image `ghcr.io/google/osv-scanner`) over the four `pom.xml` files and both `package-lock.json`
files found 71 advisories (10 Critical, 34 High, 24 Medium, 3 Low) in `tomcat-embed-core` 11.0.24, `jackson-core` and
`jackson-databind` 2.21.5 and 3.1.5, `lz4-java` 1.10.1 and, in the tweet service, `bcprov-jdk18on` 1.84. All were fixed by
raising the versions in each `pom.xml` (Tomcat 11.0.25, Jackson 2.21.7 and 3.1.7, lz4-java 1.11.4, BouncyCastle 1.85), and
a second scan reported no issues. Reachability was not assessed. Nothing runs the scan automatically yet, so repeat it
before each deployment.
