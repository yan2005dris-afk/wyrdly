package com.wyrdly.notifications.application.listener;

import com.wyrdly.chat.domain.event.DirectMessageSentEvent;
import com.wyrdly.chat.infrastructure.websocket.ChatSessionRegistry;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notifications bounded context listener that observes {@link DirectMessageSentEvent} emitted by
 * the chat module. Decouples chat domain operations from notification persistence and push
 * dispatch, mirroring {@link UserFollowNotificationEventListener} and {@link
 * PostReactionNotificationEventListener}.
 *
 * <p>When the recipient has an active WebSocket session the message is already delivered in
 * real-time, so the push is skipped. Otherwise the listener persists an in-app {@link Notification}
 * and dispatches a Web Push payload so the recipient still sees it on their next device unlock.
 */
@ApplicationScoped
public class ChatMessagePushEventListener {

  private static final Logger LOG = Logger.getLogger(ChatMessagePushEventListener.class.getName());

  static final String NOTIFICATION_TYPE = "CHAT_MESSAGE";
  static final int MAX_SNIPPET_LENGTH = 140;
  static final String DEEP_LINK_PREFIX = "/chat/";
  static final String DEEP_LINK_FALLBACK = "/chat";
  static final String TITLE_FALLBACK = "Nuevo mensaje de chat";
  static final String TITLE_PREFIX = "Mensaje de ";

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;
  private final ChatSessionRegistry sessionRegistry;
  private final UserProfileRepository userProfileRepository;

  @Inject
  public ChatMessagePushEventListener(
      PushDispatcherPort dispatcher,
      NotificationRepository notificationRepository,
      ChatSessionRegistry sessionRegistry,
      @ResilientNeo4j UserProfileRepository userProfileRepository) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
    this.sessionRegistry =
        Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
  }

  /**
   * Handles a {@link DirectMessageSentEvent}. Skips dispatch (and persistence) when the recipient
   * is online, then otherwise enriches the event with the sender's name, persists the notification,
   * and dispatches the push.
   */
  public void on(@Observes DirectMessageSentEvent event) {
    Objects.requireNonNull(event, "event must not be null");

    if (event.senderId() != null && event.senderId().equals(event.recipientId())) {
      return;
    }

    boolean isOnline;
    try {
      isOnline = sessionRegistry.isUserOnline(event.recipientId());
    } catch (RuntimeException ex) {
      LOG.log(
          Level.WARNING,
          "Error checking WS session status for user " + event.recipientId() + "; assuming offline",
          ex);
      isOnline = false;
    }

    if (isOnline) {
      return;
    }

    FollowerSummary senderSummary = resolveSenderSummary(event.senderId());
    String senderName =
        (senderSummary != null
                && senderSummary.fullName() != null
                && !senderSummary.fullName().isBlank())
            ? senderSummary.fullName()
            : null;
    String username =
        (senderSummary != null
                && senderSummary.username() != null
                && !senderSummary.username().isBlank())
            ? senderSummary.username()
            : null;

    // Simple substring truncation. Future improvement: normalize Unicode graphemes before splitting
    // so surrogate pairs and combining marks stay intact for non-Latin scripts.
    int limit = Math.min(event.content().length(), MAX_SNIPPET_LENGTH);
    String snippet = event.content().substring(0, limit);

    String title = senderName != null ? TITLE_PREFIX + senderName : TITLE_FALLBACK;
    String deepLink = username != null ? DEEP_LINK_PREFIX + username : DEEP_LINK_FALLBACK;

    Notification notification =
        new Notification(
            nextId(),
            event.recipientId(),
            NOTIFICATION_TYPE,
            event.senderId(),
            title,
            snippet,
            deepLink,
            event.messageId(),
            false,
            Instant.now());
    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      LOG.log(
          Level.WARNING,
          "Failed to persist chat notification for " + event.recipientId(),
          persistError);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("senderId", event.senderId());
    data.put("messageId", event.messageId());
    data.put("snippet", snippet);

    PushEvent push =
        new PushEvent(event.recipientId(), NOTIFICATION_TYPE, title, snippet, deepLink, data);
    dispatcher.dispatch(push);
  }

  private FollowerSummary resolveSenderSummary(String senderId) {
    if (senderId == null) {
      return null;
    }
    try {
      var map = userProfileRepository.findProfileSummariesByIds(Set.of(senderId));
      return map.get(senderId);
    } catch (RuntimeException lookupError) {
      LOG.log(
          Level.WARNING, "Failed to resolve profile summary for sender " + senderId, lookupError);
      return null;
    }
  }

  private static String nextId() {
    return "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
