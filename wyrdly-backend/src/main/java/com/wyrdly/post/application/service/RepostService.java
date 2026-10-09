package com.wyrdly.post.application.service;

import com.wyrdly.post.application.dto.RepostResponse;
import com.wyrdly.post.application.usecase.RepostPostUseCase;
import com.wyrdly.post.application.usecase.UndoRepostUseCase;
import com.wyrdly.post.domain.event.PostRepostedEvent;
import com.wyrdly.post.domain.model.RepostResult;
import com.wyrdly.post.domain.repository.RepostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service orchestrating post reposting (boost) and unreposting. Fires {@link
 * PostRepostedEvent} only when a new repost is created and the actor is not the post author
 * (avoiding self-notifications).
 */
@ApplicationScoped
public class RepostService implements RepostPostUseCase, UndoRepostUseCase {

  private final RepostRepository repostRepository;
  private final Event<PostRepostedEvent> postRepostedEvent;

  @Inject
  public RepostService(
      RepostRepository repostRepository, Event<PostRepostedEvent> postRepostedEvent) {
    this.repostRepository =
        Objects.requireNonNull(repostRepository, "repostRepository must not be null");
    this.postRepostedEvent =
        Objects.requireNonNull(postRepostedEvent, "postRepostedEvent must not be null");
  }

  @Override
  public RepostResponse repost(String userId, String postId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    if (postId == null || postId.isBlank()) {
      throw new IllegalArgumentException("postId must not be blank");
    }

    String requestId = UUID.randomUUID().toString();
    RepostResult result = repostRepository.repost(userId, postId, requestId);

    if (result.changed() && result.reposted() && !result.authorId().equals(userId)) {
      postRepostedEvent.fire(new PostRepostedEvent(result.postId(), result.authorId(), userId));
    }

    return new RepostResponse(result.reposted(), result.repostsCount());
  }

  @Override
  public RepostResponse unrepost(String userId, String postId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    if (postId == null || postId.isBlank()) {
      throw new IllegalArgumentException("postId must not be blank");
    }

    RepostResult result = repostRepository.unrepost(userId, postId);
    return new RepostResponse(result.reposted(), result.repostsCount());
  }
}
