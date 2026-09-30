package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.FeedResponseDto;

public interface GetFeedUseCase {
  FeedResponseDto getFeed(String userId, int page, int pageSize);

  FeedResponseDto getFeedWithCursor(String userId, String cursor, int limit);
}
