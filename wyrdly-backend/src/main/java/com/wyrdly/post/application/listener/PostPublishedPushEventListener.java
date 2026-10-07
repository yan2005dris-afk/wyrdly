package com.wyrdly.post.application.listener;

import com.wyrdly.notifications.application.usecase.NotifyFollowersUseCase;
import com.wyrdly.notifications.domain.model.PushMessage;
import com.wyrdly.post.domain.event.PostPublishedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Asynchronous CDI observer that turns a {@link PostPublishedEvent} into a {@code
 * NEW_POST_FROM_FOLLOWED} Web Push fanned out to the author's subscribed followers.
 *
 * <p>{@code @ObservesAsync} runs on the managed executor, never on the request thread, so the
 * {@code POST /api/posts} response is not delayed by the audience lookup. This listener only
 * decides WHAT the notification says; resolving the audience and delivering it is owned by {@link
 * NotifyFollowersUseCase}.
 */
@ApplicationScoped
public class PostPublishedPushEventListener {

  static final String NOTIFICATION_TYPE = "NEW_POST_FROM_FOLLOWED";
  static final String TITLE_PREFIX = "Nueva publicación de ";
  static final String DEEP_LINK_PREFIX = "/posts/";
  static final int SNIPPET_MAX_CODE_POINTS = 140;
  static final String ELLIPSIS = "…";

  private final NotifyFollowersUseCase notifyFollowers;

  @Inject
  public PostPublishedPushEventListener(NotifyFollowersUseCase notifyFollowers) {
    this.notifyFollowers =
        Objects.requireNonNull(notifyFollowers, "notifyFollowers must not be null");
  }

  void on(@ObservesAsync PostPublishedEvent event) {
    Objects.requireNonNull(event, "event must not be null");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("postId", event.postId());
    data.put("authorId", event.authorId());

    PushMessage message =
        new PushMessage(
            NOTIFICATION_TYPE,
            TITLE_PREFIX + event.authorUsername(),
            snippet(event.content()),
            DEEP_LINK_PREFIX + event.postId(),
            data);
    notifyFollowers.notifyFollowers(event.authorId(), message);
  }

  /**
   * Collapses whitespace and truncates to {@link #SNIPPET_MAX_CODE_POINTS} code points (never
   * splitting a surrogate pair, e.g. an emoji), appending an ellipsis when truncated.
   */
  static String snippet(String content) {
    if (content == null) {
      return "";
    }
    String normalized = content.strip().replaceAll("\\s+", " ");
    if (normalized.codePointCount(0, normalized.length()) <= SNIPPET_MAX_CODE_POINTS) {
      return normalized;
    }
    int end = normalized.offsetByCodePoints(0, SNIPPET_MAX_CODE_POINTS - 1);
    return normalized.substring(0, end).stripTrailing() + ELLIPSIS;
  }
}
