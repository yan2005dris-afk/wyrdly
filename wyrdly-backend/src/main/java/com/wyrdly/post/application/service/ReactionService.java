package com.wyrdly.post.application.service;

import com.wyrdly.post.application.usecase.ReactToPostUseCase;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionType;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

/**
 * Default application-layer orchestration for the {@link ReactToPostUseCase}. The service is a thin
 * pass-through to the repository because rate-limit and idempotency concerns are handled at the
 * REST layer (see {@code ReactionRateLimitFilter} / {@code ReactionIdempotencyFilter}).
 */
@ApplicationScoped
public class ReactionService implements ReactToPostUseCase {

  private final PostRepository postRepository;

  @Inject
  public ReactionService(PostRepository postRepository) {
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository must not be null");
  }

  @Override
  public ReactionResult react(String userId, String postId, ReactionType type) {
    return postRepository.react(userId, postId, type);
  }
}
