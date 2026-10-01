package com.yaga.chat.application.usecase;

public interface BroadcastTypingUseCase {
  void execute(String senderId, String recipientId);
}
