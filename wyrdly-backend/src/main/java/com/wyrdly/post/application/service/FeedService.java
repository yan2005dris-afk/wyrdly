package com.wyrdly.post.application.service;

import com.wyrdly.post.application.dto.FeedResponseDto;
import com.wyrdly.post.application.dto.FeedResponseDto.PaginationMeta;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.GetFeedUseCase;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class FeedService implements GetFeedUseCase {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 50;

  private final PostRepository postRepository;

  @Inject
  public FeedService(PostRepository postRepository) {
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository must not be null");
  }

  @Override
  public FeedResponseDto getFeed(String userId, int page, int pageSize) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }

    int effectivePage = page < 1 ? DEFAULT_PAGE : page;
    int effectivePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

    List<FeedPost> feedPosts =
        postRepository.findFeedByUserId(userId, effectivePage, effectivePageSize);
    long totalElements = postRepository.countFeedByUserId(userId);

    int totalPages =
        totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / effectivePageSize);
    boolean hasNext = (long) effectivePage * effectivePageSize < totalElements;

    List<PostResponse> postResponses =
        feedPosts.stream().map(this::toPostResponse).collect(Collectors.toList());

    PaginationMeta meta =
        new PaginationMeta(effectivePage, effectivePageSize, totalElements, totalPages, hasNext);

    return new FeedResponseDto(postResponses, meta);
  }

  private PostResponse toPostResponse(FeedPost feedPost) {
    AuthorDto authorDto =
        new AuthorDto(
            feedPost.author().id(),
            feedPost.author().username(),
            feedPost.author().fullName(),
            feedPost.author().avatarUrl());

    return new PostResponse(
        feedPost.id(), feedPost.content(), feedPost.mediaUrl(), feedPost.createdAt(), authorDto);
  }
}
