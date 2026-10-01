package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.FeedResponseDto;
import com.wyrdly.post.application.pagination.CursorFeedPagination;
import com.wyrdly.post.application.pagination.FeedPaginationRequest;
import com.wyrdly.post.application.pagination.OffsetFeedPagination;

public interface GetFeedUseCase {

  FeedResponseDto getFeed(String userId, FeedPaginationRequest pagination);

  default FeedResponseDto getFeed(String userId, int page, int pageSize) {
    return getFeed(userId, new OffsetFeedPagination(page, pageSize));
  }

  default FeedResponseDto getFeedWithCursor(String userId, String cursor, int limit) {
    return getFeed(userId, new CursorFeedPagination(cursor, limit));
  }
}
