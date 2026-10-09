package com.wyrdly.post.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a post enriched with its author information and reactions for feed
 * presentation.
 *
 * <p>{@code repostContext} is non-null only when the post appears in a timeline because someone
 * shared it (HU #150); {@code author} always remains the original author.
 */
public record FeedPost(
    String id,
    String content,
    String mediaUrl,
    Instant createdAt,
    Author author,
    long likeCount,
    long loveCount,
    long celebrateCount,
    long commentsCount,
    long repostsCount,
    String userReaction,
    boolean userHasReposted,
    RepostContext repostContext) {

  public FeedPost {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(author, "author must not be null");
    if (likeCount < 0
        || loveCount < 0
        || celebrateCount < 0
        || commentsCount < 0
        || repostsCount < 0) {
      throw new IllegalArgumentException("counts must not be negative");
    }
    // userReaction can be null (user has not reacted) or one of: LIKE, LOVE, CELEBRATE
  }

  /** Backward-compatible 12-argument constructor for call sites that pre-date HU #150. */
  public FeedPost(
      String id,
      String content,
      String mediaUrl,
      Instant createdAt,
      Author author,
      long likeCount,
      long loveCount,
      long celebrateCount,
      long commentsCount,
      long repostsCount,
      String userReaction,
      boolean userHasReposted) {
    this(
        id,
        content,
        mediaUrl,
        createdAt,
        author,
        likeCount,
        loveCount,
        celebrateCount,
        commentsCount,
        repostsCount,
        userReaction,
        userHasReposted,
        null);
  }

  /** True when this entry is a share of someone else's post rather than an original publication. */
  public boolean isRepost() {
    return repostContext != null;
  }

  /** Backward-compatible 10-argument constructor for call sites that pre-date HU #144. */
  public FeedPost(
      String id,
      String content,
      String mediaUrl,
      Instant createdAt,
      Author author,
      long likeCount,
      long loveCount,
      long celebrateCount,
      long commentsCount,
      String userReaction) {
    this(
        id,
        content,
        mediaUrl,
        createdAt,
        author,
        likeCount,
        loveCount,
        celebrateCount,
        commentsCount,
        0L,
        userReaction,
        false,
        null);
  }
}
