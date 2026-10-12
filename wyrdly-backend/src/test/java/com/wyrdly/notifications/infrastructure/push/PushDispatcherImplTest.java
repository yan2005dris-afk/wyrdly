package com.wyrdly.notifications.infrastructure.push;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushSubscription;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PushDispatcherImplTest {

  @InjectMock PushSubscriptionRepositoryPort subscriptionRepository;
  @InjectMock WebPushLibrarySender librarySender;

  @Inject PushDispatcherImpl dispatcher;
  @Inject MeterRegistry meterRegistry;
  @Inject org.eclipse.microprofile.context.ManagedExecutor managedExecutor;

  @Test
  void incrementsOkCounterOnTwoHundredResponse() throws Exception {
    String userId = "usr_ok";
    PushSubscription sub = subscriptionFor(userId);
    when(subscriptionRepository.findAllByUserId(userId)).thenReturn(java.util.List.of(sub));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(201);

    double before = counterValue("result", "ok");

    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "ok", before, 1);
    verify(subscriptionRepository, never()).deleteByUserId(userId);
  }

  @Test
  void cleansUpSubscriptionAndIncrementsGoneCounterOnFourHundredAndTen() throws Exception {
    String userId = "usr_gone";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(410);

    double before = counterValue("result", "gone");

    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "gone", before, 1);
    // Only the failed endpoint is removed; sibling devices keep working.
    await()
        .atMost(java.time.Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                verify(subscriptionRepository, times(1))
                    .deleteByEndpoint(eq(userId), any(String.class)));
    verify(subscriptionRepository, never()).deleteByUserId(userId);
  }

  @Test
  void cleansUpSubscriptionOnFourHundredFourToo() throws Exception {
    String userId = "usr_404";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(404);

    double before = counterValue("result", "gone");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "gone", before, 1);
    await()
        .atMost(java.time.Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                verify(subscriptionRepository, times(1))
                    .deleteByEndpoint(eq(userId), any(String.class)));
  }

  @Test
  void incrementsClientErrorOnOtherFourHundred() throws Exception {
    String userId = "usr_400";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(400);

    double before = counterValue("result", "http_4xx");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "http_4xx", before, 1);
    verify(subscriptionRepository, never()).deleteByUserId(userId);
  }

  @Test
  void retriesThenIncrementsServerErrorOnFiveHundred() throws Exception {
    String userId = "usr_500";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    AtomicInteger calls = new AtomicInteger();
    when(librarySender.send(any(PushSubscription.class), any(byte[].class)))
        .thenAnswer(
            inv -> {
              calls.incrementAndGet();
              return 503;
            });

    double before = counterValue("result", "http_5xx");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "http_5xx", before, 1);
    assertTrue(
        calls.get() >= 2, "expected at least 2 attempts (initial + retries), got " + calls.get());
    verify(subscriptionRepository, never()).deleteByUserId(userId);
  }

  @Test
  void incrementsSkippedWhenNoSubscriptionStored() throws Exception {
    String userId = "usr_nope";
    when(subscriptionRepository.findAllByUserId(userId)).thenReturn(java.util.List.of());

    double before = counterValue("result", "skipped_no_subscription");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "skipped_no_subscription", before, 1);
    verify(librarySender, never()).send(any(PushSubscription.class), any(byte[].class));
  }

  @Test
  void incrementsFailedCounterOnSenderFailure() throws Exception {
    String userId = "usr_sendfail";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class)))
        .thenThrow(new RuntimeException("library boom"));

    double before = counterValue("result", "failed");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "failed", before, 1);
  }

  @Test
  void dispatchToUsesResolvedSubscriptionWithoutLookup() throws Exception {
    String userId = "usr_fanout";
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(201);

    double before = counterValue("result", "ok");
    dispatcher
        .dispatchTo(subscriptionFor(userId), newEvent(userId))
        .get(5, java.util.concurrent.TimeUnit.SECONDS);

    awaitCounterIncrease("result", "ok", before, 1);
    verify(subscriptionRepository, never()).findAllByUserId(userId);
  }

  @Test
  void dispatchToFutureCompletesNormallyEvenWhenPushFails() throws Exception {
    String userId = "usr_fanout_fail";
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(410);

    double before = counterValue("result", "gone");
    dispatcher
        .dispatchTo(subscriptionFor(userId), newEvent(userId))
        .get(5, java.util.concurrent.TimeUnit.SECONDS);

    awaitCounterIncrease("result", "gone", before, 1);
    verify(subscriptionRepository, times(1)).deleteByEndpoint(eq(userId), any(String.class));
  }

  @Test
  void dispatchFansOutToEveryDeviceAndCleansOnlyTheGoneEndpoint() throws Exception {
    String userId = "usr_multidevice";
    PushSubscription firefox = subscriptionFor(userId);
    PushSubscription chrome =
        new PushSubscription(
            "https://fcm.googleapis.com/fcm/send/other",
            firefox.p256dh(),
            firefox.auth());
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(firefox, chrome));
    // First device answers 410 (stale), second answers 201.
    when(librarySender.send(eq(firefox), any(byte[].class))).thenReturn(410);
    when(librarySender.send(eq(chrome), any(byte[].class))).thenReturn(201);

    double beforeOk = counterValue("result", "ok");
    double beforeGone = counterValue("result", "gone");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "ok", beforeOk, 1);
    awaitCounterIncrease("result", "gone", beforeGone, 1);
    verify(librarySender, times(1)).send(eq(firefox), any(byte[].class));
    verify(librarySender, times(1)).send(eq(chrome), any(byte[].class));
    verify(subscriptionRepository, times(1)).deleteByEndpoint(userId, firefox.endpoint());
    verify(subscriptionRepository, never()).deleteByUserId(userId);
  }

  @Test
  void forwardsEncodedPayloadToLibrarySender() throws Exception {
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(201);

    dispatcher
        .dispatchTo(subscriptionFor("usr_payload"), newEvent("usr_payload"))
        .get(5, java.util.concurrent.TimeUnit.SECONDS);

    var captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
    verify(librarySender, times(1)).send(any(PushSubscription.class), captor.capture());
    String json = new String(captor.getValue(), StandardCharsets.UTF_8);
    assertTrue(json.contains("Alice le dio Like"), "payload must carry the title, got: " + json);
    assertTrue(json.contains("POST_LIKE"), "payload must carry the type, got: " + json);
  }

  @Test
  void reportsActiveSubscriptionsGauge() {
    when(subscriptionRepository.countActive()).thenReturn(7L);
    assertEquals(7.0, meterRegistry.find("wyrdly.push.subscriptions").gauge().value());
  }

  @Test
  void recordsDurationAndBytesMetricsOnSuccess() throws Exception {
    String userId = "usr_metrics";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(201);

    double beforeOk = counterValue("result", "ok");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "ok", beforeOk, 1);
    assertNotNull(
        meterRegistry
            .find("wyrdly.push.dispatch.duration")
            .tag("type", "POST_LIKE")
            .tag("result", "ok")
            .timer());
    assertNotNull(
        meterRegistry.find("wyrdly.push.dispatch.bytes").tag("type", "POST_LIKE").summary());
  }

  @Test
  void rateLimitsRapidPushesToSameRecipient() throws Exception {
    String userId = "usr_storm";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class))).thenReturn(201);

    double beforeOk = counterValue("result", "ok");
    double beforeRateLimited = counterValue("result", "rate_limited");

    // Send 100 rapid pushes to the same recipient in a tight loop
    for (int i = 0; i < 100; i++) {
      dispatcher.dispatch(newEvent(userId));
    }

    awaitCounterIncrease("result", "ok", beforeOk, 1);
    awaitCounterIncrease("result", "rate_limited", beforeRateLimited, 99);

    // Verify library sender was only invoked once for this recipient
    verify(librarySender, times(1)).send(any(PushSubscription.class), any(byte[].class));
  }

  @Test
  void rateLimitsWhenGlobalCapacityExceeded() {
    PushDispatcherImpl customDispatcher =
        new PushDispatcherImpl(
            subscriptionRepository,
            librarySender,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            meterRegistry,
            1,
            10,
            10, // global rate limit = 10
            0); // recipient rate limit disabled

    double before = counterValue("result", "rate_limited");
    for (int i = 0; i < 25; i++) {
      customDispatcher.dispatch(newEvent("usr_global_" + i));
    }
    assertEquals(before + 15, counterValue("result", "rate_limited"), 0.0);
    customDispatcher.shutdown();
  }

  @Test
  void incrementsNetworkErrorCounterOnConnectionFailure() throws Exception {
    String userId = "usr_neterr";
    when(subscriptionRepository.findAllByUserId(userId))
        .thenReturn(java.util.List.of(subscriptionFor(userId)));
    when(librarySender.send(any(PushSubscription.class), any(byte[].class)))
        .thenThrow(new java.io.IOException("Connection refused"));

    double before = counterValue("result", "network_error");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "network_error", before, 1);
  }

  @Test
  void shutsDownGracefullyWithoutError() {
    // Use a dedicated local dispatcher (not the @Inject-ed one) so the
    // shared bean keeps working for the rest of the suite. JUnit 5 runs
    // tests by method-name hash, so this method may execute in the middle
    // of the class — shutting down the injected dispatcher would close the
    // backoff scheduler for every subsequent test.
    PushDispatcherImpl localDispatcher =
        new PushDispatcherImpl(
            subscriptionRepository,
            librarySender,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            meterRegistry,
            1,
            10,
            1000,
            0);
    assertDoesNotThrow(localDispatcher::shutdown);
  }

  // ---- helpers ------------------------------------------------------------

  private static PushEvent newEvent(String userId) {
    return new PushEvent(
        userId,
        "POST_LIKE",
        "Alice le dio Like a tu publicacion",
        "snippet",
        "/posts/pst_abc",
        java.util.Map.of("postId", "pst_abc"));
  }

  private static PushSubscription subscriptionFor(String userId) throws Exception {
    // Use a real, freshly generated browser key pair so the encryption round-trip works.
    java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC");
    g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
    java.security.KeyPair pair = g.generateKeyPair();
    java.security.interfaces.ECPublicKey pub =
        (java.security.interfaces.ECPublicKey) pair.getPublic();
    byte[] uncompressed = new byte[65];
    uncompressed[0] = 0x04;
    byte[] x = toFixed32(pub.getW().getAffineX().toByteArray());
    byte[] y = toFixed32(pub.getW().getAffineY().toByteArray());
    System.arraycopy(x, 0, uncompressed, 1, 32);
    System.arraycopy(y, 0, uncompressed, 33, 32);
    String peerB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
    String authB64Url =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("1234567890123456".getBytes(StandardCharsets.UTF_8));
    return new PushSubscription(
        "https://fcm.googleapis.com/fcm/send/dK98s", peerB64Url, authB64Url);
  }

  private static byte[] toFixed32(byte[] src) {
    if (src.length == 32) return src;
    byte[] dst = new byte[32];
    if (src.length > 32) System.arraycopy(src, src.length - 32, dst, 0, 32);
    else System.arraycopy(src, 0, dst, 32 - src.length, src.length);
    return dst;
  }

  private double counterValue(String resultTag, String resultValue) {
    return meterRegistry.find("wyrdly.push.dispatch").tag("result", resultValue).counters().stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  /**
   * Poll until the counter for {@code result=<resultValue>} increases by at least {@code delta}.
   */
  private void awaitCounterIncrease(String tag, String value, double before, double delta)
      throws InterruptedException {
    for (int i = 0; i < 500; i++) {
      double now = counterValue(tag, value);
      if (now >= before + delta) return;
      Thread.sleep(20);
    }
    throw new AssertionError(
        "Counter wyrdly.push.dispatch{result="
            + value
            + "} did not advance by "
            + delta
            + " (before="
            + before
            + ", now="
            + counterValue(tag, value)
            + ")");
  }
}
