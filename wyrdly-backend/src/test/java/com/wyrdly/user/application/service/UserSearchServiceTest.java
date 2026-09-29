package com.wyrdly.user.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.user.application.dto.UserSearchResponseDto;
import com.wyrdly.user.application.dto.UserSearchResponseDto.Meta;
import com.wyrdly.user.application.dto.UserSearchResultDto;
import com.wyrdly.user.domain.exception.SearchValidationException;
import com.wyrdly.user.domain.repository.UserSearchRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSearchServiceTest {

  private UserSearchRepository repository;
  private UserSearchService service;

  private static final String VIEWER_ID = "usr_viewer";
  private static final String QUERY = "alice";

  @BeforeEach
  void setUp() {
    repository = mock(UserSearchRepository.class);
    service = new UserSearchService(repository);
  }

  @Test
  void searchUsers_returnsResultsAndMeta_whenValidQuery() {
    List<UserSearchResultDto> results =
        List.of(
            new UserSearchResultDto("usr_1", "alice", "Alice Chen", null, "Bio 1", false, null),
            new UserSearchResultDto(
                "usr_2", "alice2", "Alice Doe", null, null, true, "3 amigos en común"));
    when(repository.findByText(eq(QUERY), eq(VIEWER_ID), eq(0), eq(20))).thenReturn(results);
    when(repository.countByText(QUERY, VIEWER_ID)).thenReturn(2);

    UserSearchResponseDto response = service.searchUsers(VIEWER_ID, QUERY, 0, 20);

    assertNotNull(response);
    assertEquals(2, response.data().size());
    assertEquals(new Meta(0, 20, 2), response.meta());
    verify(repository).findByText(QUERY, VIEWER_ID, 0, 20);
    verify(repository).countByText(QUERY, VIEWER_ID);
  }

  @Test
  void searchUsers_returnsEmptyDataAndZeroTotal_whenNoMatches() {
    when(repository.findByText(anyString(), anyString(), anyInt(), anyInt())).thenReturn(List.of());
    when(repository.countByText(QUERY, VIEWER_ID)).thenReturn(0);

    UserSearchResponseDto response = service.searchUsers(VIEWER_ID, QUERY, 0, 20);

    assertEquals(0, response.data().size());
    assertEquals(new Meta(0, 20, 0), response.meta());
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenQueryIsNull() {
    SearchValidationException ex =
        assertThrows(
            SearchValidationException.class, () -> service.searchUsers(VIEWER_ID, null, 0, 20));
    assertTrue(ex.getMessage().contains("2 caracteres"));
    verify(repository, never()).findByText(anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenQueryIsBlank() {
    SearchValidationException ex =
        assertThrows(
            SearchValidationException.class, () -> service.searchUsers(VIEWER_ID, "   ", 0, 20));
    assertTrue(ex.getMessage().contains("2 caracteres"));
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenQueryShorterThanMinLength() {
    SearchValidationException ex =
        assertThrows(
            SearchValidationException.class, () -> service.searchUsers(VIEWER_ID, "a", 0, 20));
    assertTrue(ex.getMessage().contains("2 caracteres"));
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenQueryAtMinLengthIsAccepted() {
    when(repository.findByText(eq("ab"), eq(VIEWER_ID), anyInt(), anyInt())).thenReturn(List.of());
    when(repository.countByText("ab", VIEWER_ID)).thenReturn(0);

    UserSearchResponseDto response = service.searchUsers(VIEWER_ID, "ab", 0, 20);
    assertNotNull(response);
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenPageIsNegative() {
    SearchValidationException ex =
        assertThrows(
            SearchValidationException.class, () -> service.searchUsers(VIEWER_ID, QUERY, -1, 20));
    assertTrue(ex.getMessage().contains("mayor o igual a 0"));
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenPageSizeIsTooLarge() {
    SearchValidationException ex =
        assertThrows(
            SearchValidationException.class, () -> service.searchUsers(VIEWER_ID, QUERY, 0, 51));
    assertTrue(ex.getMessage().contains("entre 1 y 50"));
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenPageSizeIsZero() {
    SearchValidationException ex =
        assertThrows(
            SearchValidationException.class, () -> service.searchUsers(VIEWER_ID, QUERY, 0, 0));
    assertTrue(ex.getMessage().contains("entre 1 y 50"));
  }

  @Test
  void searchUsers_throwsSearchValidationException_whenViewerIdIsBlank() {
    SearchValidationException ex =
        assertThrows(SearchValidationException.class, () -> service.searchUsers("", QUERY, 0, 20));
    assertTrue(ex.getMessage().contains("autenticado"));
  }

  @Test
  void searchUsers_passesCorrectSkipToRepository_whenPageIsNonZero() {
    when(repository.findByText(QUERY, VIEWER_ID, 2, 20)).thenReturn(List.of());
    when(repository.countByText(QUERY, VIEWER_ID)).thenReturn(0);

    service.searchUsers(VIEWER_ID, QUERY, 2, 20);

    verify(repository).findByText(QUERY, VIEWER_ID, 2, 20);
  }
}
