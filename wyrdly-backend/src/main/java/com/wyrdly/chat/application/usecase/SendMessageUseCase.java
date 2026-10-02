package com.wyrdly.chat.application.usecase;

import com.wyrdly.chat.application.dto.MessageResponse;

public interface SendMessageUseCase {
  MessageResponse execute(String senderId, String recipientId, String content);
}
