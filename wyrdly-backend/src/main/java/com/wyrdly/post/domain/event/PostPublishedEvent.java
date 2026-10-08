package com.wyrdly.post.domain.event;

import java.time.Instant;

/**
 * Fired by the application layer once a post has been committed to the graph. The event is the
 * input to the Web Push fan-out (HU11 — {@code NEW_POST_FROM_FOLLOWED}) and any future notification
 * surface.
 *
 * <p>{@code authorUsername} is resolved at fire time by the service (the author is already loaded
 * to build the response) so listeners do not need to re-query the graph.
 */
public record PostPublishedEvent(
    String postId, String authorId, String authorUsername, String content, Instant createdAt) {}
