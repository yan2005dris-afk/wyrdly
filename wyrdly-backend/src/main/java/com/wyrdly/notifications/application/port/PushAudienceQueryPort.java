package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.domain.model.PushTarget;
import java.util.List;

/**
 * Output port that resolves the audience of an author's post fan-out: all followers, attaching
 * their active Web Push subscription if present.
 */
public interface PushAudienceQueryPort {

  /**
   * Reads a keyset page of all followers of {@code authorId} (with or without active Web Push
   * subscription). If a follower has a valid push subscription, it is attached; otherwise null.
   *
   * @param authorId author user id
   * @param afterUserId cursor for keyset pagination (strictly greater than)
   * @param limit maximum number of recipients to fetch in this batch
   * @return list of followers with their optional push subscription
   */
  List<PushTarget> findAllFollowers(String authorId, String afterUserId, int limit);
}
