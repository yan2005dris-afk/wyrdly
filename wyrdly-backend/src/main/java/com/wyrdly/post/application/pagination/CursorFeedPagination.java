package com.wyrdly.post.application.pagination;

/**
 * Strategy parameters for keyset / cursor-based feed pagination.
 *
 * <p>Used for infinite scrolling timelines with temporal anchoring on {@code p.createdAt}.
 */
public record CursorFeedPagination(String cursor, int limit) implements FeedPaginationRequest {

  public CursorFeedPagination {
    limit = limit < 1 ? 20 : Math.min(limit, 50);
  }

  @Override
  public int size() {
    return limit;
  }
}
