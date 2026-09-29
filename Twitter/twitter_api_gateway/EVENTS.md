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
  "confirmationUrl": "http://localhost/confirm?token=<43-char base64url>",
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
