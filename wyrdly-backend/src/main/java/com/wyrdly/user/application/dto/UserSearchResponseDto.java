package com.wyrdly.user.application.dto;

import java.util.List;

public record UserSearchResponseDto(List<UserSearchResultDto> data, Meta meta) {

  public record Meta(int page, int pageSize, int totalResults) {}
}
