package com.yaga.post.application.dto;

import java.time.Instant;
import java.util.Map;

public record PostResponse(
    String id,
    String content,
    String mediaUrl,
    Instant createdAt,
    AuthorDto author,
    Map<String, Integer> reactionCounts,
    String userReaction) {

  public record AuthorDto(String id, String username, String fullName, String avatarUrl) {}
}
