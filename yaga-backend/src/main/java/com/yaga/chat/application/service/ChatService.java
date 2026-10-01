package com.yaga.chat.application.service;

import com.yaga.chat.application.dto.MessageResponse;
import com.yaga.chat.application.usecase.SendMessageUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ChatService {

  private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());

  @Inject
  SendMessageUseCase sendMessageUseCase;

  public MessageResponse sendMessage(String senderId, String recipientId, String content) {
    LOGGER.info(
        "Sending message from " + senderId + " to " + recipientId);
    return sendMessageUseCase.execute(senderId, recipientId, content);
  }
}
