package com.yaga.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yaga.user.application.dto.UserSearchResultDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class Neo4jUserSearchRepositoryAdapterIT {

  @Container
  static final Neo4jContainer NEO4J_CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  static Driver driver;

  Neo4jUserSearchRepositoryAdapter adapter;

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
    adapter = new Neo4jUserSearchRepositoryAdapter(driver);
  }

  @AfterEach
  void cleanDatabase() {
    try (Session session = driver.session()) {
      session.run("MATCH (n) DETACH DELETE n");
    }
  }

  @Test
  void findByText_matchesUsernameExactCase() {
    seedUser("usr_alice", "alice", "Alice Chen", "Backend dev");
    seedUser("usr_bob", "bob", "Bob Stone", null);
    seedUser("usr_carol", "carol", "Carol Diaz", null);

    List<UserSearchResultDto> results = adapter.findByText("alice", "usr_viewer", 0, 20);

    assertEquals(1, results.size());
    assertEquals("usr_alice", results.get(0).id());
    assertEquals("alice", results.get(0).username());
    assertEquals("Alice Chen", results.get(0).fullName());
    assertEquals("Backend dev", results.get(0).bio());
  }

  @Test
  void findByText_matchesFullNamePartiallyCaseInsensitive() {
    seedUser("usr_alice", "alice", "Alice Chen", null);
    seedUser("usr_alfredo", "alfredo", "Alfredo Gomez", null);
    seedUser("usr_bob", "bob", "Bob Stone", null);

    List<UserSearchResultDto> results = adapter.findByText("ALFR", "usr_viewer", 0, 20);

    assertEquals(1, results.size());
    assertEquals("usr_alfredo", results.get(0).id());
  }

  @Test
  void findByText_matchesBioSubstring() {
    seedUser("usr_alice", "alice", "Alice Chen", "Loves Neo4j and graph databases");
    seedUser("usr_bob", "bob", "Bob Stone", "Java backend");

    List<UserSearchResultDto> results = adapter.findByText("neo4j", "usr_viewer", 0, 20);

    assertEquals(1, results.size());
    assertEquals("usr_alice", results.get(0).id());
    assertEquals("Loves Neo4j and graph databases", results.get(0).bio());
  }

  @Test
  void findByText_excludesViewerFromResults() {
    seedUser("usr_alice", "alice", "Alice Chen", null);
    seedUser("usr_alicia", "alicia", "Alicia Keys", null);

    List<UserSearchResultDto> results = adapter.findByText("ali", "usr_alice", 0, 20);

    assertEquals(1, results.size());
    assertEquals("usr_alicia", results.get(0).id());
  }

  @Test
  void findByText_returnsIsFollowingTrue_whenViewerFollowsUser() {
    seedUser("usr_alice", "alice", "Alice Chen", null);
    seedUser("usr_bob", "bob", "Bob Stone", null);
    createFollow("usr_viewer", "usr_bob");

    List<UserSearchResultDto> results = adapter.findByText("bob", "usr_viewer", 0, 20);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isFollowing());
  }

  @Test
  void findByText_returnsIsFollowingFalse_whenViewerDoesNotFollowUser() {
    seedUser("usr_bob", "bob", "Bob Stone", null);

    List<UserSearchResultDto> results = adapter.findByText("bob", "usr_viewer", 0, 20);

    assertEquals(1, results.size());
    assertFalse(results.get(0).isFollowing());
    assertNull(results.get(0).mutualConnectionSnippet());
  }

  @Test
  void findByText_computesMutualCountCorrectly() {
    seedUser("usr_viewer", "viewer", "Viewer", null);
    seedUser("usr_alice", "alice", "Alice Chen", null);
    seedUser("usr_bob", "bob", "Bob", null);
    seedUser("usr_carol", "carol", "Carol", null);
    seedUser("usr_dave", "dave", "Dave", null);

    createFollow("usr_viewer", "usr_bob");
    createFollow("usr_viewer", "usr_carol");
    createFollow("usr_bob", "usr_alice");
    createFollow("usr_carol", "usr_alice");

    List<UserSearchResultDto> results = adapter.findByText("alice", "usr_viewer", 0, 20);

    assertEquals(1, results.size());
    assertEquals("2 amigos en común", results.get(0).mutualConnectionSnippet());
  }

  @Test
  void findByText_returnsSingleMutualSnippet_whenOneMutualConnection() {
    seedUser("usr_viewer", "viewer", "Viewer", null);
    seedUser("usr_alice", "alice", "Alice Chen", null);
    seedUser("usr_bob", "bob", "Bob", null);

    createFollow("usr_viewer", "usr_bob");
    createFollow("usr_bob", "usr_alice");

    List<UserSearchResultDto> results = adapter.findByText("alice", "usr_viewer", 0, 20);

    assertEquals(1, results.size());
    assertEquals("1 amigo en común", results.get(0).mutualConnectionSnippet());
  }

  @Test
  void findByText_ordersFollowedUsersFirst() {
    seedUser("usr_alice", "alice", "Alice A", null);
    seedUser("usr_alicia", "alicia", "Alicia B", null);
    seedUser("usr_viewer", "viewer", "Viewer", null);

    createFollow("usr_viewer", "usr_alicia");

    List<UserSearchResultDto> results = adapter.findByText("ali", "usr_viewer", 0, 20);

    assertEquals(2, results.size());
    assertEquals("usr_alicia", results.get(0).id());
    assertTrue(results.get(0).isFollowing());
    assertEquals("usr_alice", results.get(1).id());
    assertFalse(results.get(1).isFollowing());
  }

  @Test
  void findByText_respectsPagination() {
    for (int i = 0; i < 5; i++) {
      seedUser("usr_alice_" + i, "alice" + i, "Alice " + i, null);
    }

    List<UserSearchResultDto> firstPage = adapter.findByText("alice", "usr_viewer", 0, 2);
    List<UserSearchResultDto> secondPage = adapter.findByText("alice", "usr_viewer", 1, 2);
    List<UserSearchResultDto> thirdPage = adapter.findByText("alice", "usr_viewer", 2, 2);

    assertEquals(2, firstPage.size());
    assertEquals(2, secondPage.size());
    assertEquals(1, thirdPage.size());

    assertNotNull(firstPage.get(0).id());
    assertNotNull(secondPage.get(0).id());
    assertNotNull(thirdPage.get(0).id());
  }

  @Test
  void countByText_returnsCorrectTotal() {
    seedUser("usr_alice", "alice", "Alice Chen", null);
    seedUser("usr_alicia", "alicia", "Alicia Keys", null);
    seedUser("usr_alfredo", "alfredo", "Alfredo", null);
    seedUser("usr_bob", "bob", "Bob", null);

    int total = adapter.countByText("ali", "usr_viewer");

    assertEquals(3, total);
  }

  @Test
  void countByText_excludesViewer() {
    seedUser("usr_alice", "alice", "Alice", null);
    seedUser("usr_alicia", "alicia", "Alicia", null);

    int total = adapter.countByText("ali", "usr_alice");

    assertEquals(1, total);
  }

  @Test
  void findByText_returnsEmptyList_whenNoMatches() {
    seedUser("usr_alice", "alice", "Alice", null);

    List<UserSearchResultDto> results = adapter.findByText("zzzzz", "usr_viewer", 0, 20);

    assertTrue(results.isEmpty());
  }

  private void seedUser(String id, String username, String fullName, String bio) {
    try (Session session = driver.session()) {
      session.run(
          "CREATE (u:Usuario {id: $id, username: $username, fullName: $fullName, "
              + "bio: $bio, avatarUrl: '', email: $email, passwordHash: 'x', "
              + "createdAt: datetime($createdAt)})",
          Map.of(
              "id",
              id,
              "username",
              username,
              "fullName",
              fullName,
              "bio",
              bio == null ? "" : bio,
              "email",
              username + "@yaga.social",
              "createdAt",
              Instant.parse("2026-01-01T00:00:00Z").toString()));
    }
  }

  private void createFollow(String followerId, String followingId) {
    try (Session session = driver.session()) {
      session.run(
          "MATCH (a:Usuario {id: $follower}), (b:Usuario {id: $following}) "
              + "MERGE (a)-[:SIGUE {fecha: datetime()}]->(b)",
          Map.of("follower", followerId, "following", followingId));
    }
  }
}
