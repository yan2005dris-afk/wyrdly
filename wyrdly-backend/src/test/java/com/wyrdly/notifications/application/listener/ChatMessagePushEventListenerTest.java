package com.wyrdly.notifications.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wyrdly.chat.domain.event.DirectMessageSentEvent;
import com.wyrdly.chat.infrastructure.websocket.ChatSessionRegistry;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatMessagePushEventListenerTest {

  private static final String SENDER_ID = "usr_sender";
  private static final String RECIPIENT_ID = "usr_recipient";
  private static final String MESSAGE_ID = "msg_1";

  private PushDispatcherPort dispatcher;
  private NotificationRepository notificationRepository;
  private ChatSessionRegistry sessionRegistry;
  private UserProfileRepository userProfileRepository;
  private ChatMessagePushEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    notificationRepository = mock(NotificationRepository.class);
    sessionRegistry = mock(ChatSessionRegistry.class);
    userProfileRepository = mock(UserProfileRepository.class);
    listener =
        new ChatMessagePushEventListener(
            dispatcher, notificationRepository, sessionRegistry, userProfileRepository);
    when(sessionRegistry.isUserOnline(RECIPIENT_ID)).thenReturn(false);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());
  }

  private DirectMessageSentEvent event(String content) {
    return new DirectMessageSentEvent(
        MESSAGE_ID, SENDER_ID, RECIPIENT_ID, content, Instant.parse("2026-10-08T10:00:00Z"));
  }

  @Test
  void doesNotDispatchPushWhenRecipientIsOnline() {
    when(sessionRegistry.isUserOnline(RECIPIENT_ID)).thenReturn(true);

    listener.on(event("hi"));

    verifyNoInteractions(dispatcher);
    verifyNoInteractions(notificationRepository);
  }

  @Test
  void doesNotDispatchPushWhenSenderIsRecipient() {
    DirectMessageSentEvent selfEvent =
        new DirectMessageSentEvent(
            MESSAGE_ID,
            SENDER_ID,
            SENDER_ID,
            "notes to self",
            Instant.parse("2026-10-08T10:00:00Z"));

    listener.on(selfEvent);

    verifyNoInteractions(dispatcher);
    verifyNoInteractions(notificationRepository);
    verifyNoInteractions(sessionRegistry);
  }

  @Test
  void dispatchesPushWhenRecipientIsOfflineAndSubscriptionExists() {
    when(userProfileRepository.findProfileSummariesByIds(Set.of(SENDER_ID)))
        .thenReturn(
            Map.of(SENDER_ID, new FollowerSummary(SENDER_ID, "maria", "Maria Lopez", "", false)));

    listener.on(event("hello there"));

    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals(RECIPIENT_ID, push.recipientUserId());
                  assertEquals("CHAT_MESSAGE", push.type());
                  assertEquals("Mensaje de Maria Lopez", push.title());
                  assertEquals("hello there", push.body());
                  assertEquals("/chat/maria", push.deepLink());
                  Map<String, Object> data = push.data();
                  assertEquals(SENDER_ID, data.get("senderId"));
                  assertEquals(MESSAGE_ID, data.get("messageId"));
                  assertEquals("hello there", data.get("snippet"));
                  return true;
                }));
    verify(notificationRepository)
        .save(
            argThat(
                n -> {
                  assertNotNull(n.id());
                  assertEquals(RECIPIENT_ID, n.recipientUserId());
                  assertEquals("CHAT_MESSAGE", n.type());
                  assertEquals(SENDER_ID, n.actorId());
                  assertEquals("Mensaje de Maria Lopez", n.title());
                  assertEquals("hello there", n.body());
                  assertEquals("/chat/maria", n.deepLink());
                  assertEquals(MESSAGE_ID, n.targetResourceId());
                  return true;
                }));
  }

  @Test
  void dispatchesPushWhenSessionRegistryThrows() {
    when(sessionRegistry.isUserOnline(RECIPIENT_ID)).thenThrow(new RuntimeException("boom"));
    when(userProfileRepository.findProfileSummariesByIds(Set.of(SENDER_ID)))
        .thenReturn(
            Map.of(SENDER_ID, new FollowerSummary(SENDER_ID, "maria", "Maria Lopez", "", false)));

    listener.on(event("hello there"));

    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals(RECIPIENT_ID, push.recipientUserId());
                  assertEquals("Mensaje de Maria Lopez", push.title());
                  assertEquals("/chat/maria", push.deepLink());
                  return true;
                }));
    verify(notificationRepository).save(argThat(n -> RECIPIENT_ID.equals(n.recipientUserId())));
  }

  @Test
  void truncatesSnippetTo140Characters() {
    String longContent = "a".repeat(500);
    when(userProfileRepository.findProfileSummariesByIds(Set.of(SENDER_ID)))
        .thenReturn(
            Map.of(SENDER_ID, new FollowerSummary(SENDER_ID, "maria", "Maria Lopez", "", false)));

    listener.on(event(longContent));

    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals(140, push.body().length());
                  assertEquals(140, ((String) push.data().get("snippet")).length());
                  return true;
                }));
  }

  @Test
  void usesFallbackTitleWhenSenderProfileNotFound() {
    when(userProfileRepository.findProfileSummariesByIds(Set.of(SENDER_ID))).thenReturn(Map.of());

    listener.on(event("hello there"));

    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals("Nuevo mensaje de chat", push.title());
                  assertEquals("/chat", push.deepLink());
                  return true;
                }));
    verify(notificationRepository)
        .save(
            argThat(
                n -> {
                  assertEquals("Nuevo mensaje de chat", n.title());
                  assertEquals("/chat", n.deepLink());
                  return true;
                }));
  }

  @Test
  void persistsNotificationBeforeDispatchingPush() {
    when(userProfileRepository.findProfileSummariesByIds(Set.of(SENDER_ID)))
        .thenReturn(
            Map.of(SENDER_ID, new FollowerSummary(SENDER_ID, "maria", "Maria Lopez", "", false)));

    listener.on(event("hello there"));

    var inOrder = inOrder(notificationRepository, dispatcher);
    inOrder
        .verify(notificationRepository)
        .save(argThat(n -> RECIPIENT_ID.equals(n.recipientUserId())));
    inOrder
        .verify(dispatcher)
        .dispatch(argThat(push -> RECIPIENT_ID.equals(push.recipientUserId())));
  }

  @Test
  void continuesDispatchingPushWhenNotificationSaveThrows() {
    doThrow(new RuntimeException("neo4j down"))
        .when(notificationRepository)
        .save(argThat(n -> true));
    when(userProfileRepository.findProfileSummariesByIds(Set.of(SENDER_ID)))
        .thenReturn(
            Map.of(SENDER_ID, new FollowerSummary(SENDER_ID, "maria", "Maria Lopez", "", false)));

    listener.on(event("hello there"));

    verify(dispatcher)
        .dispatch(
            argThat(
                push ->
                    RECIPIENT_ID.equals(push.recipientUserId())
                        && "CHAT_MESSAGE".equals(push.type())));
  }
}
