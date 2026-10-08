package com.wyrdly.post.application.dto;

import java.util.List;

/**
 * Paginated response for {@code GET /api/posts/{postId}/comments}. {@code totalCount} is the count
 * of all comments for the post (not just the current page) so the UI can render "Ver más" controls.
 */
public record CommentListResponseDto(
    List<CommentResponse> data, long totalCount, int page, int pageSize) {}
