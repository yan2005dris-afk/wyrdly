package com.wyrdly.post.application.pagination;

/**
 * Sealed interface representing a pagination strategy for the feed.
 *
 * <p>Enables idiomatic pattern matching in {@code FeedService} across cursor-based and offset-based
 * traversal strategies.
 */
public sealed interface FeedPaginationRequest permits OffsetFeedPagination, CursorFeedPagination {

  int size();

  static FeedPaginationRequest fromParams(
      String cursor, Integer limit, Integer page, Integer pageSize) {
    if (cursor != null || limit != null || (page == null && pageSize == null)) {
      int effectiveLimit = limit != null ? limit : (pageSize != null ? pageSize : 20);
      return new CursorFeedPagination(cursor, effectiveLimit);
    }
    return new OffsetFeedPagination(page != null ? page : 1, pageSize != null ? pageSize : 20);
  }
}
