package com.wyrdly.notifications.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.notifications.domain.model.PushTarget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
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
 * Integration test for {@link Neo4jPushAudienceQueryAdapter} against a real Neo4j instance spun up
 * by Testcontainers. Covers audience filtering (only subscribed followers, never the author nor
 * non-followers) and keyset pagination without gaps or duplicates.
 */
@Testcontainers
class Neo4jPushAudienceQueryAdapterIT {

  @Container
  static final Neo4jContainer NEO4J_CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  static Driver driver;
  Neo4jPushAudienceQueryAdapter adapter;

  @BeforeAll
  static void setUpDriver() {
    driver = GraphDatabase.driver(NEO4J_CONTAINER.getBoltUrl(), AuthTokens.none());
  }

  @AfterAll
  static void tearDownDriver() {
    if (driver != null) {
      driver.close();
    }
  }

  @BeforeEach
  void setUp() {
    adapter = new Neo4jPushAudienceQueryAdapter(driver);
    try (Session session = driver.session()) {
      session.run("MATCH (n) DETACH DELETE n");
      session.run(
          "CREATE (:Usuario {id: 'author', pushEndpoint: 'https://push/a', "
              + "pushP256dh: 'k', pushAuth: 'a'})");
    }
  }

  private void follower(String id, boolean subscribed) {
    try (Session session = driver.session()) {
      session.run(
          "MATCH (author:Usuario {id: 'author'}) "
              + "CREATE (f:Usuario {id: $id}) "
              + "SET f.pushEndpoint = CASE WHEN $sub THEN 'https://push/' + $id END, "
              + "f.pushP256dh = CASE WHEN $sub THEN 'p256dh-' + $id END, "
              + "f.pushAuth = CASE WHEN $sub THEN 'auth-' + $id END "
              + "CREATE (f)-[:SIGUE]->(author)",
          org.neo4j.driver.Values.parameters("id", id, "sub", subscribed));
    }
  }

  @Test
  void returnsOnlySubscribedFollowersWithTheirSubscription() {
    follower("u1", true);
    follower("u2", false);
    try (Session session = driver.session()) {
      // Subscribed, but does not follow the author.
      session.run(
          "CREATE (:Usuario {id: 'stranger', pushEndpoint: 'https://push/s', "
              + "pushP256dh: 'k', pushAuth: 'a'})");
      // Author following themselves must never be part of their audience.
      session.run("MATCH (a:Usuario {id: 'author'}) CREATE (a)-[:SIGUE]->(a)");
    }

    List<PushTarget> result = adapter.findSubscribedFollowers("author", "", 100);

    assertEquals(1, result.size());
    PushTarget target = result.get(0);
    assertEquals("u1", target.userId());
    assertEquals("https://push/u1", target.subscription().endpoint());
    assertEquals("p256dh-u1", target.subscription().p256dh());
    assertEquals("auth-u1", target.subscription().auth());
  }

  @Test
  void returnsEmptyWhenAuthorHasNoFollowersOrDoesNotExist() {
    assertTrue(adapter.findSubscribedFollowers("author", "", 100).isEmpty());
    assertTrue(adapter.findSubscribedFollowers("ghost", "", 100).isEmpty());
  }

  @Test
  void keysetPaginationVisitsEveryFollowerExactlyOnceInOrder() {
    for (int i = 0; i < 7; i++) {
      follower("u" + i, true);
    }

    List<String> visited = new ArrayList<>();
    String cursor = "";
    while (true) {
      List<PushTarget> page = adapter.findSubscribedFollowers("author", cursor, 3);
      if (page.isEmpty()) {
        break;
      }
      page.forEach(t -> visited.add(t.userId()));
      cursor = page.getLast().userId();
    }

    assertEquals(List.of("u0", "u1", "u2", "u3", "u4", "u5", "u6"), visited);
  }
}
