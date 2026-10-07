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
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 *   <li>Dispatcher saturated (queue full) → push dropped, counter {@code result=rejected}.
 * </ul>
 *
 * <p>All HTTP I/O runs on a dedicated, bounded {@link ThreadPoolExecutor} so {@link #dispatch}
 * returns to the caller immediately. The queue is bounded ({@code
 * wyrdly.push.dispatch.queue-capacity}) so a large fan-out cannot exhaust the heap: Web Push is
 * best-effort, so dropping a push under saturation is preferred over an OOM.
 *
 * <p>The parsed VAPID signing key and the VAPID JWT per Push Service origin are cached: a fan-out
 * to thousands of recipients signs a handful of JWTs instead of one per push.
 */
@ApplicationScoped
public class PushDispatcherImpl implements PushDispatcherPort {

  private static final Logger LOG = Logger.getLogger(PushDispatcherImpl.class.getName());

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
  private final ThreadPoolExecutor executor;
  private final ConcurrentMap<String, CachedJwt> jwtCache = new ConcurrentHashMap<>();
  private volatile CachedSigningKey signingKeyCache;

  private Counter okCounter;
  private Counter goneCounter;
  private Counter clientErrorCounter;
  private Counter serverErrorCounter;
  private Counter skippedCounter;
  private Counter failedCounter;
  private Counter rejectedCounter;

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
          long initialBackoffMs,
      @ConfigProperty(name = "wyrdly.push.dispatch.pool-size", defaultValue = "8") int poolSize,
      @ConfigProperty(name = "wyrdly.push.dispatch.queue-capacity", defaultValue = "5000")
          int queueCapacity) {
    this.vapidKeyProvider = vapidKeyProvider;
    this.subscriptionRepository = subscriptionRepository;
    this.gatewayClient = gatewayClient;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.subject = subject;
    this.maxRetries = maxRetries;
    this.initialBackoff = Duration.ofMillis(initialBackoffMs);
    AtomicInteger threadIndex = new AtomicInteger();
    this.executor =
        new ThreadPoolExecutor(
            poolSize,
            poolSize,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            r -> {
              Thread t = new Thread(r, "push-dispatcher-" + threadIndex.incrementAndGet());
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
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
    this.rejectedCounter = meterRegistry.counter("wyrdly.push.dispatch", "result", "rejected");
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException ex) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void dispatch(PushEvent event) {
    submit(
        () -> {
          PushSubscription subscription =
              subscriptionRepository.findByUserId(event.recipientUserId());
          if (subscription == null) {
            skippedCounter.increment();
            LOG.log(Level.FINE, "no subscription for user {0}", event.recipientUserId());
            return;
          }
          send(subscription, event);
        });
  }

  @Override
  public CompletableFuture<Void> dispatchTo(PushSubscription subscription, PushEvent event) {
    return submit(() -> send(subscription, event));
  }

  /** Runs {@code task} on the dispatcher pool; the returned future always completes normally. */
  private CompletableFuture<Void> submit(Runnable task) {
    CompletableFuture<Void> done = new CompletableFuture<>();
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } catch (RuntimeException ex) {
              failedCounter.increment();
              LOG.log(Level.WARNING, "push dispatch task failed", ex);
            } finally {
              done.complete(null);
            }
          });
    } catch (RejectedExecutionException ex) {
      rejectedCounter.increment();
      LOG.log(Level.WARNING, "push dispatcher saturated; push dropped");
      done.complete(null);
    }
    return done;
  }

  private void send(PushSubscription subscription, PushEvent event) {
    try {
      byte[] plaintext = encodePayload(event);
      byte[] ciphertext =
          MessageEncryptor.encrypt(plaintext, subscription.p256dh(), subscription.auth());

      String jwt = jwtFor(extractOrigin(subscription.endpoint()));

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
}
