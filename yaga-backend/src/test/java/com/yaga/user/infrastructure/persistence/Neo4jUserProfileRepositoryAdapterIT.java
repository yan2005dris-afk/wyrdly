package com.yaga.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yaga.user.domain.model.UserProfile;
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

/**
 * Real integration test against an ephemeral Neo4j 5.26 container (same image the project uses
 * in compose.yaml / CI). Exercises the actual Cypher in {@link Neo4jUserProfileRepositoryAdapter}
 * (COUNT{}/EXISTS{}/coalesce subqueries) end-to-end — this is deliberately NOT mocked, since the
 * mocked unit/component tests never verify that the Cypher itself is correct.
 */
@Testcontainers
class Neo4jUserProfileRepositoryAdapterIT {

  @Container
  static final Neo4jContainer NEO4J_CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  static Driver driver;

  Neo4jUserProfileRepositoryAdapter adapter;

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
    adapter = new Neo4jUserProfileRepositoryAdapter(driver);
  }

  @AfterEach
  void cleanDatabase() {
    try (Session session = driver.session()) {
      session.run("MATCH (n) DETACH DELETE n");
    }
  }

  @Test
  void findProfileByUsername_ComputesFollowerAndFollowingCounts_ViaRealCypher() {
    seedUser("usr_alice", "alice", "Alice", "Bio", "http://avatar/alice");
    seedUser("usr_bob", "bob", "Bob", "", "");
    seedUser("usr_carol", "carol", "Carol", "", "");
    follow("usr_bob", "usr_alice");
    follow("usr_carol", "usr_alice");
    follow("usr_alice", "usr_bob");

    Optional<UserProfile> profile = adapter.findProfileByUsername("alice", null);

    assertTrue(profile.isPresent());
    assertEquals("usr_alice", profile.get().id());
    assertEquals(2, profile.get().followersCount());
    assertEquals(1, profile.get().followingCount());
    assertFalse(profile.get().isFollowing());
  }

  @Test
  void findProfileByUsername_ReturnsIsFollowingTrue_WhenViewerFollowsTarget() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");
    follow("usr_bob", "usr_alice");

    Optional<UserProfile> profile = adapter.findProfileByUsername("alice", "usr_bob");

    assertTrue(profile.get().isFollowing());
  }

  @Test
  void findProfileByUsername_ReturnsIsFollowingFalse_WhenViewerDoesNotFollowTarget() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");
    // No follow relationship created between bob and alice.

    Optional<UserProfile> profile = adapter.findProfileByUsername("alice", "usr_bob");

    assertFalse(profile.get().isFollowing());
  }

  @Test
  void findProfileByUsername_ReturnsEmpty_WhenUsernameDoesNotExist() {
    Optional<UserProfile> profile = adapter.findProfileByUsername("ghost", null);

    assertTrue(profile.isEmpty());
  }

  @Test
  void updateProfile_UpdatesOnlyProvidedFields_AndPreservesTheRest() {
    seedUser("usr_alice", "alice", "Alice Original", "Bio original", "http://old-avatar");
    seedUser("usr_bob", "bob", "Bob", "", "");
    follow("usr_bob", "usr_alice");

    Optional<UserProfile> updated =
        adapter.updateProfile("usr_alice", "Alice Updated", null, null);

    assertTrue(updated.isPresent());
    assertEquals("Alice Updated", updated.get().fullName());
    assertEquals("Bio original", updated.get().bio());
    assertEquals("http://old-avatar", updated.get().avatarUrl());
    assertEquals(1, updated.get().followersCount());

    Optional<UserProfile> reloaded = adapter.findProfileByUsername("alice", null);
    assertEquals("Alice Updated", reloaded.get().fullName());
  }

  @Test
  void updateProfile_ReturnsEmpty_WhenUserIdDoesNotExist() {
    Optional<UserProfile> updated = adapter.updateProfile("usr_ghost", "Name", null, null);

    assertTrue(updated.isEmpty());
  }

  private void seedUser(
      String id, String username, String fullName, String bio, String avatarUrl) {
    try (Session session = driver.session()) {
      session.run(
          "CREATE (u:Usuario {id: $id, username: $username, fullName: $fullName, "
              + "bio: $bio, avatarUrl: $avatarUrl, email: $email, passwordHash: 'x', "
              + "createdAt: datetime($createdAt)})",
          Map.of(
              "id", id,
              "username", username,
              "fullName", fullName,
              "bio", bio,
              "avatarUrl", avatarUrl,
              "email", username + "@yaga.social",
              "createdAt", Instant.parse("2026-01-01T00:00:00Z").toString()));
    }
  }

  private void follow(String followerId, String targetId) {
    try (Session session = driver.session()) {
      session.run(
          "MATCH (a:Usuario {id: $followerId}), (b:Usuario {id: $targetId}) "
              + "MERGE (a)-[:SIGUE {fecha: datetime()}]->(b)",
          Map.of("followerId", followerId, "targetId", targetId));
    }
  }
}
