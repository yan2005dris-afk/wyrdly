package com.yaga.chat.application.usecase;

import com.yaga.chat.application.dto.ChatHistoryPage;

public interface GetChatHistoryUseCase {
  ChatHistoryPage execute(String userId, String recipientId, int page, int pageSize);
}
