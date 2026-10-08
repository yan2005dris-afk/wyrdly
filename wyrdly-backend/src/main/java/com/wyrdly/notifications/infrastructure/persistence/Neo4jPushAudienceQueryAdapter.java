package com.wyrdly.notifications.infrastructure.persistence;

import com.wyrdly.notifications.application.port.PushAudienceQueryPort;
import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.domain.model.PushTarget;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/**
 * Neo4j-backed implementation of {@link PushAudienceQueryPort}.
 *
 * <p>One query per batch returns recipients together with their subscription triple, so the
 * dispatcher never re-reads {@code :Usuario} per recipient. The traversal starts from the author
 * (unique index on {@code id}) and only expands its incoming {@code :SIGUE} relationships. Keyset
 * pagination on {@code f.id} keeps every batch O(batch) instead of the O(n²) of {@code SKIP}.
 */
@ApplicationScoped
@Unremovable
public class Neo4jPushAudienceQueryAdapter implements PushAudienceQueryPort {

  static final String CYPHER_SUBSCRIBED_FOLLOWERS =
      "MATCH (author:Usuario {id: $authorId})<-[:SIGUE]-(f:Usuario) "
          + "WHERE f.id > $afterUserId AND f <> author "
          + "AND f.pushEndpoint IS NOT NULL AND f.pushP256dh IS NOT NULL AND f.pushAuth IS NOT NULL "
          + "AND f.pushEndpoint <> '' AND f.pushP256dh <> '' AND f.pushAuth <> '' "
          + "RETURN f.id AS userId, f.pushEndpoint AS endpoint, "
          + "f.pushP256dh AS p256dh, f.pushAuth AS auth "
          + "ORDER BY f.id "
          + "LIMIT $limit";

  static final String CYPHER_ALL_FOLLOWERS =
      "MATCH (author:Usuario {id: $authorId})<-[:SIGUE]-(f:Usuario) "
          + "WHERE f.id > $afterUserId AND f <> author "
          + "RETURN f.id AS userId, f.pushEndpoint AS endpoint, "
          + "f.pushP256dh AS p256dh, f.pushAuth AS auth "
          + "ORDER BY f.id "
          + "LIMIT $limit";

  private final Driver driver;

  @Inject
  public Neo4jPushAudienceQueryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public List<PushTarget> findSubscribedFollowers(String authorId, String afterUserId, int limit) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(
                      CYPHER_SUBSCRIBED_FOLLOWERS,
                      Values.parameters(
                          "authorId",
                          authorId,
                          "afterUserId",
                          afterUserId == null ? "" : afterUserId,
                          "limit",
                          limit))
                  .list(
                      record ->
                          new PushTarget(
                              record.get("userId").asString(),
                              new PushSubscription(
                                  record.get("endpoint").asString(),
                                  record.get("p256dh").asString(),
                                  record.get("auth").asString()))));
    }
  }

  @Override
  public List<PushTarget> findAllFollowers(String authorId, String afterUserId, int limit) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(
                      CYPHER_ALL_FOLLOWERS,
                      Values.parameters(
                          "authorId",
                          authorId,
                          "afterUserId",
                          afterUserId == null ? "" : afterUserId,
                          "limit",
                          limit))
                  .list(
                      record -> {
                        String userId = record.get("userId").asString();
                        var epVal = record.get("endpoint");
                        var p256Val = record.get("p256dh");
                        var authVal = record.get("auth");
                        PushSubscription sub = null;
                        if (!epVal.isNull() && !p256Val.isNull() && !authVal.isNull()) {
                          String ep = epVal.asString();
                          String p256 = p256Val.asString();
                          String auth = authVal.asString();
                          if (!ep.isBlank() && !p256.isBlank() && !auth.isBlank()) {
                            sub = new PushSubscription(ep, p256, auth);
                          }
                        }
                        return new PushTarget(userId, sub);
                      }));
    }
  }
}
