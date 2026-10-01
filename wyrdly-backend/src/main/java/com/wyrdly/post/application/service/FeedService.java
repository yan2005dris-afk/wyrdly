package com.wyrdly.post.application.service;

import com.wyrdly.post.application.dto.FeedResponseDto;
import com.wyrdly.post.application.dto.FeedResponseDto.PaginationMeta;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.pagination.CursorFeedPagination;
import com.wyrdly.post.application.pagination.FeedPaginationRequest;
import com.wyrdly.post.application.pagination.OffsetFeedPagination;
import com.wyrdly.post.application.usecase.GetFeedUseCase;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class FeedService implements GetFeedUseCase {

  private final PostRepository postRepository;

  @Inject
  public FeedService(PostRepository postRepository) {
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository must not be null");
  }

  @Override
  public FeedResponseDto getFeed(String userId, FeedPaginationRequest pagination) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    Objects.requireNonNull(pagination, "pagination must not be null");

    return switch (pagination) {
      case CursorFeedPagination cursorStrategy -> executeCursorStrategy(userId, cursorStrategy);
      case OffsetFeedPagination offsetStrategy -> executeOffsetStrategy(userId, offsetStrategy);
    };
  }

  @Override
  public FeedResponseDto getFeed(String userId, int page, int pageSize) {
    return getFeed(userId, new OffsetFeedPagination(page, pageSize));
  }

  @Override
  public FeedResponseDto getFeedWithCursor(String userId, String cursor, int limit) {
    return getFeed(userId, new CursorFeedPagination(cursor, limit));
  }

  private FeedResponseDto executeOffsetStrategy(String userId, OffsetFeedPagination pagination) {
    int effectivePage = pagination.page();
    int effectivePageSize = pagination.pageSize();

    List<FeedPost> feedPosts =
        postRepository.findFeedByUserId(userId, effectivePage, effectivePageSize);
    long totalElements = postRepository.countFeedByUserId(userId);

    int totalPages =
        totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / effectivePageSize);
    boolean hasNext = (long) effectivePage * effectivePageSize < totalElements;

    List<PostResponse> postResponses =
        feedPosts.stream().map(this::toPostResponse).collect(Collectors.toList());

    String nextCursor = null;
    if (hasNext && !feedPosts.isEmpty()) {
      Instant lastCreatedAt = feedPosts.get(feedPosts.size() - 1).createdAt();
      nextCursor = encodeCursor(lastCreatedAt);
    }

    PaginationMeta meta =
        new PaginationMeta(
            effectivePage,
            effectivePageSize,
            totalElements,
            totalPages,
            hasNext,
            nextCursor,
            hasNext);

    return new FeedResponseDto(postResponses, meta);
  }

  private FeedResponseDto executeCursorStrategy(String userId, CursorFeedPagination pagination) {
    int effectiveLimit = pagination.limit();
    Instant cursorInstant = decodeCursor(pagination.cursor());

    List<FeedPost> rawPosts =
        postRepository.findFeedByUserIdWithCursor(userId, cursorInstant, effectiveLimit + 1);

    boolean hasMore = rawPosts.size() > effectiveLimit;
    List<FeedPost> pagePosts = hasMore ? rawPosts.subList(0, effectiveLimit) : rawPosts;

    String nextCursor = null;
    if (hasMore && !pagePosts.isEmpty()) {
      Instant lastCreatedAt = pagePosts.get(pagePosts.size() - 1).createdAt();
      nextCursor = encodeCursor(lastCreatedAt);
    }

    List<PostResponse> postResponses =
        pagePosts.stream().map(this::toPostResponse).collect(Collectors.toList());

    PaginationMeta meta =
        new PaginationMeta(1, effectiveLimit, pagePosts.size(), 1, hasMore, nextCursor, hasMore);

    return new FeedResponseDto(postResponses, meta);
  }

  private Instant decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      return Instant.parse(decoded);
    } catch (Exception e) {
      try {
        return Instant.parse(cursor);
      } catch (Exception ex2) {
        throw new IllegalArgumentException("Invalid cursor format: " + cursor);
      }
    }
  }

  private String encodeCursor(Instant instant) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(instant.toString().getBytes(StandardCharsets.UTF_8));
  }

  private PostResponse toPostResponse(FeedPost feedPost) {
    AuthorDto authorDto =
        new AuthorDto(
            feedPost.author().id(),
            feedPost.author().username(),
            feedPost.author().fullName(),
            feedPost.author().avatarUrl());

    PostResponse.ReactionCounts reactionCounts =
        new PostResponse.ReactionCounts(
            feedPost.likeCount(), feedPost.loveCount(), feedPost.celebrateCount());

    return new PostResponse(
        feedPost.id(),
        feedPost.content(),
        feedPost.mediaUrl(),
        feedPost.createdAt(),
        authorDto,
        reactionCounts,
        feedPost.userReaction());
  }
}
