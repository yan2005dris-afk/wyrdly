package com.wyrdly.user.domain.repository;

import com.wyrdly.user.domain.model.UserProfile;
import java.util.Optional;

public interface UserProfileRepository {

  Optional<UserProfile> findProfileByUsername(String username, String viewerId);

  Optional<UserProfile> updateProfile(String userId, String fullName, String bio, String avatarUrl);

  void followUser(String followerId, String followingId);

  void unfollowUser(String followerId, String followingId);

  boolean isFollowing(String followerId, String followingId);

  void validateUserExists(String userId);
}
