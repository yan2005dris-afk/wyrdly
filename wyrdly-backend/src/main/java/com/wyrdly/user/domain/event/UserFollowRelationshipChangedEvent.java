package com.wyrdly.user.domain.event;

public record UserFollowRelationshipChangedEvent(
    String followerId, String targetUserId, boolean followed) {}
