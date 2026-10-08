package com.wyrdly.notifications.infrastructure.sse;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory reactive adapter for {@link NotificationBroadcasterPort}. Dispatches real-time
 * notification events to active SSE client connections partitioned by target user ID.
 */
@ApplicationScoped
public class InMemoryNotificationBroadcasterAdapter implements NotificationBroadcasterPort {

  private static final Logger LOG =
      Logger.getLogger(InMemoryNotificationBroadcasterAdapter.class.getName());

  private final ConcurrentMap<String, Set<MultiEmitter<? super NotificationDto>>> userEmitters =
      new ConcurrentHashMap<>();

  @Override
  public void broadcast(String targetUserId, NotificationDto notification) {
    if (targetUserId == null || notification == null) {
      return;
    }
    Set<MultiEmitter<? super NotificationDto>> emitters = userEmitters.get(targetUserId);
    if (emitters == null || emitters.isEmpty()) {
      return;
    }

    for (MultiEmitter<? super NotificationDto> emitter : emitters) {
      try {
        emitter.emit(notification);
      } catch (Exception e) {
        LOG.log(
            Level.FINE, "Failed to emit notification to subscriber for user " + targetUserId, e);
        emitters.remove(emitter);
      }
    }
  }

  @Override
  public Multi<NotificationDto> subscribe(String userId) {
    Objects.requireNonNull(userId, "userId must not be null");

    return Multi.createFrom()
        .<NotificationDto>emitter(
            emitter -> {
              Set<MultiEmitter<? super NotificationDto>> emitters =
                  userEmitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
              emitters.add(emitter);

              emitter.onTermination(
                  () -> {
                    Set<MultiEmitter<? super NotificationDto>> currentEmitters =
                        userEmitters.get(userId);
                    if (currentEmitters != null) {
                      currentEmitters.remove(emitter);
                      if (currentEmitters.isEmpty()) {
                        userEmitters.remove(userId, Collections.emptySet());
                      }
                    }
                  });
            },
            BackPressureStrategy.BUFFER);
  }

  /** Visible for testing: returns count of active subscriber emitters for a given user. */
  int activeSubscribersCount(String userId) {
    Set<MultiEmitter<? super NotificationDto>> set = userEmitters.get(userId);
    return set == null ? 0 : set.size();
  }
}
