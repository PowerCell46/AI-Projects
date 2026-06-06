# SpotyStats

A web application that accumulates a user's Spotify listening activity over time and visualizes how their music fits into their life across days, seasons, and life phases.

## Language

**Play**:
A single listening event — one track played by the user at a specific moment. Identified by the track plus the timestamp it was played at.
_Avoid_: "scrobble", "listen", "stream"

**Listening History**:
The accumulated, persisted collection of a user's Plays that the backend builds up over time by syncing from Spotify. Because we store it ourselves, it can span seasons and life phases — unlike Spotify's source data.
_Avoid_: "recently played" (that is the Spotify *source endpoint*, not our owned, accumulated history)

**Saved Track**:
A track the user has marked as liked in Spotify, as reported by the "Check User's Saved Tracks" endpoint. Liked/unliked status is owned by Spotify and always evaluated as of *now* (never snapshotted per Play). The UI may label this "Liked".
_Avoid_: "favourite", "bookmarked"

**Liked Percentage**:
Over a viewed window, the share of distinct tracks played that are currently Saved Tracks: distinct liked tracks ÷ distinct tracks played. Computed live, so it responds immediately to save/unsave.
_Avoid_: computing it over plays rather than distinct tracks

**Spotify Account**:
The external Spotify identity a user authenticates with. Distinct from a session — see _Session_.
_Avoid_: "user account" when you mean the Spotify side

**Session**:
A server-side authenticated context for one logged-in user, referenced from the browser by an HTTP-only cookie. Holds (or points to) the Spotify tokens. Distinct from the _Spotify Account_.

**Listening Time**:
The summed full duration of a set of Plays, treating each Play as one complete listen. An approximation — Spotify does not report partial or skipped listens — and the frontend must disclose this where the metric is shown.
_Avoid_: "minutes listened" / "time spent" implying precision we do not have

**Track Count**:
The number of Plays in a set (the other selectable chart metric alongside _Listening Time_). Note: this counts play events, so repeated plays of the same track each add to the count.
_Avoid_: "songs" when you mean play events vs. distinct tracks

**Grouping**:
How Plays in the history view are bucketed in time — by day, week, or month — selectable via a toggle (default: day). Wider buckets are where seasonal/longer-term patterns surface.
_Avoid_: confusing time _Grouping_ (by date) with artist grouping in charts

**Primary Artist**:
The first credited artist on a track. In all artist-grouped charts and stats, each Play is attributed solely to its Primary Artist, so per-artist totals sum to the true overall total (featured/collaborating artists are not separately credited).
_Avoid_: attributing one Play to multiple artists
