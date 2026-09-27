package com.yaga.user.application.dto;

import com.yaga.user.domain.model.UserProfile;
import java.time.Instant;

public record UserProfileResponse(
    String id,
    String username,
    String fullName,
    String bio,
    String avatarUrl,
    long followersCount,
    long followingCount,
    boolean isFollowing,
    Instant createdAt) {

  public static UserProfileResponse fromDomain(UserProfile profile) {
    return new UserProfileResponse(
        profile.id(),
        profile.username(),
        profile.fullName(),
        profile.bio(),
        profile.avatarUrl(),
        profile.followersCount(),
        profile.followingCount(),
        profile.isFollowing(),
        profile.createdAt());
  }
}
