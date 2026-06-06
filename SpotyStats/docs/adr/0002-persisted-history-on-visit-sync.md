# Persisted listening history, synced on-visit only

Spotify's "Get Recently Played" returns only the last ~50 plays and cannot reach back in time, so to support the "across seasons and life phases" goal we **persist plays in our own database** and accumulate them over time. We sync **only when the user is actively using the app** (on visit), not via background polling, as a deliberate privacy choice: the backend never acts on a user's behalf while they are offline, and we never hold/use tokens for absent users.

## Consequences
- History **starts empty** and grows from first use; it is not backfilled.
- Completeness depends on visit frequency: any plays beyond the most recent 50 that occur between two visits are **lost permanently** (Spotify will not return them). Acceptable because the audience is a small set of frequent users.
- No scheduler / background job is needed. Refresh tokens are used only to refresh access tokens *during* an active session.

## Considered Options
- **On-visit sync (chosen)** — private, simple, no scheduler; history may have gaps for sporadic users.
- **Background per-user polling (~30 min)** — only way to guarantee gap-free history, but acts on offline users' behalf and holds tokens long-term. Rejected on privacy grounds.
