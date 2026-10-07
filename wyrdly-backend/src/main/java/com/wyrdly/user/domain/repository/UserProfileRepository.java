package com.wyrdly.user.domain.repository;

import com.wyrdly.user.domain.model.UserProfile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface UserProfileRepository {

  Optional<UserProfile> findProfileByUsername(String username, String viewerId);

  Optional<UserProfile> updateProfile(String userId, String fullName, String bio, String avatarUrl);

  void followUser(String followerId, String followingId);

  void unfollowUser(String followerId, String followingId);

  boolean isFollowing(String followerId, String followingId);

  void validateUserExists(String userId);

  /**
   * Return the users who follow {@code userId}, paginated. The returned records are bare summaries
   * suitable for follower / following lists. If {@code viewerId} is non-null, each row carries a
   * boolean indicating whether the viewer also follows that user back.
   */
  List<FollowerSummary> findFollowers(String userId, String viewerId, int page, int pageSize);

  /**
   * Return the users that {@code userId} follows, paginated. Mirrors {@link #findFollowers} for the
   * other direction.
   */
  List<FollowerSummary> findFollowing(String userId, String viewerId, int page, int pageSize);

  /**
   * Batch lookup of bare user summaries by id. Returns a map keyed by user id; ids that do not
   * resolve are omitted from the result. Used by other bounded contexts (e.g. notifications) to
   * enrich their projections without N+1 queries.
   */
  Map<String, FollowerSummary> findProfileSummariesByIds(Set<String> userIds);

  record FollowerSummary(
      String id, String username, String fullName, String avatarUrl, boolean isFollowing) {}
}
