package com.wyrdly.user.domain.repository;

import com.wyrdly.user.application.dto.UserSearchResultDto;
import java.util.List;

public interface UserSearchRepository {

  List<UserSearchResultDto> findByText(String text, String viewerId, int page, int pageSize);

  int countByText(String text, String viewerId);
}
