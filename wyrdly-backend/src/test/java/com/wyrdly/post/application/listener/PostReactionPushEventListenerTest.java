package com.wyrdly.post.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.post.domain.event.PostReactionEvent;
import com.wyrdly.post.domain.model.ReactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostReactionPushEventListenerTest {

  private PushDispatcherPort dispatcher;
  private PostReactionPushEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    listener = new PostReactionPushEventListener(dispatcher);
  }

  @Test
  void mapsLikeReactionToPostLike() {
    var event = new PostReactionEvent("pst_1", "usr_author", "usr_reactor", ReactionType.LIKE);

    listener.on(event);

    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals("usr_author", push.recipientUserId());
                  assertEquals("POST_LIKE", push.type());
                  assertEquals("/posts/pst_1", push.deepLink());
                  assertEquals("pst_1", push.data().get("postId"));
                  assertEquals("usr_reactor", push.data().get("reactorId"));
                  assertNotNull(push.title());
                  assertNotNull(push.body());
                  return true;
                }));
  }

  @Test
  void mapsLoveReactionToPostLove() {
    var event = new PostReactionEvent("pst_2", "usr_author", "usr_reactor", ReactionType.LOVE);

    listener.on(event);

    verify(dispatcher).dispatch(argThat(push -> "POST_LOVE".equals(push.type())));
  }

  @Test
  void mapsCelebrateReactionToPostCelebrate() {
    var event = new PostReactionEvent("pst_3", "usr_author", "usr_reactor", ReactionType.CELEBRATE);

    listener.on(event);

    verify(dispatcher).dispatch(argThat(push -> "POST_CELEBRATE".equals(push.type())));
  }

  @Test
  void doesNotDispatchOnSelfReactionDefensively() {
    var event = new PostReactionEvent("pst_1", "usr_same", "usr_same", ReactionType.LIKE);

    listener.on(event);

    verifyNoInteractions(dispatcher);
  }

  @Test
  void doesNotDispatchWhenReactionTypeIsNull() {
    var event = new PostReactionEvent("pst_1", "usr_author", "usr_reactor", null);

    listener.on(event);

    verifyNoInteractions(dispatcher);
  }
}
