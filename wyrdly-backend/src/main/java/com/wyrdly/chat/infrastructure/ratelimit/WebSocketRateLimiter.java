package com.wyrdly.chat.infrastructure.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class WebSocketRateLimiter {

  private static final int MESSAGES_PER_SECOND = 10;
  private static final int TYPING_EVENTS_PER_SECOND = 5;
  private static final long REFILL_INTERVAL = 1000; // 1 second in ms

  private final Map<String, TokenBucket> messageBuckets = new ConcurrentHashMap<>();
  private final Map<String, TokenBucket> typingBuckets = new ConcurrentHashMap<>();

  public boolean allowMessage(String userId) {
    return messageBuckets.computeIfAbsent(userId, k -> new TokenBucket(MESSAGES_PER_SECOND, REFILL_INTERVAL))
        .tryConsume();
  }

  public boolean allowTyping(String userId) {
    return typingBuckets.computeIfAbsent(userId, k -> new TokenBucket(TYPING_EVENTS_PER_SECOND, REFILL_INTERVAL))
        .tryConsume();
  }

  private static class TokenBucket {
    private final int capacity;
    private final long refillInterval;
    private int tokens;
    private long lastRefillTime;

    TokenBucket(int capacity, long refillInterval) {
      this.capacity = capacity;
      this.refillInterval = refillInterval;
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
      if (elapsed >= refillInterval) {
        tokens = capacity;
        lastRefillTime = now;
      }
    }
  }
}
