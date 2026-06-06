# Liked status is computed live, never snapshotted per Play

A track's liked/saved status changes over time, and the spec requires the liked-percentage stat to update immediately after a save/unsave. We therefore evaluate liked status against the user's **current** Spotify saved-tracks at view time, and deliberately **do not** store a liked flag on each `Play` record. For a viewed window we dedupe the distinct track IDs and batch them (50 per request) into "Check User's Saved Tracks".

This is intentionally *not* the obvious design (storing liked-at-play-time with each Play): that would make historical stats stale and unable to respond to save/unsave, contradicting the spec.

## Consequences
- "What was liked back when I played it" is not recoverable — we only know current liked status. The spec does not ask for the historical view.
- The liked-percentage = **distinct liked tracks ÷ distinct tracks played** over the viewed window.
