package com.yaga.post.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yaga.post.domain.model.Post;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
class Neo4jPostRepositoryAdapterIT {

  @Container
  static final Neo4jContainer NEO4J_CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  static Driver driver;

  Neo4jPostRepositoryAdapter adapter;

  @BeforeAll
  static void setUpDriver() {
    driver = GraphDatabase.driver(NEO4J_CONTAINER.getBoltUrl(), AuthTokens.none());
  }

  @AfterAll
  static void tearDownDriver() {
    driver.close();
  }

  @BeforeEach
  void setUp() {
    adapter = new Neo4jPostRepositoryAdapter(driver);
  }

  @AfterEach
  void cleanDatabase() {
    try (Session session = driver.session()) {
      session.run("MATCH (n) DETACH DELETE n");
    }
  }

  @Test
  void save_PersistsPostToNeo4j() {
    seedUser("usr_123", "testuser");
    Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
    Post post = new Post("pst_abc123", "usr_123", "This is a test post", null, createdAt);

    Post saved = adapter.save(post);

    assertTrue(saved != null);
    assertEquals("pst_abc123", saved.id());
    assertEquals("usr_123", saved.userId());
    assertEquals("This is a test post", saved.content());

    verifyPostExistsInNeo4j("pst_abc123", "This is a test post");
  }

  @Test
  void save_CreatesPublicaRelationshipBetweenAuthorAndPost() {
    seedUser("usr_123", "testuser");
    Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
    Post post = new Post("pst_abc123", "usr_123", "Test post", null, createdAt);

    adapter.save(post);

    verifyPublicaRelationshipExists("usr_123", "pst_abc123");
  }

  @Test
  void save_PersistsMediaUrlWhenProvided() {
    seedUser("usr_123", "testuser");
    Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
    String mediaUrl = "https://example.com/image.jpg";
    Post post = new Post("pst_abc123", "usr_123", "Post with media", mediaUrl, createdAt);

    adapter.save(post);

    try (Session session = driver.session()) {
      var record =
          session
              .run(
                  "MATCH (p:Post {id: $id}) RETURN p.mediaUrl AS mediaUrl",
                  Map.of("id", "pst_abc123"))
              .single();
      assertEquals(mediaUrl, record.get("mediaUrl").asString());
    }
  }

  @Test
  void findById_ReturnsPostWhenExists() {
    seedUser("usr_123", "testuser");
    Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
    Post post = new Post("pst_abc123", "usr_123", "Test content", null, createdAt);

    adapter.save(post);

    Optional<Post> found = adapter.findById("pst_abc123");

    assertTrue(found.isPresent());
    assertEquals("pst_abc123", found.get().id());
    assertEquals("usr_123", found.get().userId());
    assertEquals("Test content", found.get().content());
    assertEquals(createdAt, found.get().createdAt());
  }

  @Test
  void findById_ReturnsEmptyWhenPostDoesNotExist() {
    Optional<Post> found = adapter.findById("pst_nonexistent");

    assertTrue(found.isEmpty());
  }

  @Test
  void findById_ReturnsPostWithMediaUrl() {
    seedUser("usr_123", "testuser");
    Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
    String mediaUrl = "https://example.com/image.jpg";
    Post post = new Post("pst_abc123", "usr_123", "Content", mediaUrl, createdAt);

    adapter.save(post);

    Optional<Post> found = adapter.findById("pst_abc123");

    assertTrue(found.isPresent());
    assertEquals(mediaUrl, found.get().mediaUrl());
  }

  @Test
  void findById_PreservesCreatedAtTimestamp() {
    seedUser("usr_123", "testuser");
    Instant createdAt = Instant.parse("2026-09-28T15:30:45Z");
    Post post = new Post("pst_abc123", "usr_123", "Content", null, createdAt);

    adapter.save(post);

    Optional<Post> found = adapter.findById("pst_abc123");

    assertTrue(found.isPresent());
    assertEquals(createdAt, found.get().createdAt());
  }

  @Test
  void save_MultiplePosts_AllPersistIndependently() {
    seedUser("usr_123", "testuser");
    Instant time1 = Instant.parse("2026-09-28T12:00:00Z");
    Instant time2 = Instant.parse("2026-09-28T12:05:00Z");

    Post post1 = new Post("pst_001", "usr_123", "First post", null, time1);
    Post post2 = new Post("pst_002", "usr_123", "Second post", null, time2);

    adapter.save(post1);
    adapter.save(post2);

    Optional<Post> found1 = adapter.findById("pst_001");
    Optional<Post> found2 = adapter.findById("pst_002");

    assertTrue(found1.isPresent());
    assertTrue(found2.isPresent());
    assertEquals("First post", found1.get().content());
    assertEquals("Second post", found2.get().content());
  }

  @Test
  void findById_HandlesNullMediaUrl() {
    seedUser("usr_123", "testuser");
    Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
    Post post = new Post("pst_abc123", "usr_123", "Content", null, createdAt);

    adapter.save(post);

    Optional<Post> found = adapter.findById("pst_abc123");

    assertTrue(found.isPresent());
    assertEquals(null, found.get().mediaUrl());
  }

  private void seedUser(String userId, String username) {
    try (Session session = driver.session()) {
      session.run(
          "CREATE (u:Usuario {id: $id, username: $username, fullName: $fullName, "
              + "bio: '', avatarUrl: '', email: $email, passwordHash: 'x', "
              + "createdAt: datetime($createdAt)})",
          Map.of(
              "id",
              userId,
              "username",
              username,
              "fullName",
              "Test User",
              "email",
              username + "@yaga.social",
              "createdAt",
              Instant.parse("2026-01-01T00:00:00Z").toString()));
    }
  }

  private void verifyPostExistsInNeo4j(String postId, String expectedContent) {
    try (Session session = driver.session()) {
      var record =
          session
              .run("MATCH (p:Post {id: $id}) RETURN p.content AS content", Map.of("id", postId))
              .single();
      assertEquals(expectedContent, record.get("content").asString());
    }
  }

  private void verifyPublicaRelationshipExists(String userId, String postId) {
    try (Session session = driver.session()) {
      var result =
          session.run(
              "MATCH (u:Usuario {id: $userId})-[r:PUBLICA]->(p:Post {id: $postId}) "
                  + "RETURN COUNT(r) AS count",
              Map.of("userId", userId, "postId", postId));

      long count = result.single().get("count").asLong();
      assertEquals(1, count, "Expected exactly one [:PUBLICA] relationship");
    }
  }
}
