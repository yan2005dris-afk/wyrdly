package com.wyrdly.user.domain.model;

import java.time.Instant;

public record FollowRelation(String followerId, String followingId, Instant createdAt) {}
