package com.wyrdly.post.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * Integration tests for the HU09 reaction toggle Cypher query. Runs against a Testcontainers Neo4j
 * 5.26 instance. Exercises the ADDED/REMOVED/UPDATED transitions and a 50-thread fan-in to validate
 * concurrency safety.
 *
 * <p>Skipped at compile-time when Docker is unavailable: the {@code skipITs} profile is on by
 * default. Run with {@code -DskipITs=false -Dtest='*IT'} when Docker is present.
 */
@Testcontainers
class Neo4jPostReactionRepositoryIT {

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
  void react_AddsReactionWhenNoneExists() {
    seedUser("usr_alice");
    seedPost("pst_1", "usr_alice");

    ReactionResult result = adapter.react("usr_alice", "pst_1", ReactionType.LIKE);

    assertEquals("pst_1", result.postId());
    assertEquals(ReactionStatus.ADDED, result.status());
    assertEquals(ReactionType.LIKE, result.reactionType());
    assertEquals(1L, result.totalReactions());
    assertReaccionaExists("usr_alice", "pst_1", "LIKE");
  }

  @Test
  void react_RemovesReactionWhenSameType() {
    seedUser("usr_alice");
    seedPost("pst_1", "usr_alice");
    adapter.react("usr_alice", "pst_1", ReactionType.LIKE);

    ReactionResult result = adapter.react("usr_alice", "pst_1", ReactionType.LIKE);

    assertEquals(ReactionStatus.REMOVED, result.status());
    assertNull(result.reactionType());
    assertEquals(0L, result.totalReactions());
    assertReaccionaAbsent("usr_alice", "pst_1");
  }

  @Test
  void react_UpdatesReactionWhenDifferentType() {
    seedUser("usr_alice");
    seedPost("pst_1", "usr_alice");
    adapter.react("usr_alice", "pst_1", ReactionType.LIKE);

    ReactionResult result = adapter.react("usr_alice", "pst_1", ReactionType.LOVE);

    assertEquals(ReactionStatus.UPDATED, result.status());
    assertEquals(ReactionType.LOVE, result.reactionType());
    assertEquals(1L, result.totalReactions());
    assertReaccionaExists("usr_alice", "pst_1", "LOVE");
  }

  @Test
  void react_ThrowsWhenPostNotFound() {
    seedUser("usr_alice");
    assertThrows(
        PostNotFoundException.class,
        () -> adapter.react("usr_alice", "pst_missing", ReactionType.LIKE));
  }

  @Test
  void react_CountsAllReactionsAcrossUsers() {
    seedUser("usr_alice");
    seedUser("usr_bob");
    seedUser("usr_carol");
    seedPost("pst_1", "usr_alice");

    adapter.react("usr_alice", "pst_1", ReactionType.LIKE);
    adapter.react("usr_bob", "pst_1", ReactionType.LOVE);
    adapter.react("usr_carol", "pst_1", ReactionType.CELEBRATE);

    assertEquals(1L, adapter.react("usr_alice", "pst_1", ReactionType.LIKE).totalReactions());
  }

  @Test
  void react_Concurrent50Users_NoDuplicates() throws InterruptedException {
    seedPost("pst_viral", "usr_author");
    int threads = 50;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger failures = new AtomicInteger();

    try {
      for (int i = 0; i < threads; i++) {
        String userId = "usr_fan_" + i;
        seedUser(userId);
        pool.submit(
            () -> {
              try {
                start.await();
                adapter.react(userId, "pst_viral", ReactionType.LIKE);
              } catch (Throwable t) {
                failures.incrementAndGet();
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertTrue(done.await(60, TimeUnit.SECONDS), "Concurrent reactions timed out");
    } finally {
      pool.shutdownNow();
    }

    assertEquals(0, failures.get(), "No concurrent reaction should fail");
    try (Session session = driver.session()) {
      long count =
          session
              .run(
                  "MATCH (:Usuario)-[r:REACCIONA]->(:Post {id: $id}) RETURN count(r) AS n",
                  Map.of("id", "pst_viral"))
              .single()
              .get("n")
              .asLong();
      assertEquals(threads, count);
    }
  }

  // --- helpers ---------------------------------------------------------------

  private void seedUser(String userId) {
    try (Session session = driver.session()) {
      session.run(
          "MERGE (u:Usuario {id: $id}) SET u.username = $id, u.fullName = $id",
          Map.of("id", userId));
    }
  }

  private void seedPost(String postId, String authorId) {
    try (Session session = driver.session()) {
      session.run(
          "MERGE (p:Post {id: $postId}) SET p.content = 'x', p.createdAt = datetime() "
              + "MERGE (u:Usuario {id: $authorId}) "
              + "MERGE (u)-[:PUBLICA]->(p)",
          Map.of("postId", postId, "authorId", authorId));
    }
  }

  private void assertReaccionaExists(String userId, String postId, String tipo) {
    try (Session session = driver.session()) {
      var record =
          session
              .run(
                  "MATCH (:Usuario {id: $uid})-[r:REACCIONA]->(:Post {id: $pid}) "
                      + "RETURN r.tipo AS tipo LIMIT 1",
                  Map.of("uid", userId, "pid", postId))
              .single();
      assertNotNull(record);
      assertEquals(tipo, record.get("tipo").asString());
    }
  }

  private void assertReaccionaAbsent(String userId, String postId) {
    try (Session session = driver.session()) {
      long count =
          session
              .run(
                  "MATCH (:Usuario {id: $uid})-[r:REACCIONA]->(:Post {id: $pid}) "
                      + "RETURN count(r) AS n",
                  Map.of("uid", userId, "pid", postId))
              .single()
              .get("n")
              .asLong();
      assertEquals(0L, count);
    }
  }
}
