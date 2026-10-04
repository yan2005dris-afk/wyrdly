package com.wyrdly.notifications.infrastructure.push;

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
    verify(subscriptionRepository, times(1)).deleteByUserId(userId);
  }

  @Test
  void cleansUpSubscriptionOnFourHundredFourToo() throws Exception {
    String userId = "usr_404";
    when(subscriptionRepository.findByUserId(userId)).thenReturn(subscriptionFor(userId));
    when(gatewayClient.post(any(URI.class), any(byte[].class), anyMap())).thenReturn(404);

    double before = counterValue("result", "gone");
    dispatcher.dispatch(newEvent(userId));

    awaitCounterIncrease("result", "gone", before, 1);
    verify(subscriptionRepository, times(1)).deleteByUserId(userId);
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
    assertEquals("aes128gcm", headers.get("Content-Encoding"));
    String authz = headers.get("Authorization");
    assertTrue(
        authz.startsWith("vapid t="), "Authorization must start with 'vapid t=', got " + authz);
    assertTrue(
        authz.contains(",k="), "Authorization must include the public key ',k=' got: " + authz);
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
    Counter counter =
        meterRegistry
            .find("wyrdly.push.dispatch")
            .tags(
                "result",
                resultTag.equals("result") ? resultValue : resultTag,
                "result",
                resultValue)
            .counter();
    return counter == null ? 0.0 : counter.count();
  }

  /**
   * Poll until the counter for {@code result=<resultValue>} increases by at least {@code delta}.
   */
  private void awaitCounterIncrease(String tag, String value, double before, double delta)
      throws InterruptedException {
    for (int i = 0; i < 500; i++) {
      double now =
          meterRegistry.find("wyrdly.push.dispatch").tag("result", value).counter().count();
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
            + meterRegistry.find("wyrdly.push.dispatch").tag("result", value).counter().count()
            + ")");
  }

  /** Holds the headers captured by the last gateway call so tests can assert them. */
  private static final ThreadLocal<java.util.Map<String, String>> headersHolder =
      new ThreadLocal<>();
}
