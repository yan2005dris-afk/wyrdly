package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.PostResponse;
import java.util.List;

/**
 * Inbound port for a user's profile timeline: own publications plus shared posts, ordered by the
 * date of the owner's action (HU #150). Lets other bounded contexts (e.g. {@code user}) consume
 * timelines without depending on the post persistence port.
 */
public interface GetProfileTimelineUseCase {
  List<PostResponse> getProfileTimeline(String ownerId, String viewerId, int page, int pageSize);
}
