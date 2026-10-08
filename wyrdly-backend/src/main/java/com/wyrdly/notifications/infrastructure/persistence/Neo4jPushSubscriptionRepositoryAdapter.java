package com.wyrdly.notifications.infrastructure.persistence;

import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.domain.model.PushSubscription;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/**
 * Neo4j-backed implementation of {@link PushSubscriptionRepositoryPort}.
 *
 * <p>Persists the {@code pushEndpoint / pushP256dh / pushAuth} triple on the {@code :Usuario} node
 * identified by {@code id}. {@code MATCH ... WHERE u.id = $userId} is preferred over {@code MERGE}
 * because the user is always created upstream (auth flow) before a push subscription can exist.
 */
@ApplicationScoped
@Unremovable
public class Neo4jPushSubscriptionRepositoryAdapter implements PushSubscriptionRepositoryPort {

  private static final Logger LOG =
      Logger.getLogger(Neo4jPushSubscriptionRepositoryAdapter.class.getName());

  private static final String CYPHER_READ =
      "MATCH (u:Usuario {id: $userId}) "
          + "RETURN u.pushEndpoint AS endpoint, u.pushP256dh AS p256dh, u.pushAuth AS auth";

  private static final String CYPHER_SAVE =
      "MERGE (u:Usuario {id: $userId}) "
          + "SET u.pushEndpoint = $endpoint, u.pushP256dh = $p256dh, u.pushAuth = $auth, "
          + "u.username = coalesce(u.username, $userId), "
          + "u.email = coalesce(u.email, $userId)";

  private static final String CYPHER_DELETE =
      "MATCH (u:Usuario {id: $userId}) "
          + "SET u.pushEndpoint = null, u.pushP256dh = null, u.pushAuth = null";

  private final Driver driver;

  @Inject
  public Neo4jPushSubscriptionRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public PushSubscription findByUserId(String userId) {
    try (Session session = driver.session()) {
      Result result = session.run(CYPHER_READ, Values.parameters("userId", userId));
      if (!result.hasNext()) {
        return null;
      }
      var record = result.single();
      String endpoint =
          record.get("endpoint").isNull() ? null : record.get("endpoint").asString(null);
      if (endpoint == null) {
        return null;
      }
      String p256dh = record.get("p256dh").asString("");
      String auth = record.get("auth").asString("");
      if (p256dh.isEmpty() || auth.isEmpty()) {
        return null;
      }
      return new PushSubscription(endpoint, p256dh, auth);
    }
  }

  @Override
  public void save(String userId, PushSubscription subscription) {
    try (Session session = driver.session()) {
      session.run(
          CYPHER_SAVE,
          Values.parameters(
              "userId", userId,
              "endpoint", subscription.endpoint(),
              "p256dh", subscription.p256dh(),
              "auth", subscription.auth()));
    }
  }

  @Override
  public void deleteByUserId(String userId) {
    try (Session session = driver.session()) {
      session.run(CYPHER_DELETE, Values.parameters("userId", userId));
    }
  }

  @Override
  public long countActive() {
    try (Session session = driver.session()) {
      Result result =
          session.run(
              "MATCH (u:Usuario) WHERE u.pushEndpoint IS NOT NULL AND u.pushEndpoint <> '' RETURN"
                  + " count(u) AS active");
      if (result.hasNext()) {
        return result.single().get("active").asLong(0L);
      }
      return 0L;
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to count active push subscriptions, gauge returns 0", e);
      return 0L;
    }
  }
}
