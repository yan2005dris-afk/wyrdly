package com.wyrdly.chat.application.usecase;

import com.wyrdly.chat.application.dto.MessageResponse;
import com.wyrdly.chat.application.port.FollowValidationPort;
import com.wyrdly.chat.domain.event.DirectMessageSentEvent;
import com.wyrdly.chat.domain.exception.UsersNotFollowingException;
import com.wyrdly.chat.domain.model.DirectMessage;
import com.wyrdly.chat.domain.repository.DirectMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SendMessageUseCaseImpl implements SendMessageUseCase {

  @Inject DirectMessageRepository directMessageRepository;

  @Inject FollowValidationPort followValidationPort;

  @Inject Event<DirectMessageSentEvent> messageSentEvent;

  @Override
  @Transactional
  public MessageResponse execute(String senderId, String recipientId, String content) {
    DirectMessage message = new DirectMessage(senderId, recipientId, content);
    message.validateContent();
    message.validateRecipient();

    validateFollowRelationship(senderId, recipientId);

    directMessageRepository.save(message);

    messageSentEvent.fire(
        new DirectMessageSentEvent(
            message.getId(),
            message.getSenderId(),
            message.getRecipientId(),
            message.getContent(),
            message.getSentAt()));

    return new MessageResponse(
        message.getId(),
        message.getSenderId(),
        message.getRecipientId(),
        message.getContent(),
        message.getSentAt());
  }

  private void validateFollowRelationship(String senderId, String recipientId) {
    if (!followValidationPort.areMutualFollowers(senderId, recipientId)) {
      throw new UsersNotFollowingException("Ambos usuarios deben seguirse mutuamente para chatear");
    }
  }
}
