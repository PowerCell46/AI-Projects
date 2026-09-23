# Missing API surface for the dashboard feed

`frontend/dashboard-feed.md` specs the signed-in topic feed (segmented All / Subscribed / Not
subscribed filter, paged cards, per-state counts). Building it against the current API hits two gaps.
Frontend work on this page is paused until these are resolved.

Ticker field (`frontend/dashboard-feed.md` card ticker, e.g. `BTC`) is **not** one of these gaps —
decided to drop it from the card UI rather than add it to the backend.

---

## Gap 1 — no way to page "Subscribed" / "Not subscribed"

`GET /api/v1/interest-topics` (topic-service, forwarded verbatim by the gateway's
`InterestTopicRoutesConfiguration`) only filters by `categoryId`. It has no concept of the caller's
subscriptions — subscriptions live in the gateway's own Postgres (`SubscriptionRepository`), not the
topic-service's.

`GET /api/v1/subscriptions` (gateway) returns the caller's full subscription list (`id`,
`interestTopicId`, `createdAt`) — no topic name/description/category, so it can't render a card by
itself.

The only id→topic lookup that exists, `POST /internal/v1/interest-topics/existing`, is service-internal
(not under `/api/v1`, not forwarded by the gateway's route config) and only echoes back which ids still
exist — it doesn't return topic DTOs.

**Net effect:** there is no endpoint that can return a paged, ordered list of full topic DTOs restricted
to (or excluding) a given set of ids. Without it, "Subscribed" and "Not subscribed" can't be paged
server-side, and per-state counts can't be trusted beyond what's already loaded.

### Proposed fix

**topic-service** — extend the internal surface with a real search, e.g.:

```
POST /internal/v1/interest-topics/search
{
  "includeIds": [UUID, ...]?,   // mutually exclusive with excludeIds
  "excludeIds": [UUID, ...]?,
  "categoryId": UUID?,
  "page": int, "size": int
}
→ Page<InterestTopicResponseDTO>
```

Gateway is the only caller (internal, not forwarded).

**gateway** — add a real (non-forwarding) endpoint that composes subscription data with the above,
since the gateway is the only service that knows the caller's subscribed ids. Something like:

```
GET /api/v1/topics/feed?filter=all|subscribed|not_subscribed&categoryId=&page=&size=
→ {
    "topics": Page<InterestTopicResponseDTO>,
    "counts": { "all": int, "subscribed": int, "notSubscribed": int }
  }
```

- `filter=all` → calls topic-service's existing public `GET /api/v1/interest-topics`.
- `filter=subscribed` → loads the caller's subscribed ids from `SubscriptionRepository`, calls
  `/internal/v1/interest-topics/search` with `includeIds`.
- `filter=not_subscribed` → same, with `excludeIds`.
- `counts.subscribed` comes straight from the gateway's own subscription count for the caller;
  `counts.all` from the topic-service; `counts.notSubscribed = all - subscribed`.

This is one workable shape, not a mandate — the important part is that *some* endpoint needs to let the
gateway ask "give me a page of topics restricted to/excluding this id set," and *some* endpoint needs to
hand back counts that don't require the frontend to load the full catalog.

---

## Gap 2 — (none currently blocking) ticker field

Noted for completeness: `InterestTopicResponseDTO` has no `ticker`-equivalent field. Decision was to
drop the ticker from the card UI rather than add this to the backend — no action needed unless that
decision changes.
