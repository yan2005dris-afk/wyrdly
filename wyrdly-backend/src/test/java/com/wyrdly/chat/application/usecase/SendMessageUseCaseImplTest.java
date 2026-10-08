package com.wyrdly.chat.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.chat.application.dto.MessageResponse;
import com.wyrdly.chat.application.port.FollowValidationPort;
import com.wyrdly.chat.domain.event.DirectMessageSentEvent;
import com.wyrdly.chat.domain.exception.InvalidMessageException;
import com.wyrdly.chat.domain.exception.UsersNotFollowingException;
import com.wyrdly.chat.domain.model.DirectMessage;
import com.wyrdly.chat.domain.repository.DirectMessageRepository;
import jakarta.enterprise.event.Event;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendMessageUseCaseImplTest {

  private DirectMessageRepository directMessageRepository;
  private FollowValidationPort followValidationPort;
  private SendMessageUseCaseImpl sendMessageUseCase;

  @BeforeEach
  void setUp() {
    directMessageRepository = mock(DirectMessageRepository.class);
    followValidationPort = mock(FollowValidationPort.class);
    sendMessageUseCase = new SendMessageUseCaseImpl();
    sendMessageUseCase.directMessageRepository = directMessageRepository;
    sendMessageUseCase.followValidationPort = followValidationPort;
    sendMessageUseCase.messageSentEvent = mock(Event.class);
    when(sendMessageUseCase.messageSentEvent.fireAsync(any(DirectMessageSentEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  void shouldSendMessageSuccessfully() {
    String senderId = "user1";
    String recipientId = "user2";
    String content = "Hello world";

    when(followValidationPort.areMutualFollowers(senderId, recipientId)).thenReturn(true);

    MessageResponse response = sendMessageUseCase.execute(senderId, recipientId, content);

    assertNotNull(response);
    assertEquals(senderId, response.getSenderId());
    assertEquals(recipientId, response.getRecipientId());
    assertEquals(content, response.getContent());
    assertNotNull(response.getSentAt());

    verify(directMessageRepository).save(any(DirectMessage.class));
    verify(sendMessageUseCase.messageSentEvent).fireAsync(any(DirectMessageSentEvent.class));
  }

  @Test
  void shouldRejectEmptyMessage() {
    String senderId = "user1";
    String recipientId = "user2";

    assertThrows(
        InvalidMessageException.class,
        () -> sendMessageUseCase.execute(senderId, recipientId, "   "));
  }

  @Test
  void shouldRejectSelfMessage() {
    String userId = "user1";

    assertThrows(
        InvalidMessageException.class,
        () -> sendMessageUseCase.execute(userId, userId, "Self message"));
  }

  @Test
  void shouldRejectWhenNotMutualFollowers() {
    String senderId = "user1";
    String recipientId = "user2";

    when(followValidationPort.areMutualFollowers(senderId, recipientId)).thenReturn(false);

    assertThrows(
        UsersNotFollowingException.class,
        () -> sendMessageUseCase.execute(senderId, recipientId, "Hello"));
  }
}
