package com.wyrdly.post.application.dto;

/**
 * Response payload for {@code PUT} and {@code DELETE /api/posts/{postId}/repost}.
 *
 * @param reposted true if the post is currently reposted by the requesting user, false otherwise
 * @param repostsCount the total number of reposts for this post
 */
public record RepostResponse(boolean reposted, long repostsCount) {}
