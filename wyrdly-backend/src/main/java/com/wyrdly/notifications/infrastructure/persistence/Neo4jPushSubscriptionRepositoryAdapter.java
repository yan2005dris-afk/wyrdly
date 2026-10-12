package com.wyrdly.notifications.infrastructure.persistence;

import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.domain.model.PushSubscription;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/**
 * Neo4j-backed implementation of {@link PushSubscriptionRepositoryPort}.
 *
 * <p>Each browser subscription is a {@code :SuscripcionPush} node linked from {@code :Usuario} via
 * {@code :TIENE_SUSCRIPCION}. Nodes are keyed by {@code endpoint} (one node per device, shared
 * when two users impossibly share an endpoint) and capped at {@link
 * PushSubscriptionRepositoryPort#MAX_SUBSCRIPTIONS_PER_USER} per user with oldest-first eviction.
 */
@ApplicationScoped
@Unremovable
public class Neo4jPushSubscriptionRepositoryAdapter implements PushSubscriptionRepositoryPort {

  private static final Logger LOG =
      Logger.getLogger(Neo4jPushSubscriptionRepositoryAdapter.class.getName());

  private static final String CYPHER_FIND_ONE =
      "MATCH (u:Usuario {id: $userId})-[:TIENE_SUSCRIPCION]->(s:SuscripcionPush) "
          + "RETURN s.endpoint AS endpoint, s.p256dh AS p256dh, s.auth AS auth "
          + "ORDER BY s.createdAt DESC LIMIT 1";

  private static final String CYPHER_FIND_ALL =
      "MATCH (u:Usuario {id: $userId})-[:TIENE_SUSCRIPCION]->(s:SuscripcionPush) "
          + "RETURN s.endpoint AS endpoint, s.p256dh AS p256dh, s.auth AS auth "
          + "ORDER BY s.createdAt DESC";

  private static final String CYPHER_SAVE =
      "MERGE (u:Usuario {id: $userId}) "
          + "ON CREATE SET u.username = coalesce(u.username, $userId), "
          + "u.email = coalesce(u.email, $userId) "
          + "MERGE (s:SuscripcionPush {endpoint: $endpoint}) "
          + "ON CREATE SET s.createdAt = datetime() "
          + "SET s.p256dh = $p256dh, s.auth = $auth "
          + "MERGE (u)-[:TIENE_SUSCRIPCION]->(s) "
          + "WITH u "
          + "MATCH (u)-[r:TIENE_SUSCRIPCION]->(s:SuscripcionPush) "
          + "WITH u, r, s ORDER BY s.createdAt ASC "
          + "WITH u, collect(r) AS rels "
          + "WHERE size(rels) > $maxSubs "
          + "UNWIND rels[0..(size(rels) - $maxSubs)] AS old "
          + "DELETE old";

  private static final String CYPHER_DELETE_ALL =
      "MATCH (u:Usuario {id: $userId})-[r:TIENE_SUSCRIPCION]->(s:SuscripcionPush) "
          + "DELETE r "
          + "WITH s WHERE NOT ()-[:TIENE_SUSCRIPCION]->(s) DETACH DELETE s";

  private static final String CYPHER_DELETE_ONE =
      "MATCH (u:Usuario {id: $userId})-[r:TIENE_SUSCRIPCION]->(s:SuscripcionPush {endpoint: $endpoint}) "
          + "DELETE r "
          + "WITH s WHERE NOT ()-[:TIENE_SUSCRIPCION]->(s) DETACH DELETE s";

  private final Driver driver;

  @Inject
  public Neo4jPushSubscriptionRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public PushSubscription findByUserId(String userId) {
    List<PushSubscription> all = findAllByUserId(userId);
    return all.isEmpty() ? null : all.get(0);
  }

  @Override
  public List<PushSubscription> findAllByUserId(String userId) {
    try (Session session = driver.session()) {
      return session
          .run(CYPHER_FIND_ALL, Values.parameters("userId", userId))
          .list(
              record -> {
                String endpoint =
                    record.get("endpoint").isNull() ? null : record.get("endpoint").asString(null);
                String p256dh =
                    record.get("p256dh").isNull() ? "" : record.get("p256dh").asString("");
                String auth = record.get("auth").isNull() ? "" : record.get("auth").asString("");
                if (endpoint == null || endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) {
                  return null;
                }
                return new PushSubscription(endpoint, p256dh, auth);
              })
          .stream()
          .filter(sub -> sub != null)
          .toList();
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
              "auth", subscription.auth(),
              "maxSubs", MAX_SUBSCRIPTIONS_PER_USER));
    }
  }

  @Override
  public void deleteByUserId(String userId) {
    try (Session session = driver.session()) {
      session.run(CYPHER_DELETE_ALL, Values.parameters("userId", userId));
    }
  }

  @Override
  public void deleteByEndpoint(String userId, String endpoint) {
    try (Session session = driver.session()) {
      session.run(
          CYPHER_DELETE_ONE, Values.parameters("userId", userId, "endpoint", endpoint));
    }
  }

  @Override
  public long countActive() {
    try (Session session = driver.session()) {
      var result =
          session.run(
              "MATCH (:Usuario)-[:TIENE_SUSCRIPCION]->(s:SuscripcionPush) RETURN count(s) AS active");
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
