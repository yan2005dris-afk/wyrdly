package com.wyrdly.post.application.dto;

import java.time.Instant;

/**
 * Response DTO for {@code GET /api/posts} and {@code GET /api/feed}.
 *
 * <p>Includes {@code reactionCounts}, {@code userReaction}, and {@code commentsCount} to support
 * HU08 (Feed), HU09 (Reacciones), and HU10 (Post Comments) interactions. {@code userReaction} is
 * null when the authenticated user has not reacted. {@code commentsCount} defaults to {@code 0L}
 * for code paths that do not compute it eagerly (e.g. the {@code createPost} response).
 *
 * <p>A 7-argument overload is provided for backward compatibility with call sites that pre-date
 * HU10; it delegates to the canonical 8-argument constructor with {@code commentsCount = 0L}.
 *
 * <p>{@code repostContext} (HU #150) is non-null only when the post appears in a profile timeline
 * because that user shared it; {@code author} is always the original author.
 */
public record PostResponse(
    String id,
    String content,
    String mediaUrl,
    Instant createdAt,
    AuthorDto author,
    ReactionCounts reactionCounts,
    long commentsCount,
    long repostsCount,
    String userReaction,
    boolean userHasReposted,
    RepostContextDto repostContext) {

  /**
   * Backward-compatible 10-argument constructor for callers that pre-date HU #150. Delegates to the
   * canonical constructor with {@code repostContext = null} (an original publication).
   */
  public PostResponse(
      String id,
      String content,
      String mediaUrl,
      Instant createdAt,
      AuthorDto author,
      ReactionCounts reactionCounts,
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
        reactionCounts,
        commentsCount,
        repostsCount,
        userReaction,
        userHasReposted,
        null);
  }

  /**
   * Backward-compatible 8-argument constructor for callers that pre-date HU #144. Delegates to the
   * canonical constructor with {@code repostsCount = 0L} and {@code userHasReposted = false}.
   */
  public PostResponse(
      String id,
      String content,
      String mediaUrl,
      Instant createdAt,
      AuthorDto author,
      ReactionCounts reactionCounts,
      long commentsCount,
      String userReaction) {
    this(
        id,
        content,
        mediaUrl,
        createdAt,
        author,
        reactionCounts,
        commentsCount,
        0L,
        userReaction,
        false);
  }

  /**
   * Backward-compatible 7-argument constructor for callers that pre-date HU10. Delegates to the
   * canonical constructor with {@code commentsCount = 0L}, {@code repostsCount = 0L}, and {@code
   * userHasReposted = false}.
   */
  public PostResponse(
      String id,
      String content,
      String mediaUrl,
      Instant createdAt,
      AuthorDto author,
      ReactionCounts reactionCounts,
      String userReaction) {
    this(id, content, mediaUrl, createdAt, author, reactionCounts, 0L, 0L, userReaction, false);
  }

  public record AuthorDto(String id, String username, String fullName, String avatarUrl) {}

  public record ReactionCounts(long likeCount, long loveCount, long celebrateCount) {}

  /** Who shared the post and when (HU #150). */
  public record RepostContextDto(
      String reposterId,
      String reposterUsername,
      String reposterName,
      String reposterAvatarUrl,
      Instant repostedAt) {}
}
