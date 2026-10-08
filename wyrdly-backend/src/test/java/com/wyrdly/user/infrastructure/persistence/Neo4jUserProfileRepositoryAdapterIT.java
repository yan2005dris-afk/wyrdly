package com.wyrdly.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.testsupport.Neo4jTestContainer;
import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.model.UserProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

/**
 * Real integration test against an ephemeral Neo4j 5.26 container (same image the project uses in
 * compose.yaml / CI). Exercises the actual Cypher in {@link Neo4jUserProfileRepositoryAdapter}
 * (COUNT{}/EXISTS{}/coalesce subqueries) end-to-end — this is deliberately NOT mocked, since the
 * mocked unit/component tests never verify that the Cypher itself is correct.
 */
class Neo4jUserProfileRepositoryAdapterIT {

  static final Driver driver = Neo4jTestContainer.driver();

  Neo4jUserProfileRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new Neo4jUserProfileRepositoryAdapter(driver);
  }

  @BeforeEach
  void cleanDatabase() {
    Neo4jTestContainer.deleteAllData();
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

    Optional<UserProfile> updated = adapter.updateProfile("usr_alice", "Alice Updated", null, null);

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

  @Test
  void followUser_CreatesFollowRelationship_WhenBothUsersExist() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");

    adapter.followUser("usr_bob", "usr_alice");

    assertTrue(adapter.isFollowing("usr_bob", "usr_alice"));
    assertFalse(adapter.isFollowing("usr_alice", "usr_bob"));
  }

  @Test
  void followUser_IsMergeIdempotent_FollowingTwiceCreatesOneRelationship() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");

    assertTrue(adapter.followUser("usr_bob", "usr_alice"));
    assertFalse(adapter.followUser("usr_bob", "usr_alice"));

    assertTrue(adapter.isFollowing("usr_bob", "usr_alice"));
    assertEquals(1L, countFollows("usr_bob", "usr_alice"));
  }

  @Test
  void followUser_StoresCreatedAt() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");

    adapter.followUser("usr_bob", "usr_alice");

    try (Session session = driver.session()) {
      List<String> keys =
          session
              .run("MATCH (:Usuario {id: 'usr_bob'})-[r:SIGUE]->() RETURN keys(r) AS k")
              .single()
              .get("k")
              .asList(org.neo4j.driver.Value::asString);
      assertEquals(List.of("createdAt"), keys);
    }
  }

  @Test
  void followUser_CreatesSingleRelationship_UnderConcurrentRequests() throws Exception {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");
    int requests = 20;
    ExecutorService pool = Executors.newFixedThreadPool(requests);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < requests; i++) {
        results.add(pool.submit(() -> adapter.followUser("usr_bob", "usr_alice")));
      }
      long created = 0;
      for (Future<Boolean> result : results) {
        if (result.get(30, TimeUnit.SECONDS)) {
          created++;
        }
      }

      assertEquals(1L, created);
      assertEquals(1L, countFollows("usr_bob", "usr_alice"));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void unfollowUser_DeletesFollowRelationship_WhenRelationshipExists() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");
    follow("usr_bob", "usr_alice");

    assertTrue(adapter.unfollowUser("usr_bob", "usr_alice"));

    assertFalse(adapter.isFollowing("usr_bob", "usr_alice"));
  }

  @Test
  void unfollowUser_IsIdempotent_UnfollowingNonExistentRelationshipSucceeds() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");

    assertFalse(adapter.unfollowUser("usr_bob", "usr_alice"));

    assertFalse(adapter.isFollowing("usr_bob", "usr_alice"));
  }

  @Test
  void isFollowing_ReturnsFalse_WhenNoRelationshipExists() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");

    boolean result = adapter.isFollowing("usr_bob", "usr_alice");

    assertFalse(result);
  }

  @Test
  void isFollowing_ReturnsTrue_WhenRelationshipExists() {
    seedUser("usr_alice", "alice", "Alice", "", "");
    seedUser("usr_bob", "bob", "Bob", "", "");
    follow("usr_bob", "usr_alice");

    boolean result = adapter.isFollowing("usr_bob", "usr_alice");

    assertTrue(result);
  }

  @Test
  void validateUserExists_DoesNotThrow_WhenUserExists() {
    seedUser("usr_alice", "alice", "Alice", "", "");

    adapter.validateUserExists("usr_alice");
  }

  @Test
  void validateUserExists_ThrowsUserProfileNotFoundException_WhenUserDoesNotExist() {
    UserProfileNotFoundException exception =
        assertThrows(
            UserProfileNotFoundException.class, () -> adapter.validateUserExists("usr_ghost"));

    assertTrue(exception.getMessage().contains("no existe"));
  }

  private void seedUser(String id, String username, String fullName, String bio, String avatarUrl) {
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
              "email", username + "@wyrdly.social",
              "createdAt", Instant.parse("2026-01-01T00:00:00Z").toString()));
    }
  }

  private long countFollows(String followerId, String targetId) {
    try (Session session = driver.session()) {
      return session
          .run(
              "MATCH (:Usuario {id: $followerId})-[r:SIGUE]->(:Usuario {id: $targetId}) "
                  + "RETURN count(r) AS c",
              Map.of("followerId", followerId, "targetId", targetId))
          .single()
          .get("c")
          .asLong();
    }
  }

  private void follow(String followerId, String targetId) {
    try (Session session = driver.session()) {
      session.run(
          "MATCH (a:Usuario {id: $followerId}), (b:Usuario {id: $targetId}) "
              + "MERGE (a)-[r:SIGUE]->(b) ON CREATE SET r.createdAt = datetime()",
          Map.of("followerId", followerId, "targetId", targetId));
    }
  }
}
