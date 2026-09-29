package com.yaga.user.application.dto;

public record UserSearchResultDto(
    String id,
    String username,
    String fullName,
    String avatarUrl,
    String bio,
    boolean isFollowing,
    String mutualConnectionSnippet) {}