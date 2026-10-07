package com.wyrdly.notifications.application.usecase.impl;

import com.wyrdly.notifications.application.port.PushAudienceQueryPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.application.usecase.NotifyFollowersUseCase;
import com.wyrdly.notifications.domain.model.PushMessage;
import com.wyrdly.notifications.domain.model.PushTarget;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Batched fan-out of a {@link PushMessage} to the subscribed followers of an author.
 *
 * <p>The audience is read in keyset batches of {@code wyrdly.push.fanout.batch-size}. Each batch is
 * handed to the dispatcher and the loop waits for the whole batch to be handled before reading the
 * next one, which bounds memory and keeps a single large fan-out from flooding the dispatcher
 * queue. {@code wyrdly.push.fanout.max-recipients} caps the audience of a single message.
 */
@ApplicationScoped
public class NotifyFollowersUseCaseImpl implements NotifyFollowersUseCase {

  static final String METRIC = "wyrdly.push.fanout";

  private final PushAudienceQueryPort audience;
  private final PushDispatcherPort dispatcher;
  private final int batchSize;
  private final int maxRecipients;
  private final Counter completedCounter;
  private final Counter cappedCounter;
  private final Counter recipientsCounter;

  @Inject
  public NotifyFollowersUseCaseImpl(
      PushAudienceQueryPort audience,
      PushDispatcherPort dispatcher,
      MeterRegistry meterRegistry,
      @ConfigProperty(name = "wyrdly.push.fanout.batch-size", defaultValue = "200") int batchSize,
      @ConfigProperty(name = "wyrdly.push.fanout.max-recipients", defaultValue = "10000")
          int maxRecipients) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("wyrdly.push.fanout.batch-size must be > 0");
    }
    if (maxRecipients <= 0) {
      throw new IllegalArgumentException("wyrdly.push.fanout.max-recipients must be > 0");
    }
    this.audience = Objects.requireNonNull(audience, "audience must not be null");
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.batchSize = batchSize;
    this.maxRecipients = maxRecipients;
    this.completedCounter = meterRegistry.counter(METRIC, "result", "completed");
    this.cappedCounter = meterRegistry.counter(METRIC, "result", "capped");
    this.recipientsCounter = meterRegistry.counter(METRIC + ".recipients");
  }

  @Override
  public void notifyFollowers(String authorId, PushMessage message) {
    Objects.requireNonNull(authorId, "authorId must not be null");
    Objects.requireNonNull(message, "message must not be null");

    String cursor = "";
    int sent = 0;
    boolean exhausted = false;
    while (sent < maxRecipients) {
      int limit = Math.min(batchSize, maxRecipients - sent);
      List<PushTarget> batch = audience.findSubscribedFollowers(authorId, cursor, limit);
      if (batch.isEmpty()) {
        exhausted = true;
        break;
      }
      CompletableFuture.allOf(
              batch.stream()
                  .map(t -> dispatcher.dispatchTo(t.subscription(), message.toEvent(t.userId())))
                  .toArray(CompletableFuture[]::new))
          .join();
      sent += batch.size();
      recipientsCounter.increment(batch.size());
      cursor = batch.getLast().userId();
      if (batch.size() < limit) {
        exhausted = true;
        break;
      }
    }

    if (!exhausted) {
      cappedCounter.increment();
      Log.warnf(
          "push fan-out capped: authorId=%s type=%s recipients=%d", authorId, message.type(), sent);
    } else {
      completedCounter.increment();
      Log.debugf(
          "push fan-out completed: authorId=%s type=%s recipients=%d",
          authorId, message.type(), sent);
    }
  }
}
