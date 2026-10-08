package com.wyrdly.notifications.infrastructure.persistence;

import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/**
 * Neo4j-backed implementation of {@link NotificationRepository}.
 *
 * <p>Notifications are persisted as {@code :Notificacion} nodes. Lookups filter by {@code
 * recipientUserId} and order by {@code createdAt DESC} using the composite index added by migration
 * {@code V101__add_notification_indexes.cypher}.
 */
@ApplicationScoped
@Unremovable
public class Neo4jNotificationRepositoryAdapter implements NotificationRepository {

  private static final String CYPHER_SAVE =
      "MERGE (n:Notificacion {id: $id}) "
          + "SET n.recipientUserId = $recipientUserId, "
          + "    n.type = $type, "
          + "    n.actorId = $actorId, "
          + "    n.title = $title, "
          + "    n.body = $body, "
          + "    n.deepLink = $deepLink, "
          + "    n.targetResourceId = $targetResourceId, "
          + "    n.isRead = $isRead, "
          + "    n.createdAt = $createdAt";

  private static final String CYPHER_FIND_BY_RECIPIENT =
      "MATCH (n:Notificacion {recipientUserId: $recipientUserId}) "
          + "RETURN n "
          + "ORDER BY n.createdAt DESC "
          + "SKIP $skip LIMIT $limit";

  private static final String CYPHER_COUNT_UNREAD =
      "MATCH (n:Notificacion {recipientUserId: $recipientUserId, isRead: false}) "
          + "RETURN count(n) AS total";

  private static final String CYPHER_MARK_READ =
      "MATCH (n:Notificacion {id: $id, recipientUserId: $userId}) " + "SET n.isRead = true";

  private static final String CYPHER_MARK_ALL_READ =
      "MATCH (n:Notificacion {recipientUserId: $userId, isRead: false}) "
          + "SET n.isRead = true "
          + "RETURN count(n) AS updated";

  private final Driver driver;

  @Inject
  public Neo4jNotificationRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public void save(Notification notification) {
    try (Session session = driver.session()) {
      session.run(
          CYPHER_SAVE,
          Values.parameters(
              "id", notification.id(),
              "recipientUserId", notification.recipientUserId(),
              "type", notification.type(),
              "actorId", notification.actorId(),
              "title", notification.title(),
              "body", notification.body(),
              "deepLink", notification.deepLink(),
              "targetResourceId", notification.targetResourceId(),
              "isRead", notification.isRead(),
              "createdAt", notification.createdAt().toString()));
    }
  }

  @Override
  public List<Notification> findByRecipient(String recipientUserId, int page, int pageSize) {
    if (pageSize <= 0) {
      return List.of();
    }
    int skip = Math.max(0, page) * pageSize;
    try (Session session = driver.session()) {
      Result result =
          session.run(
              CYPHER_FIND_BY_RECIPIENT,
              Values.parameters(
                  "recipientUserId", recipientUserId, "skip", skip, "limit", pageSize));
      List<Notification> notifications = new ArrayList<>();
      while (result.hasNext()) {
        var record = result.next();
        var node = record.get("n").asNode();
        notifications.add(map(node));
      }
      return notifications;
    }
  }

  @Override
  public long countUnread(String recipientUserId) {
    try (Session session = driver.session()) {
      Result result =
          session.run(CYPHER_COUNT_UNREAD, Values.parameters("recipientUserId", recipientUserId));
      return result.single().get("total").asLong(0L);
    }
  }

  @Override
  public void markRead(String notificationId, String userId) {
    try (Session session = driver.session()) {
      session.run(CYPHER_MARK_READ, Values.parameters("id", notificationId, "userId", userId));
    }
  }

  @Override
  public long markAllRead(String userId) {
    try (Session session = driver.session()) {
      Result result = session.run(CYPHER_MARK_ALL_READ, Values.parameters("userId", userId));
      if (!result.hasNext()) {
        return 0L;
      }
      return result.single().get("updated").asLong(0L);
    }
  }

  private static Notification map(org.neo4j.driver.types.Node node) {
    return new Notification(
        node.get("id").asString(null),
        node.get("recipientUserId").asString(null),
        node.get("type").asString(null),
        node.get("actorId").asString(null),
        node.get("title").asString(null),
        node.get("body").asString(null),
        node.get("deepLink").asString(null),
        node.get("targetResourceId").isNull() ? null : node.get("targetResourceId").asString(null),
        node.get("isRead").asBoolean(false),
        Instant.parse(node.get("createdAt").asString(null)));
  }
}
