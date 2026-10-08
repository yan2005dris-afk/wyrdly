package com.wyrdly.notifications.infrastructure.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyrdly.notifications.application.port.PushGatewayClientPort;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no Quarkus) for the bounded dispatcher queue: once the pool and the queue are
 * full, further pushes are dropped instead of piling up in memory.
 */
class PushDispatcherImplSaturationTest {

  private final CountDownLatch release = new CountDownLatch(1);
  private PushDispatcherImpl dispatcher;

  @AfterEach
  void tearDown() {
    release.countDown();
    if (dispatcher != null) {
      dispatcher.shutdown();
    }
  }

  @Test
  void dropsPushesAndCountsRejectedWhenQueueIsFull() throws Exception {
    PushSubscriptionRepositoryPort repository = mock(PushSubscriptionRepositoryPort.class);
    CountDownLatch firstTaskRunning = new CountDownLatch(1);
    // The lookup runs on the dispatcher pool: blocking it keeps the single worker busy.
    when(repository.findByUserId(anyString()))
        .thenAnswer(
            inv -> {
              firstTaskRunning.countDown();
              release.await(5, TimeUnit.SECONDS);
              return null;
            });

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    dispatcher =
        new PushDispatcherImpl(
            mock(VapidKeyProvider.class),
            repository,
            mock(PushGatewayClientPort.class),
            new ObjectMapper(),
            registry,
            "mailto:test@wyrdly.com",
            0,
            1,
            1, // poolSize
            1, // queueCapacity
            0, // global rate limit disabled
            0); // recipient rate limit disabled
    dispatcher.registerMetrics();

    dispatcher.dispatch(event("usr_running"));
    assertTrue(firstTaskRunning.await(5, TimeUnit.SECONDS));
    dispatcher.dispatch(event("usr_queued"));

    CompletableFuture<Void> dropped =
        dispatcher.dispatchTo(
            new PushSubscription("https://push.example/x", "p256dh", "auth"), event("usr_dropped"));

    assertTrue(dropped.isDone(), "a rejected push must complete immediately");
    assertEquals(
        1.0,
        registry.find("wyrdly.push.dispatch").tag("result", "rejected").counters().stream()
            .mapToDouble(Counter::count)
            .sum());
  }

  private static PushEvent event(String userId) {
    return new PushEvent(userId, "NEW_POST_FROM_FOLLOWED", "t", "b", "/posts/p", Map.of());
  }
}
