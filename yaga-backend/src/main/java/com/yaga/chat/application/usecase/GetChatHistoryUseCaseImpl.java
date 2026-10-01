package com.yaga.chat.application.usecase;

import com.yaga.chat.application.dto.ChatHistoryPage;
import com.yaga.chat.application.dto.MessageResponse;
import com.yaga.chat.application.port.FollowValidationPort;
import com.yaga.chat.domain.exception.UsersNotFollowingException;
import com.yaga.chat.domain.model.DirectMessage;
import com.yaga.chat.domain.repository.DirectMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GetChatHistoryUseCaseImpl implements GetChatHistoryUseCase {

  @Inject DirectMessageRepository directMessageRepository;

  @Inject FollowValidationPort followValidationPort;

  private static final int MAX_PAGE_SIZE = 100;

  @Override
  public ChatHistoryPage execute(String userId, String recipientId, int page, int pageSize) {
    validateFollowRelationship(userId, recipientId);

    int validatedPageSize = Math.min(pageSize, MAX_PAGE_SIZE);
    int skip = Math.max(0, (page - 1) * validatedPageSize);

    List<DirectMessage> messages =
        directMessageRepository.findBetweenUsers(userId, recipientId, skip, validatedPageSize);

    long total = directMessageRepository.countBetweenUsers(userId, recipientId);

    List<MessageResponse> responses =
        messages.stream()
            .map(
                m ->
                    new MessageResponse(
                        m.getId(),
                        m.getSenderId(),
                        m.getRecipientId(),
                        m.getContent(),
                        m.getSentAt()))
            .collect(Collectors.toList());

    ChatHistoryPage.ChatHistoryMeta meta =
        new ChatHistoryPage.ChatHistoryMeta(total, page, validatedPageSize);

    return new ChatHistoryPage(responses, meta);
  }

  private void validateFollowRelationship(String userId, String recipientId) {
    if (!followValidationPort.areMutualFollowers(userId, recipientId)) {
      throw new UsersNotFollowingException(
          "Ambos usuarios deben seguirse mutuamente para ver el historial de chat");
    }
  }
}
