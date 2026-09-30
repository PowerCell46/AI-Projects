# Style discussion (open)

Proposed `java-code-style` changes waiting on a decision. Delete an entry once it is decided and applied.

## Blank line after a closing brace

**Trigger:** an agent wrote consecutive `if` blocks with no blank line between them (`detectContentType` in both
`ImageSignatureValidator`s; both already look correct in the code now).

```java
// flag
if (startsWith(header, 0, JPEG_SIGNATURE)) {
    return "image/jpeg";
}
if (startsWith(header, 0, PNG_SIGNATURE)) {
    return "image/png";
}

// prefer
if (startsWith(header, 0, JPEG_SIGNATURE)) {
    return "image/jpeg";
}

if (startsWith(header, 0, PNG_SIGNATURE)) {
    return "image/png";
}
```

**The skill today:** silent. It covers blank lines before `} catch` / `} finally` / `} else`, between fields and
between methods, but not after a block ends.

**Options:**

- **A. Narrow:** a blank line between consecutive `if` blocks only.
- **B. Broad:** a blank line after any block's closing `}` (`if`, `for`, `while`, `try`, `switch`) when another
  statement follows in the same scope. Covers A plus cases like:

  ```java
  for (...) {
      ...
  }
  members.add(member);   // B wants a blank line above
  ```

**Existing code that B would flag (21 sites, as of 2026-09-30).** A flags only the two `if` rows.

| File | Line | Statement after `}` |
| --- | --- | --- |
| gateway `controllers/FollowControllerIntegrationTest` | 783 | `if (list.equals("followers")) {` |
| gateway `controllers/FollowControllerIntegrationTest` | 789 | `members.add(member);` |
| gateway `controllers/ProfileConcurrencyIntegrationTest` | 117 | `start.countDown();` |
| gateway `controllers/ProfileConcurrencyIntegrationTest` | 123 | `return statuses;` |
| gateway `controllers/FollowConcurrencyIntegrationTest` | 165 | `start.countDown();` |
| gateway `controllers/FollowConcurrencyIntegrationTest` | 171 | `return statuses;` |
| gateway `controllers/AuthConcurrencyIntegrationTest` | 165 | `start.countDown();` |
| gateway `controllers/AuthConcurrencyIntegrationTest` | 171 | `return statuses;` |
| gateway `configurations/MinioBucketInitializer` | 34 | `minioClient.makeBucket(...)` |
| gateway `entities/User` | 127 | `if (username != null) {` |
| gateway `services/implementations/ObjectStorageServiceImpl` | 80 | `log.error(...)` |
| gateway `services/implementations/OutboxPublisherServiceImpl` | 62 | `outboxRepository.delete(row);` |
| gateway `services/implementations/OutboxPublisherServiceImpl` | 94 | `outboxRepository.save(row);` |
| gateway `services/implementations/FollowListServiceImpl` | 123 | `List<UUID> memberIds = members` |
| gateway `services/implementations/ProfilePictureServiceImpl` | 141 | `slot.write(user, null);` |
| tweet `configurations/MinioBucketInitializer` | 34 | `minioClient.makeBucket(...)` |
| tweet `utilities/CurrentUserIdArgumentResolverTest` | 87 | `throw new IllegalArgumentException(...)` |
| tweet `controllers/TweetUploadLimitsIntegrationTest` | 94 | `body.writeBytes(...)` |
| tweet `controllers/TweetControllerIntegrationTest` | 202 | `assertThat(...)` |
| tweet `controllers/TweetConcurrencyIntegrationTest` | 255 | `ready.await(...)` |
| tweet `services/implementations/TweetServiceImplTest` | 247 | `storedKeys.add(...)` |

**Open questions:**

- A or B?
- Fix the existing sites in the same change?
- `MinioBucketInitializer:34` also chains 3 calls on one line, which breaks the method-chaining rule. Fix that
  alongside?
