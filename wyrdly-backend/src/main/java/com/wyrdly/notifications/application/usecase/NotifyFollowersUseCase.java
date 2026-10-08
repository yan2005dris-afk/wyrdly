package com.wyrdly.notifications.application.usecase;

import com.wyrdly.notifications.domain.model.PushMessage;

/**
 * Input port for fanning a Web Push message out to every subscribed follower of an author. The
 * caller only decides WHAT to say; the notifications module decides WHO receives it and HOW.
 */
public interface NotifyFollowersUseCase {

  /**
   * Blocks the calling thread until the whole audience has been handed to the push pipeline, so it
   * must be invoked from a background thread (e.g. an {@code @ObservesAsync} observer), never from
   * a request thread. An author without subscribed followers is a no-op.
   */
  void notifyFollowers(String authorId, PushMessage message);
}
