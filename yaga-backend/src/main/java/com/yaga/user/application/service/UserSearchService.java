package com.yaga.user.application.service;

import com.yaga.user.application.dto.UserSearchResponseDto;
import com.yaga.user.application.dto.UserSearchResponseDto.Meta;
import com.yaga.user.application.dto.UserSearchResultDto;
import com.yaga.user.application.usecase.SearchUsersUseCase;
import com.yaga.user.domain.exception.SearchValidationException;
import com.yaga.user.domain.repository.UserSearchRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class UserSearchService implements SearchUsersUseCase {

  private static final int MIN_QUERY_LENGTH = 2;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 50;

  private final UserSearchRepository userSearchRepository;

  @Inject
  public UserSearchService(UserSearchRepository userSearchRepository) {
    this.userSearchRepository =
        Objects.requireNonNull(userSearchRepository, "userSearchRepository must not be null");
  }

  @Override
  public UserSearchResponseDto searchUsers(String userId, String query, int page, int pageSize) {
    validate(userId, query, page, pageSize);

    int effectivePageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
    int effectivePage = Math.max(page, 0);

    List<UserSearchResultDto> results =
        userSearchRepository.findByText(query, userId, effectivePage, effectivePageSize);
    int totalResults = userSearchRepository.countByText(query, userId);

    return new UserSearchResponseDto(
        results, new Meta(effectivePage, effectivePageSize, totalResults));
  }

  private void validate(String userId, String query, int page, int pageSize) {
    if (userId == null || userId.isBlank()) {
      throw new SearchValidationException("El usuario autenticado es requerido.");
    }
    if (query == null || query.isBlank()) {
      throw new SearchValidationException("La búsqueda debe tener al menos 2 caracteres.");
    }
    if (query.length() < MIN_QUERY_LENGTH) {
      throw new SearchValidationException(
          "La búsqueda debe tener al menos " + MIN_QUERY_LENGTH + " caracteres.");
    }
    if (page < 0) {
      throw new SearchValidationException("La página debe ser mayor o igual a 0.");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new SearchValidationException(
          "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
    }
  }
}
