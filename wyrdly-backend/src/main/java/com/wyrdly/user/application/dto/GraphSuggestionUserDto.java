package com.wyrdly.user.application.dto;

import com.wyrdly.user.domain.model.GraphSuggestion;

public record GraphSuggestionUserDto(
    String id,
    String username,
    String fullName,
    String avatarUrl,
    String mutualConnectionSnippet,
    boolean isFollowing) {

  public static GraphSuggestionUserDto fromDomain(GraphSuggestion suggestion) {
    String snippet =
        suggestion.mutualConnectionsCount() == 1
            ? "Followed by 1 person"
            : String.format("Followed by %d people", suggestion.mutualConnectionsCount());

    return new GraphSuggestionUserDto(
        suggestion.id(),
        suggestion.username(),
        suggestion.fullName(),
        suggestion.avatarUrl(),
        snippet,
        suggestion.isFollowing());
  }
}
