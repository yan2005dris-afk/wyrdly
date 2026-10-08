package com.wyrdly.notifications.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.notifications.domain.model.Notification;
import java.time.Instant;
import java.util.List;
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
 * Integration test for {@link Neo4jNotificationRepositoryAdapter} against a real Neo4j instance
 * spun up by Testcontainers. Covers save / findByRecipient / countUnread / markRead / markAllRead
 * on the {@code :Notificacion} node.
 */
@Testcontainers
class Neo4jNotificationRepositoryAdapterIT {

  @Container
  static final Neo4jContainer NEO4J_CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  static Driver driver;
  Neo4jNotificationRepositoryAdapter repository;

  @BeforeAll
  static void startDriver() {
    driver = GraphDatabase.driver(NEO4J_CONTAINER.getBoltUrl(), AuthTokens.none());
  }

  @AfterAll
  static void stopDriver() {
    driver.close();
  }

  @BeforeEach
  void setUp() {
    repository = new Neo4jNotificationRepositoryAdapter(driver);
    try (Session session = driver.session()) {
      session.run("MATCH (n:Notificacion) DETACH DELETE n");
    }
  }

  @AfterEach
  void cleanUp() {
    try (Session session = driver.session()) {
      session.run("MATCH (n:Notificacion) DETACH DELETE n");
    }
  }

  @Test
  void savesAndFindsNotification() {
    Notification notification =
        new Notification(
            "ntf_abc",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "Nuevo seguidor",
            "María comenzó a seguirte",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z"));

    repository.save(notification);

    List<Notification> found = repository.findByRecipient("usr_recipient", 0, 10);
    assertEquals(1, found.size());
    Notification loaded = found.get(0);
    assertEquals("ntf_abc", loaded.id());
    assertEquals("usr_recipient", loaded.recipientUserId());
    assertEquals("GRAPH_FOLLOW", loaded.type());
    assertEquals("usr_actor", loaded.actorId());
    assertEquals("Nuevo seguidor", loaded.title());
    assertFalse(loaded.isRead());
  }

  @Test
  void ordersResultsByCreatedAtDescending() {
    repository.save(
        new Notification(
            "ntf_first",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z")));
    repository.save(
        new Notification(
            "ntf_third",
            "usr_recipient",
            "POST_LIKE",
            "usr_actor",
            "t",
            "b",
            "/posts/pst_x",
            "pst_x",
            false,
            Instant.parse("2026-01-15T12:00:00Z")));
    repository.save(
        new Notification(
            "ntf_second",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T11:00:00Z")));

    List<Notification> found = repository.findByRecipient("usr_recipient", 0, 10);
    assertEquals(3, found.size());
    assertEquals("ntf_third", found.get(0).id());
    assertEquals("ntf_second", found.get(1).id());
    assertEquals("ntf_first", found.get(2).id());
  }

  @Test
  void paginatesResults() {
    for (int i = 0; i < 5; i++) {
      repository.save(
          new Notification(
              "ntf_" + i,
              "usr_recipient",
              "GRAPH_FOLLOW",
              "usr_actor",
              "t",
              "b",
              "/feed",
              null,
              false,
              Instant.parse("2026-01-15T10:00:00Z").plusSeconds(i)));
    }

    List<Notification> firstPage = repository.findByRecipient("usr_recipient", 0, 2);
    List<Notification> secondPage = repository.findByRecipient("usr_recipient", 1, 2);
    List<Notification> thirdPage = repository.findByRecipient("usr_recipient", 2, 2);
    assertEquals(2, firstPage.size());
    assertEquals(2, secondPage.size());
    assertEquals(1, thirdPage.size());
    assertEquals("ntf_4", firstPage.get(0).id());
    assertEquals("ntf_3", firstPage.get(1).id());
    assertEquals("ntf_2", secondPage.get(0).id());
    assertEquals("ntf_1", secondPage.get(1).id());
    assertEquals("ntf_0", thirdPage.get(0).id());
  }

  @Test
  void countsUnreadAndIgnoresRead() {
    repository.save(
        new Notification(
            "ntf_a",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z")));
    repository.save(
        new Notification(
            "ntf_b",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:01Z")));
    repository.save(
        new Notification(
            "ntf_c",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            true,
            Instant.parse("2026-01-15T10:00:02Z")));

    assertEquals(2L, repository.countUnread("usr_recipient"));
  }

  @Test
  void countsTotalNotifications() {
    repository.save(
        new Notification(
            "ntf_a",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z")));
    repository.save(
        new Notification(
            "ntf_b",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            true,
            Instant.parse("2026-01-15T10:00:01Z")));

    assertEquals(2L, repository.countTotal("usr_recipient"));
    assertEquals(0L, repository.countTotal("usr_other"));
  }

  @Test
  void marksReadForOwner() {
    repository.save(
        new Notification(
            "ntf_a",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z")));

    repository.markRead("ntf_a", "usr_recipient");

    List<Notification> found = repository.findByRecipient("usr_recipient", 0, 10);
    assertTrue(found.get(0).isRead());
    assertEquals(0L, repository.countUnread("usr_recipient"));
  }

  @Test
  void markReadIsNoOpForOtherUsers() {
    repository.save(
        new Notification(
            "ntf_a",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z")));

    repository.markRead("ntf_a", "usr_other");

    assertEquals(1L, repository.countUnread("usr_recipient"));
  }

  @Test
  void marksAllReadForUser() {
    repository.save(
        new Notification(
            "ntf_a",
            "usr_recipient",
            "GRAPH_FOLLOW",
            "usr_actor",
            "t",
            "b",
            "/feed",
            null,
            false,
            Instant.parse("2026-01-15T10:00:00Z")));
    repository.save(
        new Notification(
            "ntf_b",
            "usr_recipient",
            "POST_LIKE",
            "usr_actor",
            "t",
            "b",
            "/posts/x",
            "pst_x",
            false,
            Instant.parse("2026-01-15T10:00:01Z")));

    long updated = repository.markAllRead("usr_recipient");

    assertEquals(2L, updated);
    assertEquals(0L, repository.countUnread("usr_recipient"));
  }

  @Test
  void returnsEmptyForUnknownRecipient() {
    List<Notification> found = repository.findByRecipient("usr_ghost", 0, 10);
    assertNotNull(found);
    assertTrue(found.isEmpty());
    assertEquals(0L, repository.countUnread("usr_ghost"));
  }
}
