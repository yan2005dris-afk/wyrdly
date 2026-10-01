package com.yaga.chat.application.usecase;

import com.yaga.chat.application.dto.MessageResponse;

public interface SendMessageUseCase {
  MessageResponse execute(String senderId, String recipientId, String content);
}
