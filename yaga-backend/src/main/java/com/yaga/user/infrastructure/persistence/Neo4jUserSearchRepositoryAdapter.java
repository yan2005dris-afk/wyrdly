package com.yaga.user.infrastructure.persistence;

import com.yaga.user.application.dto.UserSearchResultDto;
import com.yaga.user.domain.repository.UserSearchRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;

@ApplicationScoped
public class Neo4jUserSearchRepositoryAdapter implements UserSearchRepository {

  private static final String SEARCH_CYPHER =
      "MATCH (u:Usuario) "
          + "WHERE (toLower(u.username) CONTAINS toLower($q) "
          + "    OR toLower(u.fullName) CONTAINS toLower($q) "
          + "    OR toLower(u.bio)     CONTAINS toLower($q)) "
          + "  AND u.id <> $viewerId "
          + "WITH u, "
          + "     COUNT { "
          + "       MATCH (me:Usuario {id: $viewerId})-[:SIGUE]->(mutual:Usuario)-[:SIGUE]->(u) "
          + "       WHERE mutual.id <> $viewerId AND mutual.id <> u.id "
          + "     } AS mutualCount "
          + "RETURN u.id        AS id, "
          + "       u.username  AS username, "
          + "       u.fullName  AS fullName, "
          + "       u.avatarUrl AS avatarUrl, "
          + "       u.bio       AS bio, "
          + "       EXISTS { MATCH (me:Usuario {id: $viewerId})-[:SIGUE]->(u) } AS isFollowing, "
          + "       mutualCount "
          + "ORDER BY isFollowing DESC, mutualCount DESC, u.username ASC "
          + "SKIP $skip LIMIT $limit";

  private static final String COUNT_CYPHER =
      "MATCH (u:Usuario) "
          + "WHERE (toLower(u.username) CONTAINS toLower($q) "
          + "    OR toLower(u.fullName) CONTAINS toLower($q) "
          + "    OR toLower(u.bio)     CONTAINS toLower($q)) "
          + "  AND u.id <> $viewerId "
          + "RETURN COUNT(u) AS total";

  private final Driver driver;

  @Inject
  public Neo4jUserSearchRepositoryAdapter(Driver driver) {
    this.driver = Objects.requireNonNull(driver, "driver must not be null");
  }

  @Override
  public List<UserSearchResultDto> findByText(
      String text, String viewerId, int page, int pageSize) {
    int skip = page * pageSize;
    Map<String, Object> params = baseParams(text, viewerId);
    params.put("skip", skip);
    params.put("limit", pageSize);

    try (Session session = driver.session()) {
      return session.executeRead(tx -> tx.run(SEARCH_CYPHER, params).list(this::mapRecordToResult));
    } catch (Exception e) {
      Log.errorf(e, "Failed to search users: text=%s, viewerId=%s", text, viewerId);
      throw new RuntimeException("No se pudo completar la búsqueda de usuarios.", e);
    }
  }

  @Override
  public int countByText(String text, String viewerId) {
    Map<String, Object> params = baseParams(text, viewerId);

    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            var result = tx.run(COUNT_CYPHER, params).list();
            if (result.isEmpty()) {
              return 0;
            }
            return result.get(0).get("total").asInt();
          });
    } catch (Exception e) {
      Log.errorf(e, "Failed to count search results: text=%s, viewerId=%s", text, viewerId);
      throw new RuntimeException("No se pudo contar los resultados de búsqueda.", e);
    }
  }

  private Map<String, Object> baseParams(String text, String viewerId) {
    Map<String, Object> params = new HashMap<>();
    params.put("q", text);
    params.put("viewerId", viewerId);
    return params;
  }

  private UserSearchResultDto mapRecordToResult(Record record) {
    int mutualCount = record.get("mutualCount").asInt();
    String snippet =
        mutualCount == 0
            ? null
            : mutualCount == 1 ? "1 amigo en común" : mutualCount + " amigos en común";

    return new UserSearchResultDto(
        record.get("id").asString(),
        record.get("username").asString(),
        record.get("fullName").asString(),
        record.get("avatarUrl").isNull() ? null : record.get("avatarUrl").asString(),
        record.get("bio").isNull() ? null : record.get("bio").asString(),
        record.get("isFollowing").asBoolean(),
        snippet);
  }
}
