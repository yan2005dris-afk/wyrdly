package com.wyrdly.post.infrastructure.persistence;

import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.model.RepostResult;
import com.wyrdly.post.domain.repository.RepostRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/**
 * Neo4j implementation of {@link RepostRepository}.
 *
 * <p>Uses atomic write transactions with {@code MERGE} / {@code DELETE} on {@code
 * (:Usuario)-[:COMPARTE]->(:Post)} relationships.
 */
@ApplicationScoped
public class Neo4jRepostRepositoryAdapter implements RepostRepository {

  private static final String REPOST_QUERY =
      "MATCH (u:Usuario {id: $userId}) "
          + "MATCH (a:Usuario)-[:PUBLICA]->(p:Post {id: $postId}) "
          + "MERGE (u)-[r:COMPARTE]->(p) "
          + "ON CREATE SET r.createdAt = datetime(), r.requestId = $requestId "
          + "WITH a, p, r, (r.requestId = $requestId) AS changed "
          + "RETURN changed, "
          + "       COUNT { (p)<-[:COMPARTE]-() } AS repostsCount, "
          + "       a.id AS authorId";

  private static final String UNREPOST_QUERY =
      "MATCH (a:Usuario)-[:PUBLICA]->(p:Post {id: $postId}) "
          + "OPTIONAL MATCH (u:Usuario {id: $userId})-[r:COMPARTE]->(p) "
          + "WITH a, p, r, (r IS NOT NULL) AS changed "
          + "DELETE r "
          + "WITH a, p, changed "
          + "RETURN changed, "
          + "       COUNT { (p)<-[:COMPARTE]-() } AS repostsCount, "
          + "       a.id AS authorId";

  private final Driver driver;

  @Inject
  public Neo4jRepostRepositoryAdapter(Driver driver) {
    this.driver = Objects.requireNonNull(driver, "driver must not be null");
  }

  @Override
  public RepostResult repost(String userId, String postId, String requestId) {
    try (Session session = driver.session()) {
      Optional<Record> result =
          session.executeWrite(
              tx -> {
                var run =
                    tx.run(
                        REPOST_QUERY,
                        Values.parameters(
                            "userId", userId,
                            "postId", postId,
                            "requestId", requestId));
                if (run.hasNext()) {
                  return Optional.of(run.next());
                }
                return Optional.empty();
              });

      if (result.isEmpty()) {
        throw new PostNotFoundException(postId);
      }

      Record record = result.get();
      boolean changed = record.get("changed").asBoolean();
      long repostsCount = record.get("repostsCount").asLong();
      String authorId = record.get("authorId").asString();

      return new RepostResult(postId, true, changed, repostsCount, authorId);
    } catch (PostNotFoundException e) {
      throw e;
    } catch (Exception e) {
      Log.errorf(e, "Failed to repost: userId=%s postId=%s", userId, postId);
      throw new PostPersistenceException("Failed to repost post=" + postId, e);
    }
  }

  @Override
  public RepostResult unrepost(String userId, String postId) {
    try (Session session = driver.session()) {
      Optional<Record> result =
          session.executeWrite(
              tx -> {
                var run =
                    tx.run(
                        UNREPOST_QUERY,
                        Values.parameters(
                            "userId", userId,
                            "postId", postId));
                if (run.hasNext()) {
                  return Optional.of(run.next());
                }
                return Optional.empty();
              });

      if (result.isEmpty()) {
        throw new PostNotFoundException(postId);
      }

      Record record = result.get();
      boolean changed = record.get("changed").asBoolean();
      long repostsCount = record.get("repostsCount").asLong();
      String authorId = record.get("authorId").asString();

      return new RepostResult(postId, false, changed, repostsCount, authorId);
    } catch (PostNotFoundException e) {
      throw e;
    } catch (Exception e) {
      Log.errorf(e, "Failed to unrepost: userId=%s postId=%s", userId, postId);
      throw new PostPersistenceException("Failed to unrepost post=" + postId, e);
    }
  }
}
