package com.wyrdly.post.application.dto;

import java.util.List;

public record FeedResponseDto(List<PostResponse> data, PaginationMeta meta) {

  public record PaginationMeta(
      int page, int pageSize, long totalElements, int totalPages, boolean hasNext) {}
}
