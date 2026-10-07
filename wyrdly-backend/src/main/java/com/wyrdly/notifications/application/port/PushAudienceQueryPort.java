package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.domain.model.PushTarget;
import java.util.List;

/**
 * Output port that resolves the push audience of a fan-out: the users that follow an author AND
 * have an active push subscription. Followers without a subscription never leave the database.
 */
public interface PushAudienceQueryPort {

  /**
   * Returns up to {@code limit} subscribed followers of {@code authorId} whose id is strictly
   * greater than {@code afterUserId}, ordered by user id (keyset pagination). Pass an empty string
   * to start from the beginning; an empty list means the audience is exhausted. The author is never
   * part of their own audience.
   */
  List<PushTarget> findSubscribedFollowers(String authorId, String afterUserId, int limit);
}
