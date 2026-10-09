package com.wyrdly.post.domain.event;

import java.util.Objects;

/**
 * Fired by {@code RepostService} when a user reposts a post authored by someone else for the first
 * time (i.e. {@code changed && reposted && !actorId.equals(postAuthorId)}).
 *
 * <p>Consumed by the notifications bounded context to create in-app notifications and dispatch Web
 * Push notifications with type {@code POST_BOOST}.
 */
public record PostRepostedEvent(String postId, String postAuthorId, String actorId) {

  public PostRepostedEvent {
    Objects.requireNonNull(postId, "postId must not be null");
    Objects.requireNonNull(postAuthorId, "postAuthorId must not be null");
    Objects.requireNonNull(actorId, "actorId must not be null");
    if (postId.isBlank()) {
      throw new IllegalArgumentException("postId must not be blank");
    }
    if (postAuthorId.isBlank()) {
      throw new IllegalArgumentException("postAuthorId must not be blank");
    }
    if (actorId.isBlank()) {
      throw new IllegalArgumentException("actorId must not be blank");
    }
  }
}
