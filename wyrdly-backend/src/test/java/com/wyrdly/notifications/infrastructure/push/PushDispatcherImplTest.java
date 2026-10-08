package com.wyrdly.notifications.infrastructure.push;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.port.PushGatewayClientPort;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PushDispatcherImplTest {

  @InjectMock PushSubscriptionRepositoryPort subscriptionRepository;
  @InjectMock PushGatewayClientPort gatewayClient;

  @Inject PushDispatcherImpl dispatcher;
  @Inject VapidKeyProvider vapidKeyProvider;
  @Inject MeterRegistry meterRegistry;
  @Inject org.eclipse.microprofile.context.ManagedExecutor managedExecutor;

  @BeforeEach
  void resetThreadLocal() {
    headersHolder.remove();
  }

  @Test
  void incrementsOkCounterOnTwoHundredResponse() throws Exception {
    String userId = "usr_ok";
    PushSubscription sub = subscriptionFor(userId);
    when(subscriptionRepository.findByUserId(userId)).thenReturn(sub);
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(201);

    double before = counterValue("result", "ok");

    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "ok", before, 1);
    verify(subscriptionRepository, never()).deleteByUserId(userId);
  }

  @Test
  void cleansUpSubscriptionAndIncrementsGoneCounterOnFourHundredAndTen() throws Exception {
    String userId = "usr_gone";
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(410);

    double before = counterValue("result", "gone");

    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "gone", before, 1);
    // The dispatcher runs on a dedicated executor; deleteByUserId is invoked
    // immediately after the counter increment but in a separate tick of the
    // worker. await() avoids the rare race where verify() runs before the
    // async side effect has been recorded on the mock.
    await()
        .atMost(java.time.Duration.ofSeconds(5))
        .untilAsserted(() -> verify(subscriptionRepository, times(1)).deleteByUserId(userId));
  }

  @Test
  void cleansUpSubscriptionOnFourHundredFourToo() throws Exception {
    String userId = "usr_404";
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(404);

    double before = counterValue("result", "gone");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "gone", before, 1);
    await()
        .atMost(java.time.Duration.ofSeconds(5))
        .untilAsserted(() -> verify(subscriptionRepository, times(1)).deleteByUserId(userId));
  }

  @Test
  void incrementsClientErrorOnOtherFourHundred() throws Exception {
    String userId = "usr_400";
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(400);

    double before = counterValue("result", "http_4xx");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "http_4xx", before, 1);
    verify(subscriptionRepository, never()).deleteByUserId(userId);
  }

  @Test
  void retriesThenIncrementsServerErrorOnFiveHundred() throws Exception {
    String userId = "usr_500";
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    AtomicInteger calls = new AtomicInteger();
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap()))
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
    when(subscriptionRepository.findByUserId(userId)).thenReturn(null);

    double before = counterValue("result", "skipped_no_subscription");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "skipped_no_subscription", before, 1);
    verify(gatewayClient, never()).post(any(URI.class), any(byte[].class), anyMap());
  }

  @Test
  void incrementsFailedCounterOnEncryptionFailure() throws Exception {
    String userId = "usr_encfail";
    when(subscriptionRepository.findByUserId(userId))
        .thenReturn(
            new PushSubscription(
                "https://fcm.googleapis.com/fcm/send/abc",
                "$$not-base64$$",
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])));

    double before = counterValue("result", "failed");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "failed", before, 1);
    verify(gatewayClient, never()).post(any(URI.class), any(byte[].class), anyMap());
  }

  @Test
  void dispatchToUsesResolvedSubscriptionWithoutLookup() throws Exception {
    String userId = "usr_fanout";
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(201);

    double before = counterValue("result", "ok");
    dispatcher
        .dispatchTo(subscriptionFor(userId), newEvent(userId))
        .get(5, java.util.concurrent.TimeUnit.SECONDS);

    awaitCounterIncrease("result", "ok", before, 1);
    verify(subscriptionRepository, never()).findByUserId(userId);
  }

  @Test
  void dispatchToFutureCompletesNormallyEvenWhenPushFails() throws Exception {
    String userId = "usr_fanout_fail";
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(410);

    double before = counterValue("result", "gone");
    dispatcher
        .dispatchTo(subscriptionFor(userId), newEvent(userId))
        .get(5, java.util.concurrent.TimeUnit.SECONDS);

    awaitCounterIncrease("result", "gone", before, 1);
    verify(subscriptionRepository, times(1)).deleteByUserId(userId);
  }

  @Test
  void reusesCachedVapidJwtForTheSamePushServiceOrigin() throws Exception {
    java.util.concurrent.BlockingQueue<java.util.Map<String, String>> captured =
        new java.util.concurrent.LinkedBlockingQueue<>();
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap()))
        .thenAnswer(
            inv -> {
              captured.add(inv.getArgument(2));
              return 201;
            });

    dispatcher
        .dispatchTo(subscriptionFor("usr_jwt_1"), newEvent("usr_jwt_1"))
        .get(5, java.util.concurrent.TimeUnit.SECONDS);
    dispatcher
        .dispatchTo(subscriptionFor("usr_jwt_2"), newEvent("usr_jwt_2"))
        .get(5, java.util.concurrent.TimeUnit.SECONDS);

    java.util.Map<String, String> first = captured.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    java.util.Map<String, String> second = captured.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    assertNotNull(first);
    assertNotNull(second);
    assertEquals(first.get("Authorization"), second.get("Authorization"));
  }

  @Test
  void payloadContainsVapidAuthorizationHeaderOnSuccess() throws Exception {
    String userId = "usr_hdr";
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));

    java.util.concurrent.BlockingQueue<java.util.Map<String, String>> captured =
        new java.util.concurrent.LinkedBlockingQueue<>();
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap()))
        .thenAnswer(
            inv -> {
              captured.add(inv.getArgument(2));
              return 201;
            });

    dispatcher.dispatch(newEvent(userId));

    // Block until the dispatcher posts (this is what proves the async path actually
    // went through the gateway client with the right payload).
    java.util.Map<String, String> headers = captured.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    assertNotNull(headers, "headers should have been captured by the gateway mock");
    assertEquals("high", headers.get("Urgency"));
    assertEquals("aes128gcm", headers.get("Content-Encoding"));
    String authz = headers.get("Authorization");
    assertTrue(
        authz.startsWith("vapid t="), "Authorization must start with 'vapid t=', got " + authz);
    assertTrue(
        authz.contains(",k="), "Authorization must include the public key ',k=' got: " + authz);
  }

  @Test
  void reportsActiveSubscriptionsGauge() {
    when(subscriptionRepository.countActive()).thenReturn(7L);
    assertEquals(7.0, meterRegistry.find("wyrdly.push.subscriptions").gauge().value());
  }

  @Test
  void recordsDurationAndBytesMetricsOnSuccess() throws Exception {
    String userId = "usr_metrics";
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(201);

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
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(201);

    double beforeOk = counterValue("result", "ok");
    double beforeRateLimited = counterValue("result", "rate_limited");

    // Send 100 rapid pushes to the same recipient in a tight loop
    for (int i = 0; i < 100; i++) {
      dispatcher.dispatch(newEvent(userId));
    }

    awaitCounterIncrease("result", "ok", beforeOk, 1);
    awaitCounterIncrease("result", "rate_limited", beforeRateLimited, 99);

    // Verify gateway client was only invoked once for this recipient
    verify(gatewayClient, times(1)).post(any(URI.class), any(byte[].class), anyMap());
  }

  @Test
  void rateLimitsWhenGlobalCapacityExceeded() {
    PushDispatcherImpl customDispatcher =
        new PushDispatcherImpl(
            vapidKeyProvider,
            subscriptionRepository,
            gatewayClient,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            meterRegistry,
            managedExecutor,
            "mailto:ops@wyrdly.com",
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
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap()))
        .thenThrow(new java.io.IOException("Connection refused"));

    double before = counterValue("result", "network_error");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "network_error", before, 1);
  }

  @Test
  void handlesTaskRejectionGracefullyWhenQueueIsFull() throws Exception {
    org.eclipse.microprofile.context.ManagedExecutor rejectingExecutor =
        org.mockito.Mockito.mock(org.eclipse.microprofile.context.ManagedExecutor.class);
    org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException("Queue full"))
        .when(rejectingExecutor)
        .execute(any(Runnable.class));
    org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException("Queue full"))
        .when(rejectingExecutor)
        .submit(any(Runnable.class));

    PushDispatcherImpl rejectingDispatcher =
        new PushDispatcherImpl(
            vapidKeyProvider,
            subscriptionRepository,
            gatewayClient,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            meterRegistry,
            rejectingExecutor,
            "mailto:ops@wyrdly.com",
            1,
            10,
            1000,
            1000);

    double before = counterValue("result", "rejected");
    assertDoesNotThrow(() -> rejectingDispatcher.dispatch(newEvent("usr_rejected")));
    assertEquals(before + 1, counterValue("result", "rejected"), 0.0);
  }

  @Test
  void shutsDownGracefullyWithoutError() {
    assertDoesNotThrow(dispatcher::shutdown);
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

  /** Holds the headers captured by the last gateway call so tests can assert them. */
  private static final ThreadLocal<java.util.Map<String, String>> headersHolder =
      new ThreadLocal<>();
}
