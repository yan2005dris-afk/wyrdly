package com.wyrdly.post.application.dto;

import java.util.List;

public record FeedResponseDto(List<PostResponse> data, PaginationMeta meta) {

  public record PaginationMeta(
      int page,
      int pageSize,
      long totalElements,
      int totalPages,
      boolean hasNext,
      String nextCursor,
      boolean hasMore) {

    public PaginationMeta(
        int page, int pageSize, long totalElements, int totalPages, boolean hasNext) {
      this(page, pageSize, totalElements, totalPages, hasNext, null, hasNext);
    }
  }
}
