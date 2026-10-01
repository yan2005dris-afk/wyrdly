package com.yaga.chat.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yaga.chat.domain.model.DirectMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(Neo4jTestResource.class)
class Neo4jDirectMessageRepositoryAdapterTest {

  @Inject
  Neo4jDirectMessageRepositoryAdapter repository;

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
  void shouldRespecPaginationOrder() {
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
