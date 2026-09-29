package com.yaga.user.application.usecase;

import com.yaga.user.application.dto.UserSearchResponseDto;

public interface SearchUsersUseCase {
  UserSearchResponseDto searchUsers(String userId, String query, int page, int pageSize);
}
