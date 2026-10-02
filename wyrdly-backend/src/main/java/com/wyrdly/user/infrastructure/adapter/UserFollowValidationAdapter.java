package com.wyrdly.user.infrastructure.adapter;

import com.wyrdly.chat.application.port.FollowValidationPort;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class UserFollowValidationAdapter implements FollowValidationPort {

  private static final Logger LOGGER =
      Logger.getLogger(UserFollowValidationAdapter.class.getName());

  private final ConcurrentHashMap<String, CachedFollowRelation> cache = new ConcurrentHashMap<>();
  private final Counter cacheHits;
  private final Counter cacheMisses;

  @Inject @ResilientNeo4j UserProfileRepository userProfileRepository;

  @ConfigProperty(name = "wyrdly.cache.follow.ttl.seconds", defaultValue = "300")
  long cacheTtlSeconds;

  @Inject
  public UserFollowValidationAdapter(MeterRegistry meterRegistry) {
    this.cacheHits = Counter.builder("follow.cache.hits").register(meterRegistry);
    this.cacheMisses = Counter.builder("follow.cache.misses").register(meterRegistry);
  }

  public void onFollowRelationshipChanged(@Observes UserFollowRelationshipChangedEvent event) {
    invalidateCache(event.followerId(), event.targetUserId());
  }

  @Override
  public boolean areMutualFollowers(String userId1, String userId2) {
    String key = createSymmetricKey(userId1, userId2);
    long cacheTtlMs = TimeUnit.SECONDS.toMillis(cacheTtlSeconds);

    CachedFollowRelation cached = cache.get(key);
    if (cached != null && !cached.isExpired(cacheTtlMs)) {
      cacheHits.increment();
      LOGGER.fine("Follow cache HIT: " + key);
      return cached.areMutual;
    }

    cacheMisses.increment();
    boolean senderFollows = userProfileRepository.isFollowing(userId1, userId2);
    boolean recipientFollows = userProfileRepository.isFollowing(userId2, userId1);
    boolean areMutual = senderFollows && recipientFollows;

    cache.put(key, new CachedFollowRelation(areMutual, System.currentTimeMillis()));

    LOGGER.fine(
        "Follow validation: "
            + userId1
            + " <-> "
            + userId2
            + " = "
            + areMutual
            + " (cached for "
            + cacheTtlSeconds
            + "s)");
    return areMutual;
  }

  @Override
  public void invalidateCache(String userId1, String userId2) {
    String key = createSymmetricKey(userId1, userId2);
    cache.remove(key);
    LOGGER.fine("Cache invalidated for follow relation: " + userId1 + " <-> " + userId2);
  }

  private String createSymmetricKey(String userId1, String userId2) {
    String sorted1 = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
    String sorted2 = userId1.compareTo(userId2) < 0 ? userId2 : userId1;
    return sorted1 + "::" + sorted2;
  }

  private static class CachedFollowRelation {
    boolean areMutual;
    long cachedAt;

    CachedFollowRelation(boolean areMutual, long cachedAt) {
      this.areMutual = areMutual;
      this.cachedAt = cachedAt;
    }

    boolean isExpired(long cacheTtlMs) {
      return System.currentTimeMillis() - cachedAt > cacheTtlMs;
    }
  }
}
