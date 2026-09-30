# Events produced by twitter_tweet_service

Delivery is **at-least-once**: a crash between the Kafka ack and the outbox message delete publishes the
message twice. Consumers dedupe on `eventId`. Values are JSON strings with no type headers. JSON property order
is not part of the contract. Events are written to the outbox in the same Mongo transaction as the tweet
change, so an event exists exactly when its change does, and the job sends them within a few seconds.

Both topics are keyed by `tweetId`, so all events of one tweet land on one partition, in order. There is no
`tweet.updated` event: nothing consumes tweet text yet.

## `tweet.created`

- **Topic:** `tweet.created` (`app.kafka.tweet-created.name`), 3 partitions.
- **Key:** `tweetId` (UUID string).
- **Emitted:** when a tweet is created.

```json
{
  "eventId": "0b9c6f0e-6d0e-4c53-9d3a-3f6a3d9b1e11",
  "tweetId": "5d3c2f3e-2f8b-4c59-a1a5-7d0b6a1f2c44",
  "authorId": "2f1c1c7e-5b7a-4f0e-8f7e-0c7d2c6f9a10",
  "content": "hello",
  "createdAt": "2026-09-30T12:00:00.123Z",
  "imageIds": ["9a4f0c1e-3b7d-4a52-8e61-1c2d3e4f5a6b"]
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `eventId` | UUID | Fresh per event. The dedupe key. |
| `tweetId` | UUID | The new tweet. Same as the record key. |
| `authorId` | UUID | The caller's `X-User-Id`. |
| `content` | string | The trimmed text. `""` when the tweet has only images. |
| `createdAt` | ISO-8601 instant | Millisecond precision. |
| `imageIds` | UUID[] | The image ids in upload order, `[]` when there are none. Not the storage keys. |

**Sensitivity:** `content` is user text. Never log the payload.

## `tweet.deleted`

- **Topic:** `tweet.deleted` (`app.kafka.tweet-deleted.name`), 3 partitions.
- **Key:** `tweetId` (UUID string).
- **Emitted:** once, when the author deletes a tweet. A second delete answers 404 and emits nothing.

```json
{
  "eventId": "7c1e0f52-8d3a-4b6e-9f10-2a3b4c5d6e7f",
  "tweetId": "5d3c2f3e-2f8b-4c59-a1a5-7d0b6a1f2c44",
  "authorId": "2f1c1c7e-5b7a-4f0e-8f7e-0c7d2c6f9a10",
  "deletedAt": "2026-09-30T13:00:00.456Z"
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `eventId` | UUID | Fresh per event. The dedupe key. |
| `tweetId` | UUID | The deleted tweet. Same as the record key. |
| `authorId` | UUID | Who deleted it, always the tweet's author. |
| `deletedAt` | ISO-8601 instant | Millisecond precision. |

A consumer can see `tweet.deleted` before `tweet.created` has been processed only if it reads the two topics
independently: ordering is guaranteed per topic and key, not across topics.
