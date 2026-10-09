package com.wyrdly.post.application.mapper;

import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.dto.PostResponse.ReactionCounts;
import com.wyrdly.post.application.dto.PostResponse.RepostContextDto;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.RepostContext;

/**
 * Single translation point from the {@link FeedPost} read model to the {@link PostResponse} API
 * contract, shared by the feed, post detail and profile timeline use cases.
 */
public final class PostResponseMapper {

  private PostResponseMapper() {}

  public static PostResponse toResponse(FeedPost feedPost) {
    return new PostResponse(
        feedPost.id(),
        feedPost.content(),
        feedPost.mediaUrl(),
        feedPost.createdAt(),
        toAuthorDto(feedPost.author()),
        new ReactionCounts(feedPost.likeCount(), feedPost.loveCount(), feedPost.celebrateCount()),
        feedPost.commentsCount(),
        feedPost.repostsCount(),
        feedPost.userReaction(),
        feedPost.userHasReposted(),
        toRepostContextDto(feedPost.repostContext()));
  }

  private static AuthorDto toAuthorDto(Author author) {
    return new AuthorDto(author.id(), author.username(), author.fullName(), author.avatarUrl());
  }

  private static RepostContextDto toRepostContextDto(RepostContext context) {
    if (context == null) {
      return null;
    }
    Author reposter = context.reposter();
    return new RepostContextDto(
        reposter.id(),
        reposter.username(),
        reposter.fullName(),
        reposter.avatarUrl(),
        context.repostedAt());
  }
}
