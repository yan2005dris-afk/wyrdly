package com.yaga.post.infrastructure.persistence;

import com.yaga.post.domain.exception.PostValidationException;
import com.yaga.post.domain.model.Post;
import com.yaga.post.domain.repository.PostRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
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

  @Override
  public Post save(Post post) {
    try (Session session = driver.session()) {
      session.executeWrite(
          tx ->
              tx.run(
                      "MATCH (author:Usuario {id: $userId}) "
                          + "CREATE (p:Post {id: $id, content: $content, mediaUrl: $mediaUrl, createdAt: $createdAt}) "
                          + "CREATE (author)-[:PUBLICA {createdAt: $createdAt}]->(p)",
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
      throw new PostValidationException("Failed to persist post", e);
    }
  }

  @Override
  public Optional<Post> findById(String id) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx
                  .run(
                      "MATCH (p:Post {id: $id}) "
                          + "RETURN p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, p.createdAt AS createdAt, "
                          + "           'unknown' AS userId",
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
                              Instant.parse(record.get("createdAt").asString()))));
    } catch (Exception e) {
      Log.errorf(e, "Failed to find Post by id: %s", id);
      return Optional.empty();
    }
  }
}
