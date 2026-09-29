package com.yaga.user.domain.model;

public record GraphSuggestion(
    String id,
    String username,
    String fullName,
    String avatarUrl,
    long mutualConnectionsCount,
    boolean isFollowing) {}
