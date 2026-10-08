package com.wyrdly.post.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.Comment;
import com.wyrdly.testsupport.Neo4jTestContainer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

class Neo4jCommentRepositoryAdapterIT {

  static final Driver driver = Neo4jTestContainer.driver();

  Neo4jCommentRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new Neo4jCommentRepositoryAdapter(driver);
  }

  @BeforeEach
  void cleanDatabase() {
    Neo4jTestContainer.deleteAllData();
  }

  @Test
  void savePersistsCommentWithCorrectRelationships() {
    seedUser("usr_alice", "alice");
    seedPost("pst_abc", "usr_alice");

    Author author = new Author("usr_alice", "alice", "Alice", null);
    Comment comment =
        new Comment(
            "cmt_1", "pst_abc", author, "Hello world", Instant.parse("2026-10-08T12:00:00Z"));

    Comment saved = adapter.save(comment);

    assertEquals("cmt_1", saved.id());
    verifyComentarioNodeExists("cmt_1", "Hello world");
    verifyEscribeRelationshipExists("usr_alice", "cmt_1");
    verifyEnPostRelationshipExists("cmt_1", "pst_abc");
  }

  @Test
  void findByPostIdReturnsOrderedComments() {
    seedUser("usr_alice", "alice");
    seedPost("pst_abc", "usr_alice");

    Author author = new Author("usr_alice", "alice", "Alice", null);
    adapter.save(
        new Comment(
            "cmt_old", "pst_abc", author, "Older comment", Instant.parse("2026-10-08T08:00:00Z")));
    adapter.save(
        new Comment(
            "cmt_mid", "pst_abc", author, "Middle comment", Instant.parse("2026-10-08T10:00:00Z")));
    adapter.save(
        new Comment(
            "cmt_new", "pst_abc", author, "Newest comment", Instant.parse("2026-10-08T12:00:00Z")));

    List<Comment> page = adapter.findByPostId("pst_abc", 1, 10);

    assertEquals(3, page.size());
    assertEquals("cmt_old", page.get(0).id(), "ASC order by createdAt");
    assertEquals("cmt_mid", page.get(1).id());
    assertEquals("cmt_new", page.get(2).id());
  }

  @Test
  void countByPostIdReflectsAllComments() {
    seedUser("usr_alice", "alice");
    seedPost("pst_abc", "usr_alice");
    Author author = new Author("usr_alice", "alice", "Alice", null);

    adapter.save(
        new Comment("cmt_1", "pst_abc", author, "A", Instant.parse("2026-10-08T08:00:00Z")));
    adapter.save(
        new Comment("cmt_2", "pst_abc", author, "B", Instant.parse("2026-10-08T09:00:00Z")));
    adapter.save(
        new Comment("cmt_3", "pst_abc", author, "C", Instant.parse("2026-10-08T10:00:00Z")));

    long count = adapter.countByPostId("pst_abc");
    assertEquals(3L, count);
  }

  @Test
  void deleteByIdDetachesAndDeletes() {
    seedUser("usr_alice", "alice");
    seedPost("pst_abc", "usr_alice");
    Author author = new Author("usr_alice", "alice", "Alice", null);
    adapter.save(
        new Comment("cmt_1", "pst_abc", author, "Hello", Instant.parse("2026-10-08T12:00:00Z")));

    adapter.deleteById("cmt_1");

    try (Session session = driver.session()) {
      long count =
          session
              .run("MATCH (c:Comentario {id: $id}) RETURN count(c) AS n", Map.of("id", "cmt_1"))
              .single()
              .get("n")
              .asLong();
      assertEquals(0L, count, "Comentario node must be deleted");
      long relCount =
          session
              .run(
                  "MATCH (c:Comentario {id: $id})-[r]-() RETURN count(r) AS n",
                  Map.of("id", "cmt_1"))
              .single()
              .get("n")
              .asLong();
      assertEquals(0L, relCount, "All relationships must be detached");
    }
  }

  @Test
  void findByIdReturnsCommentWithAuthor() {
    seedUser("usr_alice", "alice", "Alice Doe", "https://avatar.jpg");
    seedPost("pst_abc", "usr_alice");
    Author author = new Author("usr_alice", "alice", "Alice Doe", "https://avatar.jpg");
    adapter.save(
        new Comment("cmt_1", "pst_abc", author, "Hi there", Instant.parse("2026-10-08T12:00:00Z")));

    Optional<Comment> found = adapter.findById("cmt_1");

    assertTrue(found.isPresent());
    Comment c = found.get();
    assertEquals("cmt_1", c.id());
    assertEquals("pst_abc", c.postId());
    assertEquals("Hi there", c.content());
    assertNotNull(c.author());
    assertEquals("usr_alice", c.author().id());
    assertEquals("Alice Doe", c.author().fullName());
    assertEquals("https://avatar.jpg", c.author().avatarUrl());
  }

  // ---------------------------------------------------------------------------
  // Helpers — minimal neo4j seed
  // ---------------------------------------------------------------------------

  private void seedUser(String userId, String username) {
    seedUser(userId, username, "Test User", "");
  }

  private void seedUser(String userId, String username, String fullName, String avatarUrl) {
    try (Session session = driver.session()) {
      session.run(
          "CREATE (u:Usuario {id: $id, username: $username, fullName: $fullName, "
              + "bio: '', avatarUrl: $avatarUrl, email: $email, passwordHash: 'x', "
              + "createdAt: datetime($createdAt)})",
          Map.of(
              "id",
              userId,
              "username",
              username,
              "fullName",
              fullName,
              "avatarUrl",
              avatarUrl == null ? "" : avatarUrl,
              "email",
              username + "@yaga.social",
              "createdAt",
              Instant.parse("2026-01-01T00:00:00Z").toString()));
    }
  }

  private void seedPost(String postId, String userId) {
    try (Session session = driver.session()) {
      session.run(
          "MATCH (u:Usuario {id: $userId}) "
              + "CREATE (u)-[:PUBLICA]->(p:Post {id: $postId, content: 'Body', "
              + "mediaUrl: null, createdAt: datetime($createdAt)})",
          Map.of(
              "userId",
              userId,
              "postId",
              postId,
              "createdAt",
              Instant.parse("2026-10-01T00:00:00Z").toString()));
    }
  }

  private void verifyComentarioNodeExists(String commentId, String expectedContent) {
    try (Session session = driver.session()) {
      var record =
          session
              .run(
                  "MATCH (c:Comentario {id: $id}) RETURN c.content AS content",
                  Map.of("id", commentId))
              .single();
      assertEquals(expectedContent, record.get("content").asString());
    }
  }

  private void verifyEscribeRelationshipExists(String userId, String commentId) {
    try (Session session = driver.session()) {
      long count =
          session
              .run(
                  "MATCH (u:Usuario {id: $userId})-[r:ESCRIBE]->(c:Comentario {id: $commentId}) "
                      + "RETURN count(r) AS n",
                  Map.of("userId", userId, "commentId", commentId))
              .single()
              .get("n")
              .asLong();
      assertEquals(1, count, "Expected exactly one [:ESCRIBE] relationship");
    }
  }

  private void verifyEnPostRelationshipExists(String commentId, String postId) {
    try (Session session = driver.session()) {
      long count =
          session
              .run(
                  "MATCH (c:Comentario {id: $commentId})-[r:EN_POST]->(p:Post {id: $postId}) "
                      + "RETURN count(r) AS n",
                  Map.of("commentId", commentId, "postId", postId))
              .single()
              .get("n")
              .asLong();
      assertEquals(1, count, "Expected exactly one [:EN_POST] relationship");
    }
  }
}
