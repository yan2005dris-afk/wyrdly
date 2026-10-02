package com.wyrdly.chat.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.chat.domain.model.DirectMessage;
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

@Testcontainers
class Neo4jDirectMessageRepositoryAdapterIT {

  @Container
  static final Neo4jContainer NEO4J_CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  static Driver driver;
  Neo4jDirectMessageRepositoryAdapter repository;

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
    repository = new Neo4jDirectMessageRepositoryAdapter(driver);
    try (Session session = driver.session()) {
      session.run("MATCH (n) DETACH DELETE n");
    }
  }

  @AfterEach
  void cleanUp() {
    try (Session session = driver.session()) {
      session.run("MATCH (n) DETACH DELETE n");
    }
  }

  @Test
  void shouldSaveAndRetrieveMessage() {
    String u1 = "user_1";
    String u2 = "user_2";
    DirectMessage message = new DirectMessage(u1, u2, "Hello World!");

    repository.save(message);

    List<DirectMessage> messages = repository.findBetweenUsers(u1, u2, 0, 10);

    assertNotNull(messages);
    assertEquals(1, messages.size());
    assertEquals("Hello World!", messages.get(0).getContent());
    assertEquals(u1, messages.get(0).getSenderId());
    assertEquals(u2, messages.get(0).getRecipientId());
  }

  @Test
  void shouldCountMessages() {
    String u1 = "user_3";
    String u2 = "user_4";
    DirectMessage msg1 = new DirectMessage(u1, u2, "Message 1");
    DirectMessage msg2 = new DirectMessage(u2, u1, "Message 2");

    repository.save(msg1);
    repository.save(msg2);

    long count = repository.countBetweenUsers(u1, u2);

    assertEquals(2, count);
  }

  @Test
  void shouldReturnEmptyListForNoMessages() {
    String u1 = "user_5";
    String u2 = "user_6";
    List<DirectMessage> messages = repository.findBetweenUsers(u1, u2, 0, 10);

    assertTrue(messages.isEmpty());
  }

  @Test
  void shouldRespectPaginationOrder() {
    String u1 = "user_7";
    String u2 = "user_8";
    for (int i = 1; i <= 5; i++) {
      DirectMessage msg = new DirectMessage(u1, u2, "Message " + i);
      repository.save(msg);
    }

    List<DirectMessage> firstPage = repository.findBetweenUsers(u1, u2, 0, 2);
    List<DirectMessage> secondPage = repository.findBetweenUsers(u1, u2, 2, 2);

    assertEquals(2, firstPage.size());
    assertEquals(2, secondPage.size());
  }
}
