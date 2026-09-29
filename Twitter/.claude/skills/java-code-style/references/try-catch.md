# Try/catch formatting

Leave exactly one blank line before the closing brace of a `try` block, separating the try body from
the `catch`.

```java
// flag
try {
    redisTemplate
            .opsForValue()
            .set(code, originalUrl, ttl);
} catch (DataAccessException e) {
    log.warn("Redis unavailable while caching code '{}'; continuing without cache.", code, e);
}

// prefer
try {
    redisTemplate
            .opsForValue()
            .set(code, originalUrl, ttl);

} catch (DataAccessException e) {
    log.warn("Redis unavailable while caching code '{}'; continuing without cache.", code, e);
}
```
