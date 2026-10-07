package com.wyrdly.notifications.infrastructure.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.application.port.PushGatewayClientPort;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.infrastructure.crypto.MessageEncryptor;
import com.wyrdly.notifications.infrastructure.crypto.VapidJwtSigner;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Fire-and-forget Web Push dispatcher. Encrypts the payload per RFC 8291, signs a VAPID JWT per RFC
 * 8292, POSTs to the recipient's Push Service URL, and handles the response:
 *
 * <ul>
 *   <li>201 Created → success counter incremented.
 *   <li>404 / 410 → subscription cleaned up (counter: {@code result=gone}).
 *   <li>4xx → counter {@code result=http_4xx} (likely a payload / header bug).
 *   <li>5xx → counter {@code result=http_5xx} after retries with exponential backoff.
 *   <li>No subscription stored → counter {@code result=skipped_no_subscription}.
 * </ul>
 *
 * <p>All HTTP I/O runs on a dedicated {@link ExecutorService} so {@link #dispatch} returns to the
 * caller immediately. Health and metrics endpoints stay responsive even when the Push Service is
 * slow.
 */
@ApplicationScoped
public class PushDispatcherImpl implements PushDispatcherPort {

  private static final Logger LOG = Logger.getLogger(PushDispatcherImpl.class.getName());

  private final VapidKeyProvider vapidKeyProvider;
  private final PushSubscriptionRepositoryPort subscriptionRepository;
  private final PushGatewayClientPort gatewayClient;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  private final String subject;
  private final int maxRetries;
  private final Duration initialBackoff;
  private final ExecutorService executor;

  private Counter okCounter;
  private Counter goneCounter;
  private Counter clientErrorCounter;
  private Counter serverErrorCounter;
  private Counter skippedCounter;
  private Counter failedCounter;

  @Inject
  public PushDispatcherImpl(
      VapidKeyProvider vapidKeyProvider,
      PushSubscriptionRepositoryPort subscriptionRepository,
      PushGatewayClientPort gatewayClient,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @ConfigProperty(name = "wyrdly.push.vapid.subject", defaultValue = "mailto:ops@wyrdly.com")
          String subject,
      @ConfigProperty(name = "wyrdly.push.dispatch.max-retries", defaultValue = "3") int maxRetries,
      @ConfigProperty(name = "wyrdly.push.dispatch.initial-backoff-ms", defaultValue = "200")
          long initialBackoffMs) {
    this.vapidKeyProvider = vapidKeyProvider;
    this.subscriptionRepository = subscriptionRepository;
    this.gatewayClient = gatewayClient;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.subject = subject;
    this.maxRetries = maxRetries;
    this.initialBackoff = Duration.ofMillis(initialBackoffMs);
    this.executor =
        Executors.newFixedThreadPool(
            4,
            r -> {
              Thread t = new Thread(r, "push-dispatcher");
              t.setDaemon(true);
              return t;
            });
  }

  @PostConstruct
  void registerMetrics() {
    this.okCounter = meterRegistry.counter("wyrdly.push.dispatch", "result", "ok");
    this.goneCounter = meterRegistry.counter("wyrdly.push.dispatch", "result", "gone");
    this.clientErrorCounter = meterRegistry.counter("wyrdly.push.dispatch", "result", "http_4xx");
    this.serverErrorCounter = meterRegistry.counter("wyrdly.push.dispatch", "result", "http_5xx");
    this.skippedCounter =
        meterRegistry.counter("wyrdly.push.dispatch", "result", "skipped_no_subscription");
    this.failedCounter = meterRegistry.counter("wyrdly.push.dispatch", "result", "failed");
  }

  public void dispatch(PushEvent event) {
    executor.submit(() -> doDispatch(event));
  }

  private void doDispatch(PushEvent event) {
    PushSubscription subscription = subscriptionRepository.findByUserId(event.recipientUserId());
    if (subscription == null) {
      skippedCounter.increment();
      LOG.log(Level.FINE, "no subscription for user {0}", event.recipientUserId());
      return;
    }

    try {
      byte[] plaintext = encodePayload(event);
      byte[] ciphertext =
          MessageEncryptor.encrypt(plaintext, subscription.p256dh(), subscription.auth());

      String audience = extractOrigin(subscription.endpoint());
      ECPrivateKey signingKey =
          (ECPrivateKey)
              java.security.KeyFactory.getInstance("EC")
                  .generatePrivate(
                      new java.security.spec.PKCS8EncodedKeySpec(
                          java.util.Base64.getUrlDecoder()
                              .decode(vapidKeyProvider.getPrivateKey())));
      String jwt = VapidJwtSigner.sign(audience, subject, signingKey);

      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("TTL", String.valueOf(60 * 60 * 24));
      headers.put("Content-Encoding", "aes128gcm");
      headers.put("Content-Type", "application/octet-stream");
      headers.put("Authorization", "vapid t=" + jwt + ",k=" + vapidKeyProvider.getPublicKey());

      int status = dispatchWithRetries(subscription.endpoint(), ciphertext, headers);

      switch (status / 100) {
        case 2:
          okCounter.increment();
          return;
        case 4:
          if (status == 404 || status == 410) {
            goneCounter.increment();
            subscriptionRepository.deleteByUserId(event.recipientUserId());
            LOG.log(
                Level.INFO,
                "subscription gone for user {0} (HTTP {1}); cleaned up",
                new Object[] {event.recipientUserId(), status});
          } else {
            clientErrorCounter.increment();
            LOG.log(
                Level.WARNING,
                "push gateway rejected payload for user {0} (HTTP {1})",
                new Object[] {event.recipientUserId(), status});
          }
          return;
        case 5:
          serverErrorCounter.increment();
          return;
        default:
          failedCounter.increment();
      }
    } catch (Exception ex) {
      failedCounter.increment();
      LOG.log(Level.WARNING, "push dispatch failed for user " + event.recipientUserId(), ex);
    }
  }

  /** Returns the highest HTTP status seen (last attempt if all 5xx, else first 4xx). */
  private int dispatchWithRetries(String endpoint, byte[] body, Map<String, String> headers)
      throws Exception {
    int status = 0;
    long backoffMs = initialBackoff.toMillis();
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        status = gatewayClient.post(URI.create(endpoint), body, headers);
      } catch (Exception ex) {
        if (attempt == maxRetries) {
          throw ex;
        }
        Thread.sleep(backoffMs);
        backoffMs *= 2;
        continue;
      }
      if (status < 500) {
        return status;
      }
      if (attempt < maxRetries) {
        Thread.sleep(backoffMs);
        backoffMs *= 2;
      }
    }
    return status;
  }

  private byte[] encodePayload(PushEvent event) throws JsonProcessingException {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("title", event.title());
    payload.put("body", event.body());
    payload.put("icon", "/icons/wyrdly-icon-192.png");
    payload.put("badge", "/icons/wyrdly-badge-72.png");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("url", event.deepLink() == null ? "/" : event.deepLink());
    data.put("type", event.type());
    event.data().forEach(data::putIfAbsent);
    payload.put("data", data);
    return objectMapper.writeValueAsBytes(payload);
  }

  /** Extracts scheme + host (+ optional port) from a Push Service URL for the JWT {@code aud}. */
  private static String extractOrigin(String url) {
    URI uri = URI.create(url);
    String origin = uri.getScheme() + "://" + uri.getHost();
    if (uri.getPort() != -1) {
      origin += ":" + uri.getPort();
    }
    return origin;
  }
}
