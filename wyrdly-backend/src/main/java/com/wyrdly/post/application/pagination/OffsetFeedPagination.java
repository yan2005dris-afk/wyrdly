package com.wyrdly.post.application.pagination;

/**
 * Strategy parameters for offset-based feed pagination (SKIP / LIMIT).
 *
 * <p>Used for administrative tables, test fixtures, or legacy clients requesting specific page
 * indices.
 */
public record OffsetFeedPagination(int page, int pageSize) implements FeedPaginationRequest {

  public OffsetFeedPagination {
    page = page < 1 ? 1 : page;
    pageSize = pageSize < 1 ? 20 : Math.min(pageSize, 50);
  }

  @Override
  public int size() {
    return pageSize;
  }
}
