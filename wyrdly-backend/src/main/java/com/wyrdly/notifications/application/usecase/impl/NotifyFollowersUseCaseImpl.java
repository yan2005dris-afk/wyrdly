package com.wyrdly.notifications.application.usecase.impl;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushAudienceQueryPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.application.usecase.NotifyFollowersUseCase;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushMessage;
import com.wyrdly.notifications.domain.model.PushTarget;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Batched fan-out of post publication notifications to all followers of an author.
 *
 * <p>For every follower:
 *
 * <ul>
 *   <li>Persists an in-app {@link Notification} in Neo4j.
 *   <li>Broadcasts the real-time event via {@link NotificationBroadcasterPort} (SSE).
 *   <li>If the follower has an active Web Push subscription, dispatches background push via {@link
 *       PushDispatcherPort}.
 * </ul>
 *
 * <p>The audience is read in keyset batches of {@code wyrdly.push.fanout.batch-size}. {@code
 * wyrdly.push.fanout.max-recipients} caps the audience of a single message.
 */
@ApplicationScoped
public class NotifyFollowersUseCaseImpl implements NotifyFollowersUseCase {

  static final String METRIC = "wyrdly.push.fanout";

  private final PushAudienceQueryPort audience;
  private final PushDispatcherPort dispatcher;
  private final NotificationBroadcasterPort broadcaster;
  private final NotificationRepository notificationRepository;
  private final int batchSize;
  private final int maxRecipients;
  private final Counter completedCounter;
  private final Counter cappedCounter;
  private final Counter recipientsCounter;

  @Inject
  public NotifyFollowersUseCaseImpl(
      PushAudienceQueryPort audience,
      PushDispatcherPort dispatcher,
      NotificationBroadcasterPort broadcaster,
      NotificationRepository notificationRepository,
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
    this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
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

    NotificationDto.ActorDto authorActorDto = buildAuthorActorDto(authorId, message);
    String postId = extractPostId(message);

    String cursor = "";
    int sent = 0;
    boolean exhausted = false;
    while (sent < maxRecipients) {
      int limit = Math.min(batchSize, maxRecipients - sent);
      List<PushTarget> batch = audience.findAllFollowers(authorId, cursor, limit);
      if (batch.isEmpty()) {
        exhausted = true;
        break;
      }

      List<CompletableFuture<?>> pushFutures = new ArrayList<>();

      for (PushTarget target : batch) {
        String followerId = target.userId();

        Notification notification =
            new Notification(
                nextId(),
                followerId,
                message.type(),
                authorId,
                message.title(),
                message.body(),
                message.deepLink(),
                postId,
                false,
                Instant.now());

        try {
          notificationRepository.save(notification);
        } catch (RuntimeException persistError) {
          Log.warnf(persistError, "Failed to persist new-post notification for %s", followerId);
        }

        NotificationDto ssePayload = NotificationDto.from(notification, authorActorDto);
        try {
          broadcaster.broadcast(followerId, ssePayload);
        } catch (RuntimeException sseError) {
          Log.debugf(
              sseError, "Failed to broadcast new-post notification to SSE for %s", followerId);
        }

        if (target.subscription() != null) {
          pushFutures.add(
              dispatcher.dispatchTo(target.subscription(), message.toEvent(followerId)));
        }
      }

      if (!pushFutures.isEmpty()) {
        CompletableFuture.allOf(pushFutures.toArray(CompletableFuture[]::new)).join();
      }

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
          "post notification fan-out capped: authorId=%s type=%s recipients=%d",
          authorId, message.type(), sent);
    } else {
      completedCounter.increment();
      Log.debugf(
          "post notification fan-out completed: authorId=%s type=%s recipients=%d",
          authorId, message.type(), sent);
    }
  }

  private static NotificationDto.ActorDto buildAuthorActorDto(
      String authorId, PushMessage message) {
    String username = null;
    if (message.data() != null && message.data().get("authorUsername") != null) {
      username = String.valueOf(message.data().get("authorUsername"));
    }
    return new NotificationDto.ActorDto(authorId, username, username, null, null);
  }

  private static String extractPostId(PushMessage message) {
    if (message.data() != null && message.data().get("postId") != null) {
      return String.valueOf(message.data().get("postId"));
    }
    return null;
  }

  private static String nextId() {
    return "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
