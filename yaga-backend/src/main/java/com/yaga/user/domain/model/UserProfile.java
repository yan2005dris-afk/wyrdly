package com.yaga.user.domain.model;

import java.time.Instant;

public record UserProfile(
    String id,
    String username,
    String fullName,
    String bio,
    String avatarUrl,
    long followersCount,
    long followingCount,
    boolean isFollowing,
    Instant createdAt) {}
