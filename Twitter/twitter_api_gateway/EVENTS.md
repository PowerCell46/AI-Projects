# Events produced by twitter_api_gateway

Delivery is **at-least-once**: a crash between the Kafka ack and the outbox row delete publishes the row
twice. Consumers dedupe on `eventId`. Values are JSON strings with no type headers. JSON property order is
not part of the contract.

## `user.confirmation-requested`

- **Topic:** `user.confirmation-requested` (`app.kafka.user-confirmation-requested.name`), 3 partitions.
- **Key:** `userId` (UUID string). All events for one user land on one partition, in order.
- **Emitted:** on register, and on every resend that isn't throttled by the cooldown.

```json
{
  "eventId": "0b9c6f0e-6d0e-4c53-9d3a-3f6a3d9b1e11",
  "userId": "2f1c1c7e-5b7a-4f0e-8f7e-0c7d2c6f9a10",
  "email": "user@example.com",
  "username": "some_user",
  "confirmationUrl": "http://localhost:5173/confirm?token=<43-char base64url>",
  "expiresAt": "2026-09-30T12:00:00Z"
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `eventId` | UUID | Fresh per event. The dedupe key. |
| `userId` | UUID | The account to confirm. Same as the record key. |
| `email` | string | Recipient, lowercased. |
| `username` | string | For the greeting. |
| `confirmationUrl` | string | Ready to put in the email. Carries the raw token, which exists nowhere else. |
| `expiresAt` | ISO-8601 instant | When the token stops working. |

**Sensitivity:** the event carries the email address and a live confirmation token in plaintext. Never log the
payload.

## `user.followed`

- **Topic:** `user.followed` (`app.kafka.user-followed.name`), 3 partitions.
- **Key:** `followeeId` (UUID string), the user being followed and the mail recipient. All events for one recipient land on one partition, in order.
- **Emitted:** when a follow row is newly inserted. A repeated follow and an unfollow emit nothing.

```json
{
  "eventId": "5d1e0c2a-8f3b-4c1e-9a52-7b6e4d0c3f21",
  "followerId": "2f1c1c7e-5b7a-4f0e-8f7e-0c7d2c6f9a10",
  "followeeId": "9a7b3c1d-4e2f-4a60-b8d1-1c5e7f0a2b34",
  "occurredAt": "2026-10-01T09:30:00Z",
  "followeeEmail": "bob@example.com",
  "followerUsername": "ana",
  "followeeUsername": "bob"
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `eventId` | UUID | Fresh per event. The dedupe key. |
| `followerId` | UUID | Who followed. |
| `followeeId` | UUID | Who was followed. Same as the record key. |
| `occurredAt` | ISO-8601 instant | When the follow was stored. |
| `followeeEmail` | string | Recipient, lowercased. |
| `followerUsername` | string | For the mail text ("ana followed you"). |
| `followeeUsername` | string | For the greeting. |

**Sensitivity:** the event carries the followee's email address in plaintext. Never log the payload.

## `user.unfollowed`

- **Topic:** `user.unfollowed` (`app.kafka.user-unfollowed.name`), 3 partitions.
- **Key:** `followerId` (UUID string), the user whose feed changes. All events for one follower land on one partition, in order.
- **Emitted:** when an unfollow actually removes a follow row, in the same transaction as the removal. An unfollow of someone not followed, and a repeated unfollow, emit nothing. A follow emits `user.followed` only.

```json
{
  "eventId": "c3e4a1b2-6d5f-4e7a-8b90-2a1d3c4e5f60",
  "followerId": "2f1c1c7e-5b7a-4f0e-8f7e-0c7d2c6f9a10",
  "followeeId": "9a7b3c1d-4e2f-4a60-b8d1-1c5e7f0a2b34",
  "occurredAt": "2026-10-03T09:30:00Z"
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `eventId` | UUID | Fresh per event. The dedupe key. |
| `followerId` | UUID | Who unfollowed. Same as the record key. |
| `followeeId` | UUID | Who was unfollowed. |
| `occurredAt` | ISO-8601 instant | When the follow row was removed. A consumer drops only what the followee posted at or before this time. |

No email or username is carried.

