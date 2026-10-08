package com.wyrdly.notifications.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.notifications.domain.model.PushTarget;
import com.wyrdly.testsupport.Neo4jTestContainer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

/**
 * Integration test for {@link Neo4jPushAudienceQueryAdapter} against a real Neo4j instance spun up
 * by Testcontainers. Covers audience filtering (only subscribed followers, never the author nor
 * non-followers) and keyset pagination without gaps or duplicates.
 */
class Neo4jPushAudienceQueryAdapterIT {

  static final Driver driver = Neo4jTestContainer.driver();
  Neo4jPushAudienceQueryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new Neo4jPushAudienceQueryAdapter(driver);
    Neo4jTestContainer.deleteAllData();
    try (Session session = driver.session()) {
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
  void returnsFollowersWithTheirSubscriptionIfPresent() {
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

    List<PushTarget> result = adapter.findAllFollowers("author", "", 100);

    assertEquals(2, result.size());
    PushTarget u1 = result.stream().filter(t -> t.userId().equals("u1")).findFirst().orElseThrow();
    assertEquals("https://push/u1", u1.subscription().endpoint());
    assertEquals("p256dh-u1", u1.subscription().p256dh());
    assertEquals("auth-u1", u1.subscription().auth());

    PushTarget u2 = result.stream().filter(t -> t.userId().equals("u2")).findFirst().orElseThrow();
    org.junit.jupiter.api.Assertions.assertNull(u2.subscription());
  }

  @Test
  void returnsEmptyWhenAuthorHasNoFollowersOrDoesNotExist() {
    assertTrue(adapter.findAllFollowers("author", "", 100).isEmpty());
    assertTrue(adapter.findAllFollowers("ghost", "", 100).isEmpty());
  }

  @Test
  void keysetPaginationVisitsEveryFollowerExactlyOnceInOrder() {
    for (int i = 0; i < 7; i++) {
      follower("u" + i, i % 2 == 0);
    }

    List<String> visited = new ArrayList<>();
    String cursor = "";
    while (true) {
      List<PushTarget> page = adapter.findAllFollowers("author", cursor, 3);
      if (page.isEmpty()) {
        break;
      }
      page.forEach(t -> visited.add(t.userId()));
      cursor = page.getLast().userId();
    }

    assertEquals(List.of("u0", "u1", "u2", "u3", "u4", "u5", "u6"), visited);
  }
}

