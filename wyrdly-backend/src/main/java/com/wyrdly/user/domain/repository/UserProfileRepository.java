package com.wyrdly.user.domain.repository;

import com.wyrdly.user.domain.model.UserProfile;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {

  Optional<UserProfile> findProfileByUsername(String username, String viewerId);

  Optional<UserProfile> updateProfile(String userId, String fullName, String bio, String avatarUrl);

  void followUser(String followerId, String followingId);

  void unfollowUser(String followerId, String followingId);

  boolean isFollowing(String followerId, String followingId);

  void validateUserExists(String userId);

  /**
   * Return the users who follow {@code userId}, paginated. The returned
   * records are bare summaries suitable for follower / following lists.
   * If {@code viewerId} is non-null, each row carries a boolean
   * indicating whether the viewer also follows that user back.
   */
  List<FollowerSummary> findFollowers(String userId, String viewerId, int page, int pageSize);

  /**
   * Return the users that {@code userId} follows, paginated. Mirrors
   * {@link #findFollowers} for the other direction.
   */
  List<FollowerSummary> findFollowing(String userId, String viewerId, int page, int pageSize);

  record FollowerSummary(
      String id, String username, String fullName, String avatarUrl, boolean isFollowing) {}
}