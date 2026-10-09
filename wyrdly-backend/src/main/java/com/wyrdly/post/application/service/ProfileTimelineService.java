package com.wyrdly.post.application.service;

import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.mapper.PostResponseMapper;
import com.wyrdly.post.application.usecase.GetProfileTimelineUseCase;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;

/** Application service building a user's profile timeline (publications + reposts, HU #150). */
@ApplicationScoped
public class ProfileTimelineService implements GetProfileTimelineUseCase {

  private final PostRepository postRepository;

  @Inject
  public ProfileTimelineService(PostRepository postRepository) {
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository must not be null");
  }

  @Override
  public List<PostResponse> getProfileTimeline(
      String ownerId, String viewerId, int page, int pageSize) {
    if (ownerId == null || ownerId.isBlank()) {
      throw new IllegalArgumentException("ownerId must not be blank");
    }
    return postRepository.findProfileTimeline(ownerId, viewerId, page, pageSize).stream()
        .map(PostResponseMapper::toResponse)
        .toList();
  }
}
