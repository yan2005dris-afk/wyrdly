package com.wyrdly.post.infrastructure.persistence;

import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.PostRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

@ApplicationScoped
public class Neo4jPostRepositoryAdapter implements PostRepository {

  private final Driver driver;

  @Inject
  public Neo4jPostRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  /**
   * Idempotently persists a post and its {@code (:Usuario)-[:PUBLICA]->(:Post)} relationship.
   *
   * <p>{@code MERGE} on the post node plus the {@code post_id_unique} constraint (see {@code
   * V001__create_constraints_and_indexes.cypher}) make repeated calls with the same id safe — the
   * post is created once and the relationship is established once. The constraint additionally
   * guards against races by failing any non-MERGE write that would collide.
   */
  @Override
  public Post save(Post post) {
    try (Session session = driver.session()) {
      session.executeWrite(
          tx ->
              tx.run(
                      "MATCH (author:Usuario {id: $userId}) "
                          + "MERGE (p:Post {id: $id}) "
                          + "ON CREATE SET p.content = $content, p.mediaUrl = $mediaUrl, "
                          + "               p.createdAt = $createdAt "
                          + "MERGE (author)-[r:PUBLICA]->(p) "
                          + "ON CREATE SET r.createdAt = $createdAt",
                      Values.parameters(
                          "id", post.id(),
                          "userId", post.userId(),
                          "content", post.content(),
                          "mediaUrl", post.mediaUrl(),
                          "createdAt", post.createdAt().toString()))
                  .consume());

      Log.infof("Post saved to Neo4j: id=%s, userId=%s", post.id(), post.userId());
      return post;
    } catch (Exception e) {
      Log.errorf(e, "Failed to save Post to Neo4j: id=%s", post.id());
      throw new PostPersistenceException("Failed to persist post id=" + post.id(), e);
    }
  }

  /**
   * Looks up a post by id, traversing {@code (:Usuario)-[:PUBLICA]->(:Post)} to also return the
   * author id (previously a known bug where the query hardcoded {@code "unknown"}).
   *
   * <p>Returns {@link Optional#empty()} when no post matches. Throws {@link
   * PostPersistenceException} for any infrastructure-level failure so callers can distinguish "not
   * found" from "Neo4j unavailable".
   */
  @Override
  public Optional<Post> findById(String id) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx
                  .run(
                      "MATCH (author:Usuario)-[:PUBLICA]->(p:Post {id: $id}) "
                          + "RETURN p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
                          + "       p.createdAt AS createdAt, author.id AS userId",
                      Values.parameters("id", id))
                  .list()
                  .stream()
                  .findFirst()
                  .map(
                      record ->
                          new Post(
                              record.get("id").asString(),
                              record.get("userId").asString(),
                              record.get("content").asString(),
                              record.get("mediaUrl").isNull()
                                  ? null
                                  : record.get("mediaUrl").asString(),
                              readCreatedAt(record))));
    } catch (Exception e) {
      Log.errorf(e, "Failed to query Post by id: %s", id);
      throw new PostPersistenceException("Failed to query Post by id=" + id, e);
    }
  }

  @Override
  public List<Post> findByAuthor(String authorId, int page, int pageSize) {
    long skip = (long) page * pageSize;
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx
                  .run(
                      "MATCH (author:Usuario {id: $authorId})-[:PUBLICA]->(p:Post) "
                          + "RETURN p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
                          + "       p.createdAt AS createdAt, author.id AS userId "
                          + "ORDER BY p.createdAt DESC "
                          + "SKIP $skip LIMIT $limit",
                      Values.parameters(
                          "authorId", authorId,
                          "skip", skip,
                          "limit", (long) pageSize))
                  .list()
                  .stream()
                  .map(
                      record ->
                          new Post(
                              record.get("id").asString(),
                              record.get("userId").asString(),
                              record.get("content").asString(),
                              record.get("mediaUrl").isNull()
                                  ? null
                                  : record.get("mediaUrl").asString(),
                              readCreatedAt(record)))
                  .toList());
    } catch (Exception e) {
      Log.errorf(e, "Failed to query Posts by author: %s", authorId);
      throw new PostPersistenceException("Failed to query Posts by authorId=" + authorId, e);
    }
  }

  /**
   * Read the post createdAt field as a Java {@link Instant}. The seed.cypher uses {@code
   * datetime()} which Neo4j 5.x returns as a DATE_TIME value; older drivers or string-cast paths
   * may hand back a String instead. Try the native path first, fall back to ISO-8601 string
   * parsing.
   */
  private Instant readCreatedAt(org.neo4j.driver.Record record) {
    org.neo4j.driver.Value value = record.get("createdAt");
    if (value.isNull()) {
      return Instant.EPOCH;
    }
    try {
      return value.asZonedDateTime().toInstant();
    } catch (Exception e) {
      try {
        return Instant.parse(value.asString());
      } catch (Exception ex2) {
        Log.warnf("Could not parse Post createdAt; defaulting to EPOCH. value=%s", value);
        return Instant.EPOCH;
      }
    }
  }
}
