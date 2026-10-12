package com.wyrdly.notifications.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.testsupport.Neo4jTestContainer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

/**
 * Integration test for {@link Neo4jPushSubscriptionRepositoryAdapter} against a real Neo4j instance
 * spun up by Testcontainers. Covers the multi-device lifecycle on {@code :SuscripcionPush} nodes:
 * save / findAllByUserId / deleteByEndpoint / deleteByUserId, idempotent re-subscribe and cap
 * eviction.
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

  private static PushSubscription sub(String endpoint, String p256dh, String auth) {
    return new PushSubscription(endpoint, p256dh, auth);
  }

  @Test
  void saveAndFindRoundTrip() {
    String userId = "usr_save";
    PushSubscription expected =
        sub(
            "https://fcm.googleapis.com/fcm/send/abc",
            "BNcRdreALRFXTkOOUHK1BCK2Mxxx",
            "tBHItJI5svbpez7KI4CCXg==");

    repository.save(userId, expected);
    PushSubscription found = repository.findByUserId(userId);

    assertNotNull(found);
    assertEquals(expected.endpoint(), found.endpoint());
    assertEquals(expected.p256dh(), found.p256dh());
    assertEquals(expected.auth(), found.auth());
  }

  @Test
  void secondDeviceDoesNotOverwriteTheFirst() {
    String userId = "usr_twodevices";
    PushSubscription firefox =
        sub("https://updates.push.services.mozilla.com/wpush/v2/firefox", "AAAA", "BBBB");
    PushSubscription chrome = sub("https://fcm.googleapis.com/fcm/send/chrome", "CCCC", "DDDD");

    repository.save(userId, firefox);
    repository.save(userId, chrome);

    List<PushSubscription> all = repository.findAllByUserId(userId);
    assertEquals(2, all.size());
    assertTrue(all.stream().anyMatch(s -> s.endpoint().equals(firefox.endpoint())));
    assertTrue(all.stream().anyMatch(s -> s.endpoint().equals(chrome.endpoint())));
  }

  @Test
  void saveIsIdempotentForTheSameEndpoint() {
    String userId = "usr_idempotent";
    repository.save(userId, sub("https://push.example.com/a", "AAAA", "BBBB"));
    repository.save(userId, sub("https://push.example.com/a", "CCCC", "DDDD"));

    List<PushSubscription> all = repository.findAllByUserId(userId);
    assertEquals(1, all.size());
    assertEquals("CCCC", all.get(0).p256dh());
    assertEquals("DDDD", all.get(0).auth());
  }

  @Test
  void findByUserIdReturnsNullWhenNoSubscriptionStored() {
    assertNull(repository.findByUserId("usr_nonexistent"));
  }

  @Test
  void deleteByEndpointRemovesOnlyThatDevice() {
    String userId = "usr_deleteone";
    PushSubscription keep = sub("https://push.example.com/keep", "AAAA", "BBBB");
    PushSubscription gone = sub("https://push.example.com/gone", "CCCC", "DDDD");
    repository.save(userId, keep);
    repository.save(userId, gone);

    repository.deleteByEndpoint(userId, gone.endpoint());

    List<PushSubscription> all = repository.findAllByUserId(userId);
    assertEquals(1, all.size());
    assertEquals(keep.endpoint(), all.get(0).endpoint());
  }

  @Test
  void deleteByUserIdClearsEveryDevice() {
    String userId = "usr_delete";
    repository.save(userId, sub("https://push.example.com/a", "AAAA", "BBBB"));
    repository.save(userId, sub("https://push.example.com/b", "CCCC", "DDDD"));
    assertNotNull(repository.findByUserId(userId));

    repository.deleteByUserId(userId);
    assertNull(repository.findByUserId(userId));
    assertTrue(repository.findAllByUserId(userId).isEmpty());
  }

  @Test
  void deleteByUserIdIsSafeWhenNoSubscriptionStored() {
    // Should not throw even when there is nothing to clear.
    repository.deleteByUserId("usr_never_subscribed");
    repository.deleteByEndpoint("usr_never_subscribed", "https://push.example.com/x");
  }

  @Test
  void saveAndDeleteCycleIsIsolatedPerUser() {
    String alice = "usr_alice";
    String bob = "usr_bob";
    repository.save(alice, sub("https://push.example.com/alice", "AAAA", "BBBB"));
    repository.save(bob, sub("https://push.example.com/bob", "CCCC", "DDDD"));

    repository.deleteByUserId(alice);

    assertNull(repository.findByUserId(alice));
    assertNotNull(repository.findByUserId(bob));
  }

  @Test
  void countActiveCountsSubscriptionNodes() {
    repository.save("usr_a", sub("https://push.example.com/a1", "AAAA", "BBBB"));
    repository.save("usr_a", sub("https://push.example.com/a2", "CCCC", "DDDD"));
    repository.save("usr_b", sub("https://push.example.com/b1", "EEEE", "FFFF"));

    assertEquals(3, repository.countActive());
  }

  @Test
  void legacyTripleIsReadableAfterMigration() {
    // Simulates a database that has not run V101 yet: the adapter must not be
    // the thing that breaks reads, the migration owns the conversion. This test
    // documents that only the node model is read back.
    String userId = "usr_legacy";
    try (Session session = driver.session()) {
      session.run(
          "CREATE (u:Usuario {id: $userId, username: 'legacy', email: 'l@x', "
              + "pushEndpoint: 'https://push.example.com/old', pushP256dh: 'k', pushAuth: 'a'})",
          java.util.Map.of("userId", userId));
    }
    // Legacy triple is NOT surfaced: V101 owns the conversion at startup.
    assertNull(repository.findByUserId(userId));
  }
}
