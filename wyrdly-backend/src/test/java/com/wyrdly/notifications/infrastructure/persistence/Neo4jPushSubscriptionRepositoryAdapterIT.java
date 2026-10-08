package com.wyrdly.notifications.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.testsupport.Neo4jTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

/**
 * Integration test for {@link Neo4jPushSubscriptionRepositoryAdapter} against a real Neo4j instance
 * spun up by Testcontainers. Covers the save / findByUserId / deleteByUserId lifecycle on the
 * {@code :Usuario.pushEndpoint/pushP256dh/pushAuth} triple.
 */
class Neo4jPushSubscriptionRepositoryAdapterIT {

  static final Driver driver = Neo4jTestContainer.driver();
  Neo4jPushSubscriptionRepositoryAdapter repository;

  @BeforeEach
  void setUp() {
    repository = new Neo4jPushSubscriptionRepositoryAdapter(driver);
    Neo4jTestContainer.deleteAllData();
  }

  @AfterEach
  void cleanUp() {
    Neo4jTestContainer.deleteAllData();
  }

  @Test
  void saveAndFindRoundTrip() {
    String userId = "usr_save";
    PushSubscription sub =
        new PushSubscription(
            "https://fcm.googleapis.com/fcm/send/abc",
            "BNcRdreALRFXTkOOUHK1BCK2Mxxx",
            "tBHItJI5svbpez7KI4CCXg==");

    repository.save(userId, sub);
    PushSubscription found = repository.findByUserId(userId);

    assertNotNull(found);
    assertEquals(sub.endpoint(), found.endpoint());
    assertEquals(sub.p256dh(), found.p256dh());
    assertEquals(sub.auth(), found.auth());
  }

  @Test
  void saveIsIdempotentAndOverwritesExistingSubscription() {
    String userId = "usr_idempotent";
    PushSubscription original =
        new PushSubscription(
            "https://fcm.googleapis.com/fcm/send/abc",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
    PushSubscription updated =
        new PushSubscription(
            "https://fcm.googleapis.com/fcm/send/def",
            "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
            "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

    repository.save(userId, original);
    repository.save(userId, updated);

    PushSubscription found = repository.findByUserId(userId);
    assertNotNull(found);
    assertEquals("https://fcm.googleapis.com/fcm/send/def", found.endpoint());
    assertEquals("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC", found.p256dh());
    assertEquals("DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", found.auth());
  }

  @Test
  void findByUserIdReturnsNullWhenNoSubscriptionStored() {
    PushSubscription found = repository.findByUserId("usr_nonexistent");
    assertNull(found);
  }

  @Test
  void findByUserIdReturnsNullWhenPropertiesAreMissing() {
    String userId = "usr_partial";
    try (Session session = driver.session()) {
      // Create the :Usuario node WITHOUT the push properties.
      session.run(
          "CREATE (u:Usuario {id: $userId, username: 'someone', email: 's@x'})",
          java.util.Map.of("userId", userId));
    }
    PushSubscription found = repository.findByUserId(userId);
    assertNull(found, "incomplete subscription should be treated as no subscription");
  }

  @Test
  void deleteByUserIdClearsPushProperties() {
    String userId = "usr_delete";
    PushSubscription sub =
        new PushSubscription(
            "https://fcm.googleapis.com/fcm/send/abc",
            "BNcRdreALRFXTkOOUHK1BCK2Mxxx",
            "tBHItJI5svbpez7KI4CCXg==");

    repository.save(userId, sub);
    assertNotNull(repository.findByUserId(userId));

    repository.deleteByUserId(userId);
    assertNull(repository.findByUserId(userId));
  }

  @Test
  void deleteByUserIdIsSafeWhenNoSubscriptionStored() {
    // Should not throw even when there is nothing to clear.
    repository.deleteByUserId("usr_never_subscribed");
  }

  @Test
  void saveAndDeleteCycleIsIsolatedPerUser() {
    String alice = "usr_alice";
    String bob = "usr_bob";
    PushSubscription aliceSub =
        new PushSubscription(
            "https://fcm.googleapis.com/fcm/send/alice",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
    PushSubscription bobSub =
        new PushSubscription(
            "https://fcm.googleapis.com/fcm/send/bob",
            "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
            "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

    repository.save(alice, aliceSub);
    repository.save(bob, bobSub);

    repository.deleteByUserId(alice);

    assertNull(repository.findByUserId(alice));
    PushSubscription bobFound = repository.findByUserId(bob);
    assertNotNull(bobFound);
    assertEquals(bobSub.endpoint(), bobFound.endpoint());
  }
}
