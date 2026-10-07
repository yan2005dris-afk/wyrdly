package com.wyrdly.notifications.application.usecase.impl;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.dto.NotificationDto.ActorDto;
import com.wyrdly.notifications.application.dto.NotificationListResponseDto;
import com.wyrdly.notifications.application.usecase.GetNotificationsForUserUseCase;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default orchestration for listing the recipient's notifications. The repository returns {@link
 * Notification} entities; the use case enriches them with actor info via {@link
 * UserProfileRepository#findProfileSummariesByIds} (single batched query) and projects to {@link
 * NotificationDto} for the REST response.
 */
@ApplicationScoped
public class GetNotificationsForUserUseCaseImpl implements GetNotificationsForUserUseCase {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 50;

  private final NotificationRepository notificationRepository;
  private final UserProfileRepository userProfileRepository;

  @Inject
  public GetNotificationsForUserUseCaseImpl(
      NotificationRepository notificationRepository,
      @ResilientNeo4j UserProfileRepository userProfileRepository) {
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository");
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository");
  }

  @Override
  public NotificationListResponseDto execute(String userId, int page, int pageSize) {
    int safePage = Math.max(0, page);
    int safeSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

    List<Notification> notifications =
        notificationRepository.findByRecipient(userId, safePage, safeSize);
    long unreadCount = notificationRepository.countUnread(userId);
    long total =
        notifications.size()
            + (long) safePage * safeSize; // best-effort; replace with count query if needed

    Set<String> actorIds = new HashSet<>();
    for (Notification n : notifications) {
      actorIds.add(n.actorId());
    }
    Map<String, FollowerSummary> actors = userProfileRepository.findProfileSummariesByIds(actorIds);

    List<NotificationDto> dtos =
        notifications.stream()
            .map(
                n -> {
                  FollowerSummary s = actors.get(n.actorId());
                  ActorDto actor =
                      s == null
                          ? ActorDto.placeholder(n.actorId())
                          : new ActorDto(s.id(), s.username(), s.fullName(), s.avatarUrl(), "");
                  return NotificationDto.from(n, actor);
                })
            .collect(Collectors.toList());

    return new NotificationListResponseDto(dtos, unreadCount, safePage, safeSize, total);
  }
}
