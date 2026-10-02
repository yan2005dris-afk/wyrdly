package com.wyrdly.chat.application.service;

import com.wyrdly.chat.application.dto.MessageResponse;
import com.wyrdly.chat.application.usecase.SendMessageUseCase;
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
