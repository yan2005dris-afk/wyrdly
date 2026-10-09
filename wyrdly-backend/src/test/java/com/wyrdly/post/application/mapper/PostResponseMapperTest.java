package com.wyrdly.post.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.RepostContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostResponseMapperTest {

  private static final Instant CREATED_AT = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant REPOSTED_AT = Instant.parse("2026-10-02T09:30:00Z");
  private static final Author ORIGINAL_AUTHOR =
      new Author("usr_author", "alice", "Alice Doe", "http://avatar/alice");
  private static final Author REPOSTER = new Author("usr_owner", "bob", "Bob Smith", null);

  @Test
  void toResponse_MapsAllFields_ForOriginalPublication() {
    FeedPost feedPost =
        new FeedPost(
            "pst_1",
            "Hola",
            "http://media",
            CREATED_AT,
            ORIGINAL_AUTHOR,
            5L,
            2L,
            1L,
            7L,
            3L,
            "LOVE",
            true);

    PostResponse response = PostResponseMapper.toResponse(feedPost);

    assertEquals("pst_1", response.id());
    assertEquals("Hola", response.content());
    assertEquals("http://media", response.mediaUrl());
    assertEquals(CREATED_AT, response.createdAt());
    assertEquals("usr_author", response.author().id());
    assertEquals("alice", response.author().username());
    assertEquals("Alice Doe", response.author().fullName());
    assertEquals("http://avatar/alice", response.author().avatarUrl());
    assertEquals(5L, response.reactionCounts().likeCount());
    assertEquals(2L, response.reactionCounts().loveCount());
    assertEquals(1L, response.reactionCounts().celebrateCount());
    assertEquals(7L, response.commentsCount());
    assertEquals(3L, response.repostsCount());
    assertEquals("LOVE", response.userReaction());
    assertTrue(response.userHasReposted());
    assertNull(response.repostContext());
  }

  @Test
  void toResponse_KeepsOriginalAuthorAndAddsRepostContext_ForRepost() {
    FeedPost feedPost =
        new FeedPost(
            "pst_1",
            "Hola",
            null,
            CREATED_AT,
            ORIGINAL_AUTHOR,
            0L,
            0L,
            0L,
            0L,
            1L,
            null,
            false,
            new RepostContext(REPOSTER, REPOSTED_AT));

    PostResponse response = PostResponseMapper.toResponse(feedPost);

    assertEquals("usr_author", response.author().id());
    assertNotNull(response.repostContext());
    assertEquals("usr_owner", response.repostContext().reposterId());
    assertEquals("bob", response.repostContext().reposterUsername());
    assertEquals("Bob Smith", response.repostContext().reposterName());
    assertNull(response.repostContext().reposterAvatarUrl());
    assertEquals(REPOSTED_AT, response.repostContext().repostedAt());
  }
}
