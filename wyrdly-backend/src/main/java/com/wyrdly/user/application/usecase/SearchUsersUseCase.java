package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.UserSearchResponseDto;

public interface SearchUsersUseCase {
  UserSearchResponseDto searchUsers(String userId, String query, int page, int pageSize);
}
