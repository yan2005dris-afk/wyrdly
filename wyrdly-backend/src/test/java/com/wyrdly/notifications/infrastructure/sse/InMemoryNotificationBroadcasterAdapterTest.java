package com.wyrdly.notifications.infrastructure.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.notifications.application.dto.NotificationDto;
import io.smallrye.mutiny.subscription.Cancellable;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryNotificationBroadcasterAdapterTest {

  private InMemoryNotificationBroadcasterAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new InMemoryNotificationBroadcasterAdapter();
  }

  @Test
  @DisplayName("broadcast delivers notification to active subscriber of target user")
  void broadcastDeliversToActiveSubscriber() throws InterruptedException {
    String userId = "usr_target_1";
    CopyOnWriteArrayList<NotificationDto> received = new CopyOnWriteArrayList<>();

    Cancellable cancellable = adapter.subscribe(userId).subscribe().with(received::add);

    NotificationDto dto =
        new NotificationDto(
            "ntf_1",
            "GRAPH_FOLLOW",
            "Nuevo seguidor",
            "Alice te sigue",
            "/feed",
            null,
            false,
            Instant.now(),
            null);

    adapter.broadcast(userId, dto);

    Thread.sleep(100);

    assertEquals(1, received.size());
    assertEquals(dto, received.get(0));

    cancellable.cancel();
  }

  @Test
  @DisplayName("broadcast does not deliver notification to other users")
  void broadcastDoesNotCrossDeliver() throws InterruptedException {
    String userA = "usr_a";
    String userB = "usr_b";
    CopyOnWriteArrayList<NotificationDto> receivedB = new CopyOnWriteArrayList<>();

    Cancellable cancellableB = adapter.subscribe(userB).subscribe().with(receivedB::add);

    NotificationDto dtoA =
        new NotificationDto(
            "ntf_1",
            "GRAPH_FOLLOW",
            "Nuevo seguidor",
            "Alice te sigue",
            "/feed",
            null,
            false,
            Instant.now(),
            null);

    adapter.broadcast(userA, dtoA);

    Thread.sleep(100);

    assertTrue(receivedB.isEmpty());

    cancellableB.cancel();
  }

  @Test
  @DisplayName("cancelling subscription removes emitter from active subscribers")
  void cancellationRemovesEmitter() throws InterruptedException {
    String userId = "usr_test";

    Cancellable cancellable = adapter.subscribe(userId).subscribe().with(dto -> {});

    assertEquals(1, adapter.activeSubscribersCount(userId));

    cancellable.cancel();
    Thread.sleep(50);

    assertEquals(0, adapter.activeSubscribersCount(userId));
  }
}
