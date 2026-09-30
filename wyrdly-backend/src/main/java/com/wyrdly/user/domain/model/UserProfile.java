package com.wyrdly.user.domain.model;

import java.time.Instant;

public record UserProfile(
    String id,
    String username,
    String fullName,
    String bio,
    String avatarUrl,
    long followersCount,
    long followingCount,
    long postsCount,
    boolean isFollowing,
    Instant createdAt) {}