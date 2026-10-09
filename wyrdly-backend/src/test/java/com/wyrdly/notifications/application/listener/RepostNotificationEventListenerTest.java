package com.wyrdly.notifications.application.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.PostRepostedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepostNotificationEventListenerTest {

  private PushDispatcherPort dispatcher;
  private NotificationRepository notificationRepository;
  private UserProfileRepository userProfileRepository;
  private NotificationBroadcasterPort broadcaster;
  private RepostNotificationEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    notificationRepository = mock(NotificationRepository.class);
    userProfileRepository = mock(UserProfileRepository.class);
    broadcaster = mock(NotificationBroadcasterPort.class);
    listener =
        new RepostNotificationEventListener(
            dispatcher, notificationRepository, userProfileRepository, broadcaster);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());
  }

  @Test
  void on_DispatchesNotification_WhenActorDifferentFromAuthor() {
    when(userProfileRepository.findProfileSummariesByIds(Set.of("usr_actor")))
        .thenReturn(
            Map.of(
                "usr_actor",
                new FollowerSummary("usr_actor", "actor", "Actor Name", "https://a.jpg", false)));

    PostRepostedEvent event = new PostRepostedEvent("pst_1", "usr_author", "usr_actor");
    listener.on(event);

    verify(notificationRepository)
        .save(
            argThat(
                n ->
                    "usr_author".equals(n.recipientUserId())
                        && "POST_BOOST".equals(n.type())
                        && "usr_actor".equals(n.actorId())
                        && n.body().contains("Actor Name compartió tu publicación")));

    verify(broadcaster).broadcast(argThat("usr_author"::equals), any());
    verify(dispatcher)
        .dispatch(
            argThat(
                p ->
                    "usr_author".equals(p.recipientUserId())
                        && "POST_BOOST".equals(p.type())
                        && p.body().contains("Actor Name")));
  }

  @Test
  void on_SkipsNotification_WhenSelfRepost() {
    PostRepostedEvent event = new PostRepostedEvent("pst_1", "usr_author", "usr_author");
    listener.on(event);

    verifyNoInteractions(notificationRepository, broadcaster, dispatcher);
  }
}
