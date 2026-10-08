package com.wyrdly.notifications.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushAudienceQueryPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.application.usecase.impl.NotifyFollowersUseCaseImpl;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushMessage;
import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.domain.model.PushTarget;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotifyFollowersUseCaseImplTest {

  private static final String AUTHOR = "usr_author";
  private static final PushMessage MESSAGE =
      new PushMessage(
          "NEW_POST_FROM_FOLLOWED",
          "Nueva publicación de alice",
          "hola",
          "/posts/pst_1",
          Map.of("postId", "pst_1"));

  private PushAudienceQueryPort audience;
  private PushDispatcherPort dispatcher;
  private NotificationBroadcasterPort broadcaster;
  private NotificationRepository notificationRepository;
  private UserProfileRepository userProfileRepository;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    audience = mock(PushAudienceQueryPort.class);
    dispatcher = mock(PushDispatcherPort.class);
    broadcaster = mock(NotificationBroadcasterPort.class);
    notificationRepository = mock(NotificationRepository.class);
    userProfileRepository = mock(UserProfileRepository.class);
    meterRegistry = new SimpleMeterRegistry();
    when(dispatcher.dispatchTo(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(userProfileRepository.findProfileSummariesByIds(Set.of(AUTHOR)))
        .thenReturn(
            Map.of(
                AUTHOR,
                new FollowerSummary(AUTHOR, "alice", "Alice Wonder", "https://avatar.png", false)));
  }

  private NotifyFollowersUseCaseImpl useCase(int batchSize, int maxRecipients) {
    return new NotifyFollowersUseCaseImpl(
        audience,
        dispatcher,
        broadcaster,
        notificationRepository,
        userProfileRepository,
        meterRegistry,
        batchSize,
        maxRecipients);
  }

  private static List<PushTarget> targets(int fromInclusive, int toExclusive, boolean withSubscription) {
    return IntStream.range(fromInclusive, toExclusive)
        .mapToObj(
            i ->
                new PushTarget(
                    String.format("usr_%03d", i),
                    withSubscription
                        ? new PushSubscription("https://push.example/" + i, "p256dh", "auth")
                        : null))
        .toList();
  }

  private double counter(String result) {
    return meterRegistry.counter("wyrdly.push.fanout", "result", result).count();
  }

  @Test
  void noFollowersIsANoOp() {
    when(audience.findAllFollowers(AUTHOR, "", 200)).thenReturn(List.of());

    useCase(200, 10_000).notifyFollowers(AUTHOR, MESSAGE);

    verify(dispatcher, never()).dispatchTo(any(), any());
    verify(notificationRepository, never()).save(any());
    verify(broadcaster, never()).broadcast(any(), any());
    assertEquals(1.0, counter("completed"));
    assertEquals(0.0, counter("capped"));
  }

  @Test
  void dispatchesInAppNotificationAndSseToAllAndPushOnlyToSubscribed() {
    // usr_000 and usr_001 are subscribed, usr_002 is not
    List<PushTarget> batch =
        List.of(
            new PushTarget("usr_000", new PushSubscription("https://push.example/0", "p256", "auth")),
            new PushTarget("usr_001", new PushSubscription("https://push.example/1", "p256", "auth")),
            new PushTarget("usr_002", null));
    when(audience.findAllFollowers(AUTHOR, "", 200)).thenReturn(batch);

    useCase(200, 10_000).notifyFollowers(AUTHOR, MESSAGE);

    // In-app Notification persisted for all 3
    ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(3)).save(notifCaptor.capture());
    assertEquals("usr_000", notifCaptor.getAllValues().get(0).recipientUserId());
    assertEquals("usr_001", notifCaptor.getAllValues().get(1).recipientUserId());
    assertEquals("usr_002", notifCaptor.getAllValues().get(2).recipientUserId());
    assertEquals("pst_1", notifCaptor.getAllValues().get(0).targetResourceId());

    // SSE Broadcast for all 3
    ArgumentCaptor<NotificationDto> sseCaptor = ArgumentCaptor.forClass(NotificationDto.class);
    verify(broadcaster, times(3)).broadcast(anyString(), sseCaptor.capture());
    assertEquals("Alice Wonder", sseCaptor.getAllValues().get(0).actor().fullName());

    // Push dispatched ONLY for the 2 subscribed users
    ArgumentCaptor<PushSubscription> subs = ArgumentCaptor.forClass(PushSubscription.class);
    ArgumentCaptor<PushEvent> events = ArgumentCaptor.forClass(PushEvent.class);
    verify(dispatcher, times(2)).dispatchTo(subs.capture(), events.capture());
    assertEquals("https://push.example/0", subs.getAllValues().get(0).endpoint());
    assertEquals("usr_000", events.getAllValues().get(0).recipientUserId());
    assertEquals("https://push.example/1", subs.getAllValues().get(1).endpoint());
    assertEquals("usr_001", events.getAllValues().get(1).recipientUserId());

    verify(audience, times(1)).findAllFollowers(anyString(), anyString(), anyInt());
    assertEquals(3.0, meterRegistry.counter("wyrdly.push.fanout.recipients").count());
  }

  @Test
  void pagesThroughAudienceWithKeysetCursor() {
    when(audience.findAllFollowers(AUTHOR, "", 2)).thenReturn(targets(0, 2, true));
    when(audience.findAllFollowers(AUTHOR, "usr_001", 2)).thenReturn(targets(2, 4, true));
    when(audience.findAllFollowers(AUTHOR, "usr_003", 2)).thenReturn(targets(4, 5, true));

    useCase(2, 10_000).notifyFollowers(AUTHOR, MESSAGE);

    verify(dispatcher, times(5)).dispatchTo(any(), any());
    verify(notificationRepository, times(5)).save(any());
    verify(broadcaster, times(5)).broadcast(anyString(), any());
    verify(audience, times(3)).findAllFollowers(eq(AUTHOR), anyString(), eq(2));
    assertEquals(1.0, counter("completed"));
  }

  @Test
  void stopsAtMaxRecipientsAndReportsCapped() {
    when(audience.findAllFollowers(AUTHOR, "", 2)).thenReturn(targets(0, 2, true));
    when(audience.findAllFollowers(AUTHOR, "usr_001", 1)).thenReturn(targets(2, 3, true));

    useCase(2, 3).notifyFollowers(AUTHOR, MESSAGE);

    verify(dispatcher, times(3)).dispatchTo(any(), any());
    verify(notificationRepository, times(3)).save(any());
    verify(broadcaster, times(3)).broadcast(anyString(), any());
    verify(audience, never()).findAllFollowers(AUTHOR, "usr_002", 2);
    assertEquals(1.0, counter("capped"));
    assertEquals(0.0, counter("completed"));
  }

  @Test
  void waitsForEachBatchBeforeReadingTheNextOne() {
    List<String> timeline = Collections.synchronizedList(new ArrayList<>());
    when(dispatcher.dispatchTo(any(), any()))
        .thenAnswer(
            inv -> {
              timeline.add("dispatch");
              return CompletableFuture.runAsync(() -> sleep(50))
                  .thenRun(() -> timeline.add("done"));
            });
    when(audience.findAllFollowers(AUTHOR, "", 1))
        .thenAnswer(
            inv -> {
              timeline.add("query");
              return targets(0, 1, true);
            });
    when(audience.findAllFollowers(AUTHOR, "usr_000", 1))
        .thenAnswer(
            inv -> {
              timeline.add("query");
              return List.of();
            });

    useCase(1, 10_000).notifyFollowers(AUTHOR, MESSAGE);

    assertEquals(List.of("query", "dispatch", "done", "query"), timeline);
  }

  @Test
  void rejectsInvalidConfiguration() {
    assertThrows(IllegalArgumentException.class, () -> useCase(0, 10));
    assertThrows(IllegalArgumentException.class, () -> useCase(10, 0));
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

