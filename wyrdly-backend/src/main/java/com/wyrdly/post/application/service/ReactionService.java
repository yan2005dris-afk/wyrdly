package com.wyrdly.post.application.service;

import com.wyrdly.post.application.usecase.ReactToPostUseCase;
import com.wyrdly.post.domain.event.PostReactionEvent;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;

/**
 * Default application-layer orchestration for the {@link ReactToPostUseCase}. The service is a thin
 * pass-through to the repository because rate-limit and idempotency concerns are handled at the
 * REST layer (see {@code ReactionRateLimitFilter} / {@code ReactionIdempotencyFilter}).
 *
 * <p>On a successful add or update (i.e. status is not {@link ReactionStatus#REMOVED}) the service
 * resolves the post author and fires a {@link PostReactionEvent} so downstream listeners (HU11 Web
 * Push) can notify them. Removed reactions do not fire. Self-reactions are filtered here so
 * listeners can trust the event.
 */
@ApplicationScoped
public class ReactionService implements ReactToPostUseCase {

  private final PostRepository postRepository;
  private final Event<PostReactionEvent> reactionEvent;

  @Inject
  public ReactionService(PostRepository postRepository, Event<PostReactionEvent> reactionEvent) {
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository must not be null");
    this.reactionEvent = reactionEvent;
  }

  @Override
  public ReactionResult react(String userId, String postId, ReactionType type) {
    ReactionResult result = postRepository.react(userId, postId, type);
    if (result.status() == ReactionStatus.REMOVED) {
      return result;
    }
    Optional<Post> post = postRepository.findById(postId);
    if (post.isEmpty()) {
      // Race: the post was deleted between react and findById. Skip the push.
      return result;
    }
    String authorId = post.get().userId();
    if (authorId.equals(userId)) {
      // Self-reaction: the author already sees their own action in-app.
      return result;
    }
    if (reactionEvent != null) {
      reactionEvent.fire(new PostReactionEvent(postId, authorId, userId, type));
    }
    return result;
  }
}
