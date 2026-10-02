package com.wyrdly.chat.application.usecase;

import com.wyrdly.chat.application.dto.ChatHistoryPage;

public interface GetChatHistoryUseCase {
  ChatHistoryPage execute(String userId, String recipientId, int page, int pageSize);
}
