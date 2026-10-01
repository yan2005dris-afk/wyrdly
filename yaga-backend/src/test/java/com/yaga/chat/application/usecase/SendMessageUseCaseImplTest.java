package com.yaga.chat.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yaga.chat.application.dto.MessageResponse;
import com.yaga.chat.application.port.FollowValidationPort;
import com.yaga.chat.domain.exception.InvalidMessageException;
import com.yaga.chat.domain.model.DirectMessage;
import com.yaga.chat.domain.repository.DirectMessageRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
class SendMessageUseCaseImplTest {

  @Inject
  SendMessageUseCase sendMessageUseCase;

  @InjectMock
  DirectMessageRepository directMessageRepository;

  @InjectMock
  FollowValidationPort followValidationPort;

  @BeforeEach
  void setUp() {
    when(followValidationPort.areMutualFollowers("user1", "user2")).thenReturn(true);
    when(followValidationPort.areMutualFollowers("user2", "user1")).thenReturn(true);
  }

  @Test
  void shouldSendMessage() {
    String senderId = "user1";
    String recipientId = "user2";
    String content = "Hello!";

    MessageResponse response = sendMessageUseCase.execute(senderId, recipientId, content);

    assertNotNull(response);
    assertEquals(senderId, response.getSenderId());
    assertEquals(recipientId, response.getRecipientId());
    assertEquals(content, response.getContent());
    assertNotNull(response.getSentAt());

    ArgumentCaptor<DirectMessage> captor = ArgumentCaptor.forClass(DirectMessage.class);
    verify(directMessageRepository).save(captor.capture());
    assertEquals(content, captor.getValue().getContent());
  }

  @Test
  void shouldThrowExceptionForEmptyContent() {
    assertThrows(
        InvalidMessageException.class,
        () -> sendMessageUseCase.execute("user1", "user2", ""));
  }

  @Test
  void shouldThrowExceptionForSelfMessage() {
    assertThrows(
        InvalidMessageException.class,
        () -> sendMessageUseCase.execute("user1", "user1", "Hello"));
  }

  @Test
  void shouldThrowExceptionForContentExceedingLimit() {
    String longContent = "a".repeat(5001);
    assertThrows(
        InvalidMessageException.class,
        () -> sendMessageUseCase.execute("user1", "user2", longContent));
  }
}
