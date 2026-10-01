package com.yaga.chat.infrastructure.persistence;

import com.yaga.chat.domain.model.DirectMessage;
import com.yaga.chat.domain.repository.DirectMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

@ApplicationScoped
public class Neo4jDirectMessageRepositoryAdapter implements DirectMessageRepository {

  private final Driver driver;

  @Inject
  public Neo4jDirectMessageRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public void save(DirectMessage message) {
    String cypher =
        """
        CREATE (msg:Mensaje {
          id: $id,
          senderId: $senderId,
          recipientId: $recipientId,
          content: $content,
          sentAt: $sentAt
        })
        WITH msg
        MERGE (sender:Usuario {id: $senderId})
        MERGE (recipient:Usuario {id: $recipientId})
        CREATE (sender)-[:SENT_MESSAGE]->(msg)-[:TO]->(recipient)
        RETURN msg.id AS id
        """;

    try (Session session = driver.session()) {
      session.executeWrite(
          tx -> {
            Result result =
                tx.run(
                    cypher,
                    Map.of(
                        "id", message.getId(),
                        "senderId", message.getSenderId(),
                        "recipientId", message.getRecipientId(),
                        "content", message.getContent(),
                        "sentAt", message.getSentAt().toString()));
            return result.single();
          });
    }
  }

  @Override
  public List<DirectMessage> findBetweenUsers(String userId1, String userId2, int skip, int limit) {
    String cypher =
        """
        MATCH (msg:Mensaje)
        WHERE (msg.senderId = $userId1 AND msg.recipientId = $userId2)
          OR (msg.senderId = $userId2 AND msg.recipientId = $userId1)
        ORDER BY msg.sentAt DESC
        RETURN msg
        SKIP $skip
        LIMIT $limit
        """;

    List<DirectMessage> messages = new ArrayList<>();
    try (Session session = driver.session()) {
      session.executeRead(
          tx -> {
            Result result =
                tx.run(
                    cypher,
                    Map.of(
                        "userId1", userId1,
                        "userId2", userId2,
                        "skip", (long) skip,
                        "limit", (long) limit));
            while (result.hasNext()) {
              Record record = result.next();
              messages.add(mapRecordToMessage(record.get("msg")));
            }
            return null;
          });
    }
    return messages;
  }

  @Override
  public long countBetweenUsers(String userId1, String userId2) {
    String cypher =
        """
        MATCH (msg:Mensaje)
        WHERE (msg.senderId = $userId1 AND msg.recipientId = $userId2)
          OR (msg.senderId = $userId2 AND msg.recipientId = $userId1)
        RETURN count(msg) AS total
        """;

    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("userId1", userId1, "userId2", userId2));
            if (result.hasNext()) {
              return result.next().get("total").asLong();
            }
            return 0L;
          });
    }
  }

  public ChatHistoryWithTotal findBetweenUsersWithTotal(
      String userId1, String userId2, int skip, int limit) {
    String cypher =
        """
        MATCH (msg:Mensaje)
        WHERE (msg.senderId = $userId1 AND msg.recipientId = $userId2)
          OR (msg.senderId = $userId2 AND msg.recipientId = $userId1)
        WITH msg, count(msg) OVER() AS total
        ORDER BY msg.sentAt DESC
        RETURN msg, total
        SKIP $skip
        LIMIT $limit
        """;

    List<DirectMessage> messages = new ArrayList<>();
    long total = 0;

    try (Session session = driver.session()) {
      session.executeRead(
          tx -> {
            Result result =
                tx.run(
                    cypher,
                    Map.of(
                        "userId1", userId1,
                        "userId2", userId2,
                        "skip", (long) skip,
                        "limit", (long) limit));
            while (result.hasNext()) {
              Record record = result.next();
              messages.add(mapRecordToMessage(record.get("msg")));
            }
            return null;
          });
    }

    try (Session session = driver.session()) {
      total =
          session.executeRead(
              tx -> {
                String countQuery =
                    """
                    MATCH (msg:Mensaje)
                    WHERE (msg.senderId = $userId1 AND msg.recipientId = $userId2)
                      OR (msg.senderId = $userId2 AND msg.recipientId = $userId1)
                    RETURN count(msg) AS total
                    """;
                Result result =
                    tx.run(countQuery, Map.of("userId1", userId1, "userId2", userId2));
                if (result.hasNext()) {
                  return result.next().get("total").asLong();
                }
                return 0L;
              });
    }

    return new ChatHistoryWithTotal(messages, total);
  }

  public static class ChatHistoryWithTotal {
    public List<DirectMessage> messages;
    public long total;

    public ChatHistoryWithTotal(List<DirectMessage> messages, long total) {
      this.messages = messages;
      this.total = total;
    }

    public List<DirectMessage> getMessages() {
      return messages;
    }

    public long getTotal() {
      return total;
    }
  }

  private DirectMessage mapRecordToMessage(org.neo4j.driver.Value value) {
    var node = value.asNode();
    return new DirectMessage(
        node.get("id").asString(),
        node.get("senderId").asString(),
        node.get("recipientId").asString(),
        node.get("content").asString(),
        Instant.parse(node.get("sentAt").asString()));
  }
}
