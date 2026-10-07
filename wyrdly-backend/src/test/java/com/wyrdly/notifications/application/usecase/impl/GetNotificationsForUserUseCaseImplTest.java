package com.wyrdly.notifications.application.usecase.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.dto.NotificationListResponseDto;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetNotificationsForUserUseCaseImplTest {

  private NotificationRepository notificationRepository;
  private UserProfileRepository userProfileRepository;
  private GetNotificationsForUserUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    userProfileRepository = mock(UserProfileRepository.class);
    useCase = new GetNotificationsForUserUseCaseImpl(notificationRepository, userProfileRepository);
  }

  @Test
  void returnsEnrichedNotificationsAndUnreadCount() {
    Notification n =
        new Notification(
            "ntf_abc",
            "usr_alice",
            "GRAPH_FOLLOW",
            "usr_bob",
            "Nuevo seguidor",
            "Bob comenzó a seguirte",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z"));
    when(notificationRepository.findByRecipient("usr_alice", 0, 20)).thenReturn(List.of(n));
    when(notificationRepository.countUnread("usr_alice")).thenReturn(1L);
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenReturn(
            Map.of("usr_bob", new FollowerSummary("usr_bob", "bob", "Bob Marley", "", false)));

    NotificationListResponseDto result = useCase.execute("usr_alice", 0, 20);

    assertEquals(1, result.notifications().size());
    assertEquals(1L, result.unreadCount());
    assertEquals(0, result.page());
    assertEquals(20, result.pageSize());

    NotificationDto dto = result.notifications().get(0);
    assertEquals("ntf_abc", dto.id());
    assertEquals("GRAPH_FOLLOW", dto.type());
    assertNotNull(dto.actor());
    assertEquals("usr_bob", dto.actor().id());
    assertEquals("bob", dto.actor().username());
    assertEquals("Bob Marley", dto.actor().fullName());
  }

  @Test
  void fillsActorPlaceholderWhenActorNotFound() {
    Notification n =
        new Notification(
            "ntf_abc",
            "usr_alice",
            "POST_LIKE",
            "usr_ghost",
            "Le dio Like",
            "body",
            "/posts/pst_1",
            "pst_1",
            false,
            Instant.parse("2026-01-15T10:00:00Z"));
    when(notificationRepository.findByRecipient("usr_alice", 0, 20)).thenReturn(List.of(n));
    when(notificationRepository.countUnread("usr_alice")).thenReturn(0L);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());

    NotificationListResponseDto result = useCase.execute("usr_alice", 0, 20);

    NotificationDto dto = result.notifications().get(0);
    assertEquals("usr_ghost", dto.actor().id());
    assertEquals("Someone", dto.actor().fullName());
  }

  @Test
  void returnsEmptyListWhenUserHasNoNotifications() {
    when(notificationRepository.findByRecipient("usr_alice", 0, 20)).thenReturn(List.of());
    when(notificationRepository.countUnread("usr_alice")).thenReturn(0L);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());

    NotificationListResponseDto result = useCase.execute("usr_alice", 0, 20);

    assertEquals(0, result.notifications().size());
    assertEquals(0L, result.unreadCount());
  }

  @Test
  void clampsPageSizeToMax() {
    when(notificationRepository.findByRecipient(any(), any(int.class), any(int.class)))
        .thenReturn(List.of());
    when(notificationRepository.countUnread(any())).thenReturn(0L);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());

    NotificationListResponseDto result = useCase.execute("usr_alice", 0, 9999);

    assertEquals(50, result.pageSize());
  }

  @Test
  void batchesActorLookup() {
    Notification n1 =
        new Notification(
            "ntf_1",
            "usr_alice",
            "GRAPH_FOLLOW",
            "usr_bob",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z"));
    Notification n2 =
        new Notification(
            "ntf_2",
            "usr_alice",
            "POST_LIKE",
            "usr_carol",
            "t",
            "b",
            "/posts/p",
            "p",
            false,
            Instant.parse("2026-01-15T10:00:01Z"));
    when(notificationRepository.findByRecipient("usr_alice", 0, 20)).thenReturn(List.of(n1, n2));
    when(notificationRepository.countUnread("usr_alice")).thenReturn(2L);
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenReturn(
            Map.of(
                "usr_bob", new FollowerSummary("usr_bob", "bob", "Bob", "", false),
                "usr_carol", new FollowerSummary("usr_carol", "carol", "Carol", "", false)));

    NotificationListResponseDto result = useCase.execute("usr_alice", 0, 20);

    // Verify the lookup was called exactly once with both ids
    org.mockito.Mockito.verify(userProfileRepository)
        .findProfileSummariesByIds(Set.of("usr_bob", "usr_carol"));
    assertEquals(2, result.notifications().size());
  }
}
