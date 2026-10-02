← [Index](../00-index.md)

## Code

Real excerpts, not full file dumps — each is the part that carries the actual design decision.

**Short-code generation** — [`Base62Encoder.java`](../../src/main/java/com/aninditb/shortlink/util/Base62Encoder.java)

```java
public static String encode(long value) {
    if (value < 0) {
        throw new IllegalArgumentException("value must be non-negative: " + value);
    }
    if (value == 0) {
        return String.valueOf(ALPHABET.charAt(0));
    }

    StringBuilder sb = new StringBuilder();
    long remaining = value;
    while (remaining > 0) {
        int digit = (int) (remaining % BASE);
        sb.append(ALPHABET.charAt(digit));
        remaining /= BASE;
    }
    return sb.reverse().toString();
}
```

The auto-increment primary key is Base62-encoded into the short code — simple and collision-free by construction *for the auto-generated path in isolation*, but it couples code generation to a single writer, and it does not cross-check against already-taken custom aliases (a distinct row can be created earlier with `customAlias` equal to a future auto-generated id's Base62 encoding). The second `save()` that assigns the real code has no handler for the resulting `DataIntegrityViolationException` today — see [High-Level Design: Known gaps](../design/high-level.md#known-gaps--tech-debt).

**SSRF / private-IP rejection** — [`UrlSafetyValidator.java`](../../src/main/java/com/aninditb/shortlink/validation/UrlSafetyValidator.java)

```java
String host = uri.getHost();
if (host == null || host.isBlank()) {
    throw new InvalidUrlException("URL is missing a host: " + rawUrl);
}

if (host.equalsIgnoreCase("localhost")) {
    throw new InvalidUrlException("URL host is not allowed: " + host);
}

InetAddress[] addresses;
try {
    addresses = InetAddress.getAllByName(host);
} catch (UnknownHostException e) {
    throw new InvalidUrlException("Unable to resolve URL host: " + host);
}

for (InetAddress address : addresses) {
    if (address.isLoopbackAddress()
            || address.isSiteLocalAddress()
            || address.isLinkLocalAddress()
            || address.isAnyLocalAddress()) {
        throw new InvalidUrlException("URL host resolves to a disallowed address range: " + host);
    }
}
```

Every hostname is actually resolved (not just pattern-matched) before it's accepted — this catches DNS names that point at private IP ranges, not just literal `127.0.0.1`/`localhost`.

**Idempotency claim** — [`IdempotencyService.java`](../../src/main/java/com/aninditb/shortlink/service/IdempotencyService.java)

```java
public Optional<ShortUrlResponse> claim(String idempotencyKey, String bodyHash) {
    String redisKey = key(idempotencyKey);
    if (Boolean.TRUE.equals(redisTemplate.opsForValue()
            .setIfAbsent(redisKey, IN_PROGRESS_PREFIX + bodyHash, RESERVATION_TTL))) {
        return Optional.empty();
    }
    return awaitCompletion(redisKey, bodyHash);
}
```

A single atomic `SETNX` decides which concurrent request wins — this replaced an earlier check-then-act version that let concurrent retries create duplicate rows (fixed in [PR #104](https://github.com/AninditB/URL-shortener/pull/104)).

**Rate limiter** — [`RateLimiter.java`](../../src/main/java/com/aninditb/shortlink/service/RateLimiter.java)

```java
public boolean tryAcquire(String identity, int maxRequests) {
    long windowStart = Instant.now().getEpochSecond() / window.getSeconds();
    String key = KEY_PREFIX + identity + ":" + windowStart;

    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
        redisTemplate.expire(key, window);
    }

    return count != null && count <= maxRequests;
}
```

Fixed-window counting keyed by identity *and* the current window number — the key itself changes every window, so there's nothing to reset.

**Redirect cache-aside** — [`ShortUrlServiceImpl.java`](../../src/main/java/com/aninditb/shortlink/service/ShortUrlServiceImpl.java)

```java
public String resolve(String shortCode) {
    String cacheKey = cacheKey(shortCode);
    String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
    if (cachedUrl != null) {
        clickEventPublisher.publish(shortCode);
        return cachedUrl;
    }
    // ... miss: load from Postgres, then:
    cacheActiveUrl(entity);
    clickEventPublisher.publish(shortCode);
    return entity.getOriginalUrl();
}
```

Every write path that changes a URL's validity (`delete`, `disable`, detecting expiry on read) explicitly calls `redisTemplate.delete(cacheKey)` — the cache is invalidated on write, not just left to expire on its own TTL.

**Kafka consumer dedup** — [`EventDedupService.java`](../../src/main/java/com/aninditb/shortlink/analytics/EventDedupService.java)

```java
public boolean markProcessed(String eventId) {
    Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", ttl);
    return Boolean.TRUE.equals(firstTime);
}
```

Same atomic-claim pattern as idempotency, applied to Kafka's at-least-once delivery — a redelivered event is a no-op instead of a double-counted click.
