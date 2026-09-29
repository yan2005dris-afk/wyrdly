package com.wyrdly.user.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FollowActionResponse(
    @JsonProperty("message") String message,
    @JsonProperty("targetUserId") String targetUserId,
    @JsonProperty("following") boolean following) {}
