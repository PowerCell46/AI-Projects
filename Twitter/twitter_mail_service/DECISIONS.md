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
