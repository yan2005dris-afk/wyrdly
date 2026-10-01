package com.yaga.chat.infrastructure.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RestApiRateLimiter {

  private static final Logger LOGGER =
      Logger.getLogger(RestApiRateLimiter.class.getName());

  private final Map<String, TokenBucket> historyBuckets = new ConcurrentHashMap<>();

  @ConfigProperty(name = "yaga.rate-limit.history.requests-per-minute", defaultValue = "60")
  int requestsPerMinute;

  public boolean allowHistoryRequest(String userId) {
    int requestsPerSecond = Math.max(1, requestsPerMinute / 60);
    TokenBucket bucket =
        historyBuckets.computeIfAbsent(
            userId, k -> new TokenBucket(requestsPerSecond, 1000L));

    boolean allowed = bucket.tryConsume();
    if (!allowed) {
      LOGGER.warning(
          "Rate limit exceeded for user: "
              + userId
              + " (limit: "
              + requestsPerMinute
              + " req/min)");
    }
    return allowed;
  }

  private static class TokenBucket {
    private final int capacity;
    private final long refillIntervalMs;
    private int tokens;
    private long lastRefillTime;

    TokenBucket(int capacity, long refillIntervalMs) {
      this.capacity = capacity;
      this.refillIntervalMs = refillIntervalMs;
      this.tokens = capacity;
      this.lastRefillTime = System.currentTimeMillis();
    }

    synchronized boolean tryConsume() {
      refill();
      if (tokens > 0) {
        tokens--;
        return true;
      }
      return false;
    }

    private void refill() {
      long now = System.currentTimeMillis();
      long elapsed = now - lastRefillTime;
      if (elapsed >= refillIntervalMs) {
        tokens = capacity;
        lastRefillTime = now;
      }
    }
  }
}
