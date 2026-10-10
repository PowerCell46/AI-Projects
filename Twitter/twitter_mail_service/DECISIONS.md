# Decisions

Non-obvious calls made *during* implementation - the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Validate and claim/send/mark live in two shared services, not in each notification service

`MailEventValidationService` (bean-validate, log, throw `InvalidMailEventException`) and `MailDispatchService`
(claim, send, mark sent; release and rethrow on a failed send) were private methods of the confirmation service.
The follow service needs the same, so both moved out and each `...NotificationService` now only builds its key,
subject and `OutgoingMail`. Chosen over copying about 50 lines into the follow service, which would let the two
claim/release paths drift. The callers pass a `logLabel` carrying ids, so a shared log line never names the
address or a token. The confirmation service's unit test only changed its `setUp` wiring; no assertion moved.

## A follow event older than the follow window is not mailed (2026-10-10)

`UserFollowedNotificationServiceImpl` skips an event whose `occurredAt` is before `now - app.mail-inbox.follow-window`,
with an info log and no inbox call. The pair's sent marker lives for one window, so an older follow may have lost its
marker and a replay (a new consumer group, expired offsets, a dead-letter retry) would mail it a second time. Chosen
over a separate max-age property because the window is exactly the marker's lifetime: every event that gets through
is younger than a marker set when it was first handled. An event from the future (clock skew) is processed. The
confirmation email needed no change, its link expiry already skips old events.

## The claim TTL is 2 minutes, under the retry window (2026-10-10)

`app.mail-inbox.claim-ttl` went from 5m to 2m, and the constructor refuses a zero or negative value. The claim has two
bounds: longer than a send can take (three 10 s SMTP timeouts), and shorter than the whole retry window (2 s doubling
to 60 s over 8 retries is 242 s). After a hard kill mid-send (SIGKILL, out of memory) the claim is never released, so
at 5m every attempt of the redelivered record found it held and the email went to the dead-letter topic. At 2m the
claim has expired by the attempt 122 s in, so the last three attempts can send it. Chosen over a startup check that
the TTL is under the retry window: the test profile shrinks the retries to about half a second, so such a check would
force a sub-second claim there. The relation is stated next to the property, and the integration test pins the 2m
default. The mail service also gets `stop_grace_period: 30s` in compose, so a normal stop lets an in-flight send
finish instead of being killed after the default 10 s.

## Mail settings are checked at startup, each where it is used (2026-10-10)

`APP_BASE_URL` lost its `http://localhost:5173` default: a deployment that forgot it mailed localhost links. The follow
renderer now requires an absolute http(s) URL with no query or fragment (`BaseUrls`), trims a trailing slash as before
and logs a warning for plain http outside localhost. `MailDeliveryServiceImpl` parses `MAIL_FROM` with
`InternetAddress` (strict, then `validate()`), since a typo failed every send as a permanent address error.
`SmtpCredentialsValidator` refuses to start with `MAIL_SMTP_AUTH` on (the default) and a blank `MAIL_USERNAME` or
`MAIL_PASSWORD`, which used to be an authentication failure, classed as transient, so each email retried for 242 s and
dead-lettered. None of the failure messages contains the value: a wrong sender or password is exactly what gets pasted
into the wrong variable. Chosen over one `@ConfigurationProperties` class for all mail settings, which would move
`from`, `zone` and the base URL out of the beans that use them for no gain; each bean now fails on its own input, like
the two `Durations` checks. The validator is a `@Component` with no interface and nothing injecting it, like the
renderers: it exists to fail the context. The base compose file now passes
`APP_BASE_URL` (default `http://localhost:5173`), the e2e compose file passes its own origin, and the prod overlay
already took it from `PUBLIC_URL`. A mail service run from the IDE needs `APP_BASE_URL` in `.env`.

