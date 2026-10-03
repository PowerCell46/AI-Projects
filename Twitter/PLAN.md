# Twitter — plans

Each service owns its own backlog:

- **`twitter_api_gateway/PLAN.md`**: the entry point and user service. Phases 1–3 (auth + email
  confirmation → profile (MinIO) → follows) done, designed via `/grill-me` 2026-09-29. Phase 4 (proxy
  `/api/v1/tweets/**` to the tweet service) planned 2026-09-30, and it starts after the tweet service's final gate.
- **`twitter_tweet_service/PLAN.md`**: tweets in Mongo (create with up to 4 images, read, edit text, delete),
  `tweet.created` / `tweet.deleted` via an outbox. Designed via `/grill-me` 2026-09-30. Built 2026-09-30 (steps 1–10 done); gateway
  phase 4 may start.
- **`frontend/PLAN.md`**: the SPA, starting with the "Hadal Descent" auth flow (login, register, `/confirm`,
  `/resend`, placeholder `/feed`) from `frontend/AuthenticationViewsDesigns.md`. Designed via `/grill-me`
  2026-10-01; built 2026-10-01 (all 12 steps done: unit, component and Playwright e2e tests green).
- **`twitter_mail_service/PLAN.md`**: sends the emails over SMTP from Kafka, deduped in Redis. Designed via
  `/grill-me` 2026-10-01. Phase 1 (the confirmation email, `user.confirmation-requested`; steps 1–9) built
  2026-10-01, including the Playwright e2e that reads the link from the real email. Phase 2 (the follow email,
  `user.followed`; steps 10–13) built 2026-10-02: the gateway's `user.followed` change is committed (`85019be`).
- **`twitter_timeline_service/PLAN.md`**: the feed (fan-out on `tweet.created`, 7-day retention), saved tweets and
  unique views, in Postgres. Designed via `/grill-me` 2026-10-03; three phases, phase 1 (the feed) built 2026-10-03. Its plan also holds
  the gateway steps (internal endpoints, `user.unfollowed`, routes) and the tweet-service steps (internal batch
  read, `views` removed).

---
next step:

We must come up with a good service name, news_feed is 1/3 of the service functionality

twitter_news_feed_service:
- we consume the topics from the tweet_service for creating and deleting tweets:
- we have to be connected to the api_gateway_db, fetch the users that follow the tweets creator and create the following entity entries:
- we also have to be connected to the tweets_db when fetching the feed to fetch the tweets themselves

class CommonEntity:
  probably the same as in the other services

// exmaple class name and fields, left for discussion
class NewsFeedItem extends CommonEntity:
	UUID user_id;
	UUID tweet_id;
	boolean hasBeenSeen = false;

for the consumer of a deleted tweet, we have to delete all entries with the tweet_id (so the followers won't see it)

we will need a get endpoint for an authenticated user to fetch his feed. We will get the latest entries (sort by created_at or updated_at) and mark them as hasBeenSeen == true. We will have a cron job that cleans up hasBeenSeen tweets (for example every half and hour) or delete them directly - the idea of the boolean is that we will have an index on user_id and we don't rebalance the tree when making a get request. The index could also be on user_id and hasBeenSeen (if it will make it better and this makes sense)

Another thing worth discussing is Views. We can directly consider the current GET operation tweets as viewed, or we could do something in the frontend, when a person scrolls to a given tweet to make a request. This should be discussed, but i imagine a similar entity to the SavedTweets; but what needs to happen for sure is to remove  private long views; from Tweet_service -> documents/Tweet.java, because it doesn't make sense to be there.

||||||

SavedTweets: (Potentially could be a separate service, but i think it very similar to news feed and see a logic to be here)
- POST GET DELETE
- question that needs an answer: in which db will we keep this? can we have a third one?
POST endpoint, where a user likes a given tweet
class SavedTweet extends CommonEntity:
  user_id
  tweet_id
index on the user_id
first we should check if the tweet_id exists then if the tweet isn't already saved.
Get endpoint: pageable, like the fetch tweet fetch the saved tweets
DELETE endpoint: unsave a tweet: again we need to check if the tweet_id exists then if it's saved at all.


||||
This is for future implementation NOT NOW: (Again it could be located here, because it has similar logic, but this makes more sense to be a separate service)
Liked tweets:
- again we will have a consumer, the tweet service will produce and we will consume here
for topics like.tweet and unlike.tweet
LikedTweets:
