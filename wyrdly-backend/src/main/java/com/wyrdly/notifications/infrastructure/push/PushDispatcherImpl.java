package com.wyrdly.notifications.infrastructure.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushSubscription;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Fire-and-forget Web Push dispatcher. Sends the payload through {@link WebPushLibrarySender}
 * (proven {@code nl.martijndwars:web-push} encryption + VAPID signing per RFC 8291 / RFC 8292),
 * POSTs to the recipient's Push Service URL, and handles the response:
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
 * <p>All HTTP I/O runs on a dedicated {@link ScheduledExecutorService} (the {@code
 * push-dispatcher-backoff} pool) so {@link #dispatch} returns to the caller immediately and neither
 * the I/O call nor the retry backoff ever holds a thread from the Quarkus-shared {@link
 * org.eclipse.microprofile.context.ManagedExecutor}. Rate limiting, backpressure protection, and
 * Micrometer telemetry are built-in.
 */
@ApplicationScoped
public class PushDispatcherImpl implements PushDispatcherPort {

  private static final Logger LOG = Logger.getLogger(PushDispatcherImpl.class.getName());

  private static final long SHUTDOWN_TIMEOUT_SECONDS = 2;

  /**
   * Synthetic status for deterministic library failures (bad keys, crypto errors): they must not
   * be retried like network errors and they count as {@code result=failed}, never {@code
   * result=network_error}.
   */
  static final int SENDER_FAILED = -1;

  private final PushSubscriptionRepositoryPort subscriptionRepository;
  private final WebPushLibrarySender librarySender;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  private final int maxRetries;
  private final Duration initialBackoff;

  private final long recipientRateIntervalMs;
  private final TokenBucket globalTokenBucket;
  private final ConcurrentHashMap<String, AtomicLong> lastPushTimeByRecipient =
      new ConcurrentHashMap<>();

  /**
   * Dedicated scheduler for retry backoffs. Kept separate from the {@link ManagedExecutor} so a
   * {@code Thread.sleep} between HTTP attempts never holds a thread from the Quarkus-shared pool.
   * Daemon threads so JVM shutdown is not blocked.
   */
  private final ScheduledExecutorService backoffExecutor;

  @Inject
  public PushDispatcherImpl(
      PushSubscriptionRepositoryPort subscriptionRepository,
      WebPushLibrarySender librarySender,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @ConfigProperty(name = "wyrdly.push.dispatch.max-retries", defaultValue = "3") int maxRetries,
      @ConfigProperty(name = "wyrdly.push.dispatch.initial-backoff-ms", defaultValue = "200")
          long initialBackoffMs,
      @ConfigProperty(name = "wyrdly.push.dispatch.global-rate-per-second", defaultValue = "1000")
          long globalRatePerSecond,
      @ConfigProperty(
              name = "wyrdly.push.dispatch.recipient-rate-interval-ms",
              defaultValue = "1000")
          long recipientRateIntervalMs) {
    this.subscriptionRepository = subscriptionRepository;
    this.librarySender = librarySender;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.maxRetries = maxRetries;
    this.initialBackoff = Duration.ofMillis(initialBackoffMs);
    this.recipientRateIntervalMs = recipientRateIntervalMs;
    this.globalTokenBucket =
        globalRatePerSecond > 0 ? new TokenBucket(globalRatePerSecond, globalRatePerSecond) : null;
    this.backoffExecutor =
        Executors.newScheduledThreadPool(
            2,
            runnable -> {
              Thread thread = new Thread(runnable, "push-dispatcher-backoff");
              thread.setDaemon(true);
              return thread;
            });
  }

  @PostConstruct
  void registerMetrics() {
    meterRegistry.gauge(
        "wyrdly.push.subscriptions",
        subscriptionRepository,
        PushSubscriptionRepositoryPort::countActive);
  }

  /**
   * Drains the backoff scheduler on shutdown. The HTTP I/O and retry chain both run on this pool,
   * so a clean drain lets in-flight pushes finish (or fail with a sensible counter) before the JVM
   * exits.
   */
  public void shutdown() {
    if (backoffExecutor != null && !backoffExecutor.isShutdown()) {
      backoffExecutor.shutdown();
      try {
        if (!backoffExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          backoffExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        backoffExecutor.shutdownNow();
        Thread.currentThread().interrupt();
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
          List<PushSubscription> subscriptions =
              subscriptionRepository.findAllByUserId(event.recipientUserId());
          if (subscriptions.isEmpty()) {
            recordDispatch(typeOf(event), "skipped_no_subscription");
            LOG.log(Level.FINE, "no subscription for user {0}", event.recipientUserId());
            return;
          }
          // One device per subscription: a 410 on one endpoint must never wipe the others.
          for (PushSubscription subscription : subscriptions) {
            send(subscription, event);
          }
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

  /** Runs {@code task} on the backoff scheduler; the returned future always completes normally. */
  private CompletableFuture<Void> submit(String eventType, Runnable task) {
    CompletableFuture<Void> done = new CompletableFuture<>();
    try {
      backoffExecutor.execute(
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
      recordBytes(eventType, plaintext.length);

      long startNanos = System.nanoTime();
      int status;
      try {
        status = dispatchLibraryWithRetries(subscription, plaintext).get();
      } catch (Exception ex) {
        long durationNanos = System.nanoTime() - startNanos;
        recordDuration(eventType, "network_error", durationNanos);
        recordDispatch(eventType, "network_error");
        LOG.log(
            Level.WARNING, "push gateway network error for user " + event.recipientUserId(), ex);
        return;
      }
      long durationNanos = System.nanoTime() - startNanos;

      if (status == SENDER_FAILED) {
        recordDuration(eventType, "failed", durationNanos);
        recordDispatch(eventType, "failed");
        LOG.log(Level.WARNING, "push dispatch failed for user " + event.recipientUserId());
        return;
      }

      switch (status / 100) {
        case 2:
          recordDuration(eventType, "ok", durationNanos);
          recordDispatch(eventType, "ok");
          return;
        case 4:
          if (status == 404 || status == 410) {
            recordDuration(eventType, "gone", durationNanos);
            recordDispatch(eventType, "gone");
            subscriptionRepository.deleteByEndpoint(
                event.recipientUserId(), subscription.endpoint());
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

  /**
   * Returns a future that completes with the highest HTTP status seen (last attempt if all 5xx,
   * else first 4xx). Retries on 5xx and network errors with exponential backoff scheduled on {@link
   * #backoffExecutor}, so a blocking library send between attempts never blocks a thread from the
   * shared {@link ManagedExecutor}.
   */
  private CompletableFuture<Integer> dispatchLibraryWithRetries(
      PushSubscription subscription, byte[] plaintext) {
    CompletableFuture<Integer> result = new CompletableFuture<>();
    libraryAttempt(subscription, plaintext, 0, initialBackoff.toMillis(), result);
    return result;
  }

  /**
   * One library send, then either complete the future (terminal outcome) or schedule the next
   * attempt after the backoff.
   */
  private void libraryAttempt(
      PushSubscription subscription,
      byte[] plaintext,
      int attemptIndex,
      long backoffMs,
      CompletableFuture<Integer> result) {
    backoffExecutor.execute(
        () -> {
          int status;
          try {
            status = librarySender.send(subscription, plaintext);
          } catch (java.io.IOException | java.util.concurrent.TimeoutException ex) {
            if (attemptIndex >= maxRetries) {
              result.completeExceptionally(ex);
              return;
            }
            scheduleLibraryNext(subscription, plaintext, attemptIndex, backoffMs, result);
            return;
          } catch (Exception ex) {
            result.complete(SENDER_FAILED);
            return;
          }
          if (status < 500 || attemptIndex >= maxRetries) {
            result.complete(status);
            return;
          }
          scheduleLibraryNext(subscription, plaintext, attemptIndex, backoffMs, result);
        });
  }

  private void scheduleLibraryNext(
      PushSubscription subscription,
      byte[] plaintext,
      int attemptIndex,
      long backoffMs,
      CompletableFuture<Integer> result) {
    long nextBackoff = backoffMs * 2;
    backoffExecutor.schedule(
        () -> libraryAttempt(subscription, plaintext, attemptIndex + 1, nextBackoff, result),
        backoffMs,
        TimeUnit.MILLISECONDS);
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
