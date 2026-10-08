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
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

/**
 * Fire-and-forget Web Push dispatcher. Encrypts the payload per RFC 8291, signs a VAPID JWT per RFC
 * 8292, POSTs to the recipient's Push Service URL, and handles the response:
 *
 * <ul>
 *   <li>201 Created → success counter incremented.
 *   <li>404 / 410 → subscription cleaned up (counter: {@code result=gone}).
 *   <li>4xx → counter {@code result=http_4xx} (likely a payload / header bug).
 *   <li>5xx → counter {@code result=http_5xx} after retries with exponential backoff.
 *   <li>Network error → counter {@code result=network_error}.
 *   <li>No subscription stored → counter {@code result=skipped_no_subscription}.
 *   <li>Rate limited → counter {@code result=rate_limited}.
 *   <li>Queue full / rejected → counter {@code result=rejected} (backpressure).
 * </ul>
 *
 * <p>All HTTP I/O runs on a container-managed {@link ManagedExecutor} so {@link #dispatch} returns
 * to the caller immediately. Rate limiting, backpressure protection, and Micrometer telemetry are
 * built-in.
 *
 * <p>The parsed VAPID signing key and the VAPID JWT per Push Service origin are cached: a fan-out
 * to thousands of recipients signs a handful of JWTs instead of one per push.
 */
@ApplicationScoped
public class PushDispatcherImpl implements PushDispatcherPort {

  private static final Logger LOG = Logger.getLogger(PushDispatcherImpl.class.getName());

  private static final long SHUTDOWN_TIMEOUT_SECONDS = 2;

  /** Lifetime of a signed VAPID JWT (RFC 8292 caps it at 24h). */
  static final Duration JWT_TTL = Duration.ofMinutes(10);

  /** A cached JWT is re-signed once it gets this close to expiring. */
  static final Duration JWT_REFRESH_MARGIN = Duration.ofMinutes(2);

  private final VapidKeyProvider vapidKeyProvider;
  private final PushSubscriptionRepositoryPort subscriptionRepository;
  private final PushGatewayClientPort gatewayClient;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  private final String subject;
  private final int maxRetries;
  private final Duration initialBackoff;
  private final ManagedExecutor executor;
  private final ConcurrentMap<String, CachedJwt> jwtCache = new ConcurrentHashMap<>();
  private volatile CachedSigningKey signingKeyCache;

  private final long recipientRateIntervalMs;
  private final TokenBucket globalTokenBucket;
  private final ConcurrentHashMap<String, AtomicLong> lastPushTimeByRecipient =
      new ConcurrentHashMap<>();

  @Inject
  public PushDispatcherImpl(
      VapidKeyProvider vapidKeyProvider,
      PushSubscriptionRepositoryPort subscriptionRepository,
      PushGatewayClientPort gatewayClient,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      ManagedExecutor executor,
      @ConfigProperty(name = "wyrdly.push.vapid.subject", defaultValue = "mailto:ops@wyrdly.com")
          String subject,
      @ConfigProperty(name = "wyrdly.push.dispatch.max-retries", defaultValue = "3") int maxRetries,
      @ConfigProperty(name = "wyrdly.push.dispatch.initial-backoff-ms", defaultValue = "200")
          long initialBackoffMs,
      @ConfigProperty(name = "wyrdly.push.dispatch.global-rate-per-second", defaultValue = "1000")
          long globalRatePerSecond,
      @ConfigProperty(
              name = "wyrdly.push.dispatch.recipient-rate-interval-ms",
              defaultValue = "1000")
          long recipientRateIntervalMs) {
    this.vapidKeyProvider = vapidKeyProvider;
    this.subscriptionRepository = subscriptionRepository;
    this.gatewayClient = gatewayClient;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.executor = executor;
    this.subject = subject;
    this.maxRetries = maxRetries;
    this.initialBackoff = Duration.ofMillis(initialBackoffMs);
    this.recipientRateIntervalMs = recipientRateIntervalMs;
    this.globalTokenBucket =
        globalRatePerSecond > 0 ? new TokenBucket(globalRatePerSecond, globalRatePerSecond) : null;
  }

  @PostConstruct
  void registerMetrics() {
    meterRegistry.gauge(
        "wyrdly.push.subscriptions",
        subscriptionRepository,
        PushSubscriptionRepositoryPort::countActive);
  }

  /**
   * Optional lifecycle fallback. In Quarkus runtime, {@link ManagedExecutor} lifecycle and thread
   * draining are managed by the container.
   */
  public void shutdown() {
    if (executor != null && !executor.isShutdown()) {
      try {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          executor.shutdownNow();
          if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            LOG.log(Level.WARNING, "push-dispatcher executor did not terminate");
          }
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
        try {
          executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      } catch (IllegalStateException | UnsupportedOperationException ignored) {
        // Container-managed executors may disallow manual lifecycle invocations
      }
    }
  }

  @Override
  public void dispatch(PushEvent event) {
    if (event == null || isRateLimited(event)) {
      return;
    }
    submit(
        typeOf(event),
        () -> {
          PushSubscription subscription =
              subscriptionRepository.findByUserId(event.recipientUserId());
          if (subscription == null) {
            recordDispatch(typeOf(event), "skipped_no_subscription");
            LOG.log(Level.FINE, "no subscription for user {0}", event.recipientUserId());
            return;
          }
          send(subscription, event);
        });
  }

  @Override
  public CompletableFuture<Void> dispatchTo(PushSubscription subscription, PushEvent event) {
    if (event == null || isRateLimited(event)) {
      return CompletableFuture.completedFuture(null);
    }
    return submit(typeOf(event), () -> send(subscription, event));
  }

  /** Records and logs a rate-limited push; {@code true} means the push must be dropped. */
  private boolean isRateLimited(PushEvent event) {
    if (isGlobalRateLimited()) {
      recordDispatch(typeOf(event), "rate_limited");
      LOG.log(Level.WARNING, "Global push dispatch rate limit exceeded");
      return true;
    }
    if (isRecipientRateLimited(event.recipientUserId())) {
      recordDispatch(typeOf(event), "rate_limited");
      LOG.log(Level.FINE, "Push rate limit exceeded for recipient {0}", event.recipientUserId());
      return true;
    }
    return false;
  }

  private boolean isGlobalRateLimited() {
    return globalTokenBucket != null && !globalTokenBucket.tryConsume();
  }

  private boolean isRecipientRateLimited(String recipientUserId) {
    if (recipientRateIntervalMs <= 0 || recipientUserId == null) {
      return false;
    }
    long now = System.nanoTime();
    long intervalNanos = TimeUnit.MILLISECONDS.toNanos(recipientRateIntervalMs);
    AtomicLong lastAllowedTime =
        lastPushTimeByRecipient.computeIfAbsent(recipientUserId, k -> new AtomicLong(0));
    while (true) {
      long last = lastAllowedTime.get();
      if (last > 0 && (now - last) < intervalNanos) {
        return true;
      }
      if (lastAllowedTime.compareAndSet(last, now)) {
        if (lastPushTimeByRecipient.size() > 10_000) {
          long cutoff = now - TimeUnit.MINUTES.toNanos(1);
          lastPushTimeByRecipient.entrySet().removeIf(e -> e.getValue().get() < cutoff);
        }
        return false;
      }
    }
  }

  /** Runs {@code task} on the dispatcher pool; the returned future always completes normally. */
  private CompletableFuture<Void> submit(String eventType, Runnable task) {
    CompletableFuture<Void> done = new CompletableFuture<>();
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } catch (RuntimeException ex) {
              recordDispatch(eventType, "failed");
              LOG.log(Level.WARNING, "push dispatch task failed", ex);
            } finally {
              done.complete(null);
            }
          });
    } catch (RejectedExecutionException ex) {
      recordDispatch(eventType, "rejected");
      LOG.log(Level.WARNING, "push dispatcher saturated; push dropped");
      done.complete(null);
    }
    return done;
  }

  private void send(PushSubscription subscription, PushEvent event) {
    String eventType = typeOf(event);
    try {
      byte[] plaintext = encodePayload(event);
      byte[] ciphertext =
          MessageEncryptor.encrypt(plaintext, subscription.p256dh(), subscription.auth());
      recordBytes(eventType, ciphertext.length);

      String jwt = jwtFor(extractOrigin(subscription.endpoint()));

      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("TTL", String.valueOf(60 * 60 * 24));
      headers.put("Urgency", "high");
      headers.put("Content-Encoding", "aes128gcm");
      headers.put("Content-Type", "application/octet-stream");
      headers.put("Authorization", "vapid t=" + jwt + ",k=" + vapidKeyProvider.getPublicKey());

      long startNanos = System.nanoTime();
      int status;
      try {
        status = dispatchWithRetries(subscription.endpoint(), ciphertext, headers);
      } catch (Exception ex) {
        long durationNanos = System.nanoTime() - startNanos;
        recordDuration(eventType, "network_error", durationNanos);
        recordDispatch(eventType, "network_error");
        LOG.log(
            Level.WARNING, "push gateway network error for user " + event.recipientUserId(), ex);
        return;
      }
      long durationNanos = System.nanoTime() - startNanos;

      switch (status / 100) {
        case 2:
          recordDuration(eventType, "ok", durationNanos);
          recordDispatch(eventType, "ok");
          return;
        case 4:
          if (status == 404 || status == 410) {
            recordDuration(eventType, "gone", durationNanos);
            recordDispatch(eventType, "gone");
            subscriptionRepository.deleteByUserId(event.recipientUserId());
            LOG.log(
                Level.INFO,
                "subscription gone for user {0} (HTTP {1}); cleaned up",
                new Object[] {event.recipientUserId(), status});
          } else {
            recordDuration(eventType, "http_4xx", durationNanos);
            recordDispatch(eventType, "http_4xx");
            LOG.log(
                Level.WARNING,
                "push gateway rejected payload for user {0} (HTTP {1})",
                new Object[] {event.recipientUserId(), status});
          }
          return;
        case 5:
          recordDuration(eventType, "http_5xx", durationNanos);
          recordDispatch(eventType, "http_5xx");
          return;
        default:
          recordDuration(eventType, "failed", durationNanos);
          recordDispatch(eventType, "failed");
      }
    } catch (Exception ex) {
      recordDispatch(eventType, "failed");
      LOG.log(Level.WARNING, "push dispatch failed for user " + event.recipientUserId(), ex);
    }
  }

  private static String typeOf(PushEvent event) {
    return event.type() != null ? event.type() : "unknown";
  }

  private void recordDispatch(String type, String result) {
    meterRegistry.counter("wyrdly.push.dispatch", "type", type, "result", result).increment();
  }

  private void recordDuration(String type, String result, long durationNanos) {
    Timer.builder("wyrdly.push.dispatch.duration")
        .tag("type", type)
        .tag("result", result)
        .register(meterRegistry)
        .record(Duration.ofNanos(durationNanos));
  }

  private void recordBytes(String type, int byteCount) {
    DistributionSummary.builder("wyrdly.push.dispatch.bytes")
        .tag("type", type)
        .baseUnit("bytes")
        .register(meterRegistry)
        .record(byteCount);
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

  /** Returns a cached VAPID JWT for {@code audience}, re-signing it shortly before it expires. */
  private String jwtFor(String audience) throws Exception {
    ECPrivateKey key = signingKey();
    Instant now = Instant.now();
    CachedJwt cached = jwtCache.get(audience);
    if (cached != null && cached.key() == key && now.isBefore(cached.refreshAt())) {
      return cached.token();
    }
    String token = VapidJwtSigner.sign(audience, subject, key, JWT_TTL);
    jwtCache.put(audience, new CachedJwt(token, key, now.plus(JWT_TTL).minus(JWT_REFRESH_MARGIN)));
    return token;
  }

  /** Parses the PKCS#8 VAPID private key once; re-parses only if the provider's key changes. */
  private ECPrivateKey signingKey() throws Exception {
    String encoded = vapidKeyProvider.getPrivateKey();
    CachedSigningKey cached = signingKeyCache;
    if (cached != null && cached.encoded().equals(encoded)) {
      return cached.key();
    }
    ECPrivateKey key =
        (ECPrivateKey)
            KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getUrlDecoder().decode(encoded)));
    signingKeyCache = new CachedSigningKey(encoded, key);
    return key;
  }

  private record CachedSigningKey(String encoded, ECPrivateKey key) {}

  private record CachedJwt(String token, ECPrivateKey key, Instant refreshAt) {}

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

  static class TokenBucket {
    private final long capacity;
    private final double tokensPerNano;
    private double availableTokens;
    private long lastRefillNanos;

    TokenBucket(long capacity, long ratePerSecond) {
      this.capacity = capacity;
      this.tokensPerNano = (double) ratePerSecond / 1_000_000_000.0;
      this.availableTokens = capacity;
      this.lastRefillNanos = System.nanoTime();
    }

    synchronized boolean tryConsume() {
      long now = System.nanoTime();
      long elapsed = Math.max(0, now - lastRefillNanos);
      lastRefillNanos = now;
      availableTokens = Math.min(capacity, availableTokens + elapsed * tokensPerNano);
      if (availableTokens >= 1.0) {
        availableTokens -= 1.0;
        return true;
      }
      return false;
    }
  }
}
