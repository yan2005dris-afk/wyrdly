package com.wyrdly.post.application.dto;

import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import java.time.Instant;

/**
 * Public projection of a {@code Comment}. Includes the {@code authorId} at the top level for the
 * React UI to apply its "delete" visibility rule ({@code authorId === currentUserId || authorId ===
 * postAuthorId}) without parsing the nested {@code author} object.
 */
public record CommentResponse(
    String id,
    String postId,
    String authorId,
    String content,
    Instant createdAt,
    AuthorDto author) {}
