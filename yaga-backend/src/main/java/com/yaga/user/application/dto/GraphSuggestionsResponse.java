package com.yaga.user.application.dto;

import java.util.List;

public record GraphSuggestionsResponse(List<GraphSuggestionUserDto> data, PaginationMeta meta) {

  public record PaginationMeta(int page, int pageSize, long totalCount) {}

  public GraphSuggestionsResponse(
      List<GraphSuggestionUserDto> data, int page, int pageSize, long totalCount) {
    this(data, new PaginationMeta(page, pageSize, totalCount));
  }
}
