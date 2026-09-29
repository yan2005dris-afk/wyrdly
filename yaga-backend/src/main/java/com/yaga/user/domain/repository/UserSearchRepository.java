package com.yaga.user.domain.repository;

import com.yaga.user.application.dto.UserSearchResultDto;
import java.util.List;

public interface UserSearchRepository {

  List<UserSearchResultDto> findByText(String text, String viewerId, int page, int pageSize);

  int countByText(String text, String viewerId);
}
