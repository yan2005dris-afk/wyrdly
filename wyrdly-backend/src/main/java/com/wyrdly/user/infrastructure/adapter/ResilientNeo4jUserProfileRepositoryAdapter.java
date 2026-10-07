package com.wyrdly.user.infrastructure.adapter;

import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.model.UserProfile;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.infrastructure.qualifier.Neo4jDirect;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@ApplicationScoped
@ResilientNeo4j
public class ResilientNeo4jUserProfileRepositoryAdapter implements UserProfileRepository {

  private static final Logger LOGGER =
      Logger.getLogger(ResilientNeo4jUserProfileRepositoryAdapter.class.getName());

  private final UserProfileRepository delegate;
  private final Counter neo4jErrors;
  private final Timer neo4jLatency;

  @Inject
  public ResilientNeo4jUserProfileRepositoryAdapter(
      @Neo4jDirect UserProfileRepository delegate, MeterRegistry meterRegistry) {
    this.delegate = delegate;
    this.neo4jErrors = Counter.builder("neo4j.errors").register(meterRegistry);
    this.neo4jLatency = Timer.builder("neo4j.latency").register(meterRegistry);
  }

  @Override
  @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 5, delay = 3000)
  @Retry(maxRetries = 2, delay = 100)
  @Timeout(3000)
  @Fallback(fallbackMethod = "findProfileByUsernameFallback")
  public Optional<UserProfile> findProfileByUsername(String username, String viewerId) {
    return neo4jLatency.record(() -> delegate.findProfileByUsername(username, viewerId));
  }

  public Optional<UserProfile> findProfileByUsernameFallback(String username, String viewerId) {
    LOGGER.warning("CircuitBreaker OPEN - Cannot find profile: " + username);
    neo4jErrors.increment();
    return Optional.empty();
  }

  @Override
  @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 5, delay = 3000)
  @Retry(maxRetries = 2, delay = 100)
  @Timeout(3000)
  @Fallback(fallbackMethod = "updateProfileFallback")
  public Optional<UserProfile> updateProfile(
      String userId, String fullName, String bio, String avatarUrl) {
    return neo4jLatency.record(() -> delegate.updateProfile(userId, fullName, bio, avatarUrl));
  }

  public Optional<UserProfile> updateProfileFallback(
      String userId, String fullName, String bio, String avatarUrl) {
    LOGGER.warning("CircuitBreaker OPEN - Cannot update profile: " + userId);
    neo4jErrors.increment();
    return Optional.empty();
  }

  @Override
  @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 5, delay = 3000)
  @Retry(maxRetries = 2, delay = 100)
  @Timeout(3000)
  @Fallback(fallbackMethod = "followUserFallback")
  public void followUser(String followerId, String followingId) {
    neo4jLatency.record(
        () -> {
          delegate.followUser(followerId, followingId);
          return null;
        });
  }

  public void followUserFallback(String followerId, String followingId) {
    LOGGER.severe("CircuitBreaker OPEN - Cannot follow user: " + followerId + " -> " + followingId);
    neo4jErrors.increment();
    throw new RuntimeException("Database temporarily unavailable");
  }

  @Override
  @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 5, delay = 3000)
  @Retry(maxRetries = 2, delay = 100)
  @Timeout(3000)
  @Fallback(fallbackMethod = "unfollowUserFallback")
  public void unfollowUser(String followerId, String followingId) {
    neo4jLatency.record(
        () -> {
          delegate.unfollowUser(followerId, followingId);
          return null;
        });
  }

  public void unfollowUserFallback(String followerId, String followingId) {
    LOGGER.severe(
        "CircuitBreaker OPEN - Cannot unfollow user: " + followerId + " -> " + followingId);
    neo4jErrors.increment();
    throw new RuntimeException("Database temporarily unavailable");
  }

  @Override
  @CircuitBreaker(successThreshold = 3, requestVolumeThreshold = 10, delay = 5000)
  @Retry(maxRetries = 3, delay = 200)
  @Timeout(5000)
  @Fallback(fallbackMethod = "isFollowingFallback")
  public boolean isFollowing(String followerId, String followingId) {
    return neo4jLatency.record(() -> delegate.isFollowing(followerId, followingId));
  }

  public boolean isFollowingFallback(String followerId, String followingId) {
    LOGGER.warning("CircuitBreaker OPEN - Neo4j unavailable, denying access for " + followerId);
    neo4jErrors.increment();
    return false;
  }

  @Override
  public void validateUserExists(String userId) {
    try {
      neo4jLatency.record(
          () -> {
            delegate.validateUserExists(userId);
            return null;
          });
    } catch (UserProfileNotFoundException e) {
      throw e;
    } catch (Exception e) {
      neo4jErrors.increment();
      LOGGER.severe("Neo4j error validating user: " + e.getMessage());
      throw new RuntimeException("Database unavailable", e);
    }
  }

  @Override
  @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 5, delay = 3000)
  @Retry(maxRetries = 2, delay = 100)
  @Timeout(3000)
  @Fallback(fallbackMethod = "findFollowersFallback")
  public List<FollowerSummary> findFollowers(
      String userId, String viewerId, int page, int pageSize) {
    return neo4jLatency.record(() -> delegate.findFollowers(userId, viewerId, page, pageSize));
  }

  public List<FollowerSummary> findFollowersFallback(
      String userId, String viewerId, int page, int pageSize) {
    LOGGER.warning("CircuitBreaker OPEN - Cannot find followers for: " + userId);
    neo4jErrors.increment();
    return List.of();
  }

  @Override
  @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 5, delay = 3000)
  @Retry(maxRetries = 2, delay = 100)
  @Timeout(3000)
  @Fallback(fallbackMethod = "findFollowingFallback")
  public List<FollowerSummary> findFollowing(
      String userId, String viewerId, int page, int pageSize) {
    return neo4jLatency.record(() -> delegate.findFollowing(userId, viewerId, page, pageSize));
  }

  public List<FollowerSummary> findFollowingFallback(
      String userId, String viewerId, int page, int pageSize) {
    LOGGER.warning("CircuitBreaker OPEN - Cannot find following for: " + userId);
    neo4jErrors.increment();
    return List.of();
  }

  @Override
  @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 5, delay = 3000)
  @Retry(maxRetries = 2, delay = 100)
  @Timeout(3000)
  @Fallback(fallbackMethod = "findProfileSummariesByIdsFallback")
  public Map<String, FollowerSummary> findProfileSummariesByIds(Set<String> userIds) {
    return neo4jLatency.record(() -> delegate.findProfileSummariesByIds(userIds));
  }

  public Map<String, FollowerSummary> findProfileSummariesByIdsFallback(Set<String> userIds) {
    LOGGER.warning(
        "CircuitBreaker OPEN - Cannot resolve profile summaries for "
            + (userIds == null ? 0 : userIds.size())
            + " ids");
    neo4jErrors.increment();
    return Map.of();
  }
}
