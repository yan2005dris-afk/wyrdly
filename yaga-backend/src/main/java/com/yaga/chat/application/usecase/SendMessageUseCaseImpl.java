package com.yaga.chat.application.usecase;

import com.yaga.chat.application.dto.MessageResponse;
import com.yaga.chat.application.port.FollowValidationPort;
import com.yaga.chat.domain.exception.UsersNotFollowingException;
import com.yaga.chat.domain.model.DirectMessage;
import com.yaga.chat.domain.repository.DirectMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SendMessageUseCaseImpl implements SendMessageUseCase {

  @Inject DirectMessageRepository directMessageRepository;

  @Inject FollowValidationPort followValidationPort;

  @Override
  @Transactional
  public MessageResponse execute(String senderId, String recipientId, String content) {
    DirectMessage message = new DirectMessage(senderId, recipientId, content);
    message.validateContent();
    message.validateRecipient();

    validateFollowRelationship(senderId, recipientId);

    directMessageRepository.save(message);

    return new MessageResponse(
        message.getId(),
        message.getSenderId(),
        message.getRecipientId(),
        message.getContent(),
        message.getSentAt());
  }

  private void validateFollowRelationship(String senderId, String recipientId) {
    if (!followValidationPort.areMutualFollowers(senderId, recipientId)) {
      throw new UsersNotFollowingException(
          "Ambos usuarios deben seguirse mutuamente para chatear");
    }
  }
}
