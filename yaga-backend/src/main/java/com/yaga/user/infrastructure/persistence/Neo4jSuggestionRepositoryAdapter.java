package com.yaga.user.infrastructure.persistence;

import com.yaga.user.domain.model.GraphSuggestion;
import com.yaga.user.domain.repository.SuggestionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

@ApplicationScoped
public class Neo4jSuggestionRepositoryAdapter implements SuggestionRepository {

  private final Driver driver;

  @Inject
  public Neo4jSuggestionRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public List<GraphSuggestion> findSuggestions(String userId, int page, int pageSize) {
    String cypher =
        "MATCH (yo:Usuario {id: $userId})-[:SIGUE]->(amigo:Usuario)-[:SIGUE]->(sugerido:Usuario) "
            + "WHERE NOT (yo)-[:SIGUE]->(sugerido) AND sugerido <> yo "
            + "WITH sugerido, COUNT(DISTINCT amigo) AS mutualCount "
            + "ORDER BY mutualCount DESC "
            + "SKIP $skip LIMIT $limit "
            + "RETURN "
            + "sugerido.id AS id, "
            + "sugerido.username AS username, "
            + "sugerido.fullName AS fullName, "
            + "sugerido.avatarUrl AS avatarUrl, "
            + "mutualCount AS mutualConnectionsCount, "
            + "EXISTS { (yo:Usuario {id: $userId})-[:SIGUE]->(sugerido) } AS isFollowing";

    Map<String, Object> params = new HashMap<>();
    params.put("userId", userId);
    params.put("skip", Long.valueOf((long) page * pageSize));
    params.put("limit", Long.valueOf((long) pageSize));

    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, params);
            List<GraphSuggestion> suggestions = new ArrayList<>();
            while (result.hasNext()) {
              suggestions.add(mapRecordToSuggestion(result.next()));
            }
            return suggestions;
          });
    }
  }

  @Override
  public long countSuggestions(String userId) {
    String cypher =
        "MATCH (yo:Usuario {id: $userId})-[:SIGUE]->(amigo:Usuario)-[:SIGUE]->(sugerido:Usuario) "
            + "WHERE NOT (yo)-[:SIGUE]->(sugerido) AND sugerido <> yo "
            + "WITH DISTINCT sugerido "
            + "RETURN COUNT(sugerido) AS total";

    Map<String, Object> params = new HashMap<>();
    params.put("userId", userId);

    try (Session session = driver.session()) {
      Long count =
          session.executeRead(
              tx -> {
                Result result = tx.run(cypher, params);
                if (result.hasNext()) {
                  return result.next().get("total").asLong(0);
                }
                return 0L;
              });
      return count != null ? count : 0;
    }
  }

  private GraphSuggestion mapRecordToSuggestion(Record record) {
    String id = record.get("id").asString();
    String username = record.get("username").asString();
    String fullName = record.get("fullName").asString("");
    String avatarUrl = record.get("avatarUrl").isNull() ? "" : record.get("avatarUrl").asString("");
    long mutualConnectionsCount = record.get("mutualConnectionsCount").asLong(0);
    boolean isFollowing = record.get("isFollowing").asBoolean(false);

    return new GraphSuggestion(
        id, username, fullName, avatarUrl, mutualConnectionsCount, isFollowing);
  }
}
