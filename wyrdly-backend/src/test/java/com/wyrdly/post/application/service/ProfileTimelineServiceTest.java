package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.RepostContext;
import com.wyrdly.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileTimelineServiceTest {

  private static final Author OWNER = new Author("usr_owner", "bob", "Bob Smith", null);
  private static final Author OTHER = new Author("usr_alice", "alice", "Alice Doe", null);

  private PostRepository postRepository;
  private ProfileTimelineService service;

  @BeforeEach
  void setUp() {
    postRepository = mock(PostRepository.class);
    service = new ProfileTimelineService(postRepository);
  }

  @Test
  void getProfileTimeline_MapsPublicationsAndRepostsPreservingOrder() {
    Instant repostedAt = Instant.parse("2026-10-03T10:00:00Z");
    FeedPost repost =
        new FeedPost(
            "pst_alice",
            "Alice post",
            null,
            Instant.parse("2026-10-01T10:00:00Z"),
            OTHER,
            0L,
            0L,
            0L,
            0L,
            1L,
            null,
            true,
            new RepostContext(OWNER, repostedAt));
    FeedPost own =
        new FeedPost(
            "pst_bob",
            "Bob post",
            null,
            Instant.parse("2026-10-02T10:00:00Z"),
            OWNER,
            0L,
            0L,
            0L,
            0L,
            0L,
            null,
            false);
    when(postRepository.findProfileTimeline("usr_owner", "usr_viewer", 1, 20))
        .thenReturn(List.of(repost, own));

    List<PostResponse> timeline = service.getProfileTimeline("usr_owner", "usr_viewer", 1, 20);

    assertEquals(2, timeline.size());
    assertEquals("pst_alice", timeline.get(0).id());
    assertEquals("usr_alice", timeline.get(0).author().id());
    assertEquals("usr_owner", timeline.get(0).repostContext().reposterId());
    assertEquals(repostedAt, timeline.get(0).repostContext().repostedAt());
    assertEquals("pst_bob", timeline.get(1).id());
    assertNull(timeline.get(1).repostContext());
  }

  @Test
  void getProfileTimeline_ThrowsException_WhenOwnerIdBlank() {
    assertThrows(
        IllegalArgumentException.class, () -> service.getProfileTimeline(" ", null, 1, 20));
    verifyNoInteractions(postRepository);
  }
}
