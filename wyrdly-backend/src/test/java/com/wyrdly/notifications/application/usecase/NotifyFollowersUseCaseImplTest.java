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

import com.wyrdly.notifications.application.port.PushAudienceQueryPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.application.usecase.impl.NotifyFollowersUseCaseImpl;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushMessage;
import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.domain.model.PushTarget;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    audience = mock(PushAudienceQueryPort.class);
    dispatcher = mock(PushDispatcherPort.class);
    meterRegistry = new SimpleMeterRegistry();
    when(dispatcher.dispatchTo(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
  }

  private NotifyFollowersUseCaseImpl useCase(int batchSize, int maxRecipients) {
    return new NotifyFollowersUseCaseImpl(
        audience, dispatcher, meterRegistry, batchSize, maxRecipients);
  }

  private static List<PushTarget> targets(int fromInclusive, int toExclusive) {
    return IntStream.range(fromInclusive, toExclusive)
        .mapToObj(
            i ->
                new PushTarget(
                    String.format("usr_%03d", i),
                    new PushSubscription("https://push.example/" + i, "p256dh", "auth")))
        .toList();
  }

  private double counter(String result) {
    return meterRegistry.counter("wyrdly.push.fanout", "result", result).count();
  }

  @Test
  void noFollowersIsANoOp() {
    when(audience.findSubscribedFollowers(AUTHOR, "", 200)).thenReturn(List.of());

    useCase(200, 10_000).notifyFollowers(AUTHOR, MESSAGE);

    verify(dispatcher, never()).dispatchTo(any(), any());
    assertEquals(1.0, counter("completed"));
    assertEquals(0.0, counter("capped"));
  }

  @Test
  void dispatchesEveryTargetWithItsResolvedSubscription() {
    List<PushTarget> batch = targets(0, 3);
    when(audience.findSubscribedFollowers(AUTHOR, "", 200)).thenReturn(batch);

    useCase(200, 10_000).notifyFollowers(AUTHOR, MESSAGE);

    ArgumentCaptor<PushSubscription> subs = ArgumentCaptor.forClass(PushSubscription.class);
    ArgumentCaptor<PushEvent> events = ArgumentCaptor.forClass(PushEvent.class);
    verify(dispatcher, times(3)).dispatchTo(subs.capture(), events.capture());
    for (int i = 0; i < 3; i++) {
      assertEquals(batch.get(i).subscription(), subs.getAllValues().get(i));
      PushEvent event = events.getAllValues().get(i);
      assertEquals(batch.get(i).userId(), event.recipientUserId());
      assertEquals("NEW_POST_FROM_FOLLOWED", event.type());
      assertEquals("/posts/pst_1", event.deepLink());
    }
    // A partial batch means the audience is exhausted: no second query.
    verify(audience, times(1)).findSubscribedFollowers(anyString(), anyString(), anyInt());
    assertEquals(3.0, meterRegistry.counter("wyrdly.push.fanout.recipients").count());
  }

  @Test
  void pagesThroughAudienceWithKeysetCursor() {
    when(audience.findSubscribedFollowers(AUTHOR, "", 2)).thenReturn(targets(0, 2));
    when(audience.findSubscribedFollowers(AUTHOR, "usr_001", 2)).thenReturn(targets(2, 4));
    when(audience.findSubscribedFollowers(AUTHOR, "usr_003", 2)).thenReturn(targets(4, 5));

    useCase(2, 10_000).notifyFollowers(AUTHOR, MESSAGE);

    verify(dispatcher, times(5)).dispatchTo(any(), any());
    verify(audience, times(3)).findSubscribedFollowers(eq(AUTHOR), anyString(), eq(2));
    assertEquals(1.0, counter("completed"));
  }

  @Test
  void stopsAtMaxRecipientsAndReportsCapped() {
    when(audience.findSubscribedFollowers(AUTHOR, "", 2)).thenReturn(targets(0, 2));
    when(audience.findSubscribedFollowers(AUTHOR, "usr_001", 1)).thenReturn(targets(2, 3));

    useCase(2, 3).notifyFollowers(AUTHOR, MESSAGE);

    verify(dispatcher, times(3)).dispatchTo(any(), any());
    verify(audience, never()).findSubscribedFollowers(AUTHOR, "usr_002", 2);
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
              // Completes asynchronously, a bit later, like the real dispatcher pool.
              return CompletableFuture.runAsync(() -> sleep(50))
                  .thenRun(() -> timeline.add("done"));
            });
    when(audience.findSubscribedFollowers(AUTHOR, "", 1))
        .thenAnswer(
            inv -> {
              timeline.add("query");
              return targets(0, 1);
            });
    when(audience.findSubscribedFollowers(AUTHOR, "usr_000", 1))
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
