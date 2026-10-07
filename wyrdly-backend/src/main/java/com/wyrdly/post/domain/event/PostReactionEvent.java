package com.wyrdly.post.domain.event;

import com.wyrdly.post.domain.model.ReactionType;

/**
 * Fired by the application layer when a user successfully adds or changes a reaction on a post.
 * Removed reactions do NOT produce this event. The event is the input to the Web Push pipeline
 * (HU11) and any future notification surface.
 *
 * <p>{@code postAuthorId} is resolved at fire time by the service so listeners do not need to
 * re-query the graph.
 */
public record PostReactionEvent(
    String postId, String postAuthorId, String reactorId, ReactionType reactionType) {}
