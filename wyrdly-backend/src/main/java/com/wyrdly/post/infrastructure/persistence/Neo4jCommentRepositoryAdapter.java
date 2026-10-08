package com.wyrdly.post.infrastructure.persistence;

import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.Comment;
import com.wyrdly.post.domain.repository.CommentRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/**
 * Neo4j-backed implementation of {@link CommentRepository}. Persists {@code (:Comentario)} nodes
 * linked by {@code (:Usuario)-[:ESCRIBE]->} and {@code -[:EN_POST]->(:Post)}.
 *
 * <p>The {@code findByPostId} and {@code findById} queries {@code OPTIONAL MATCH} the author node
 * so that comments whose author profile has been deleted still resolve — in that case {@link
 * Author} is materialised with the id and empty strings for the human-readable fields.
 */
@ApplicationScoped
public class Neo4jCommentRepositoryAdapter implements CommentRepository {

  private final Driver driver;

  @Inject
  public Neo4jCommentRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  private static final String SAVE_QUERY =
      "MATCH (u:Usuario {id: $authorId}) "
          + "MATCH (p:Post {id: $postId}) "
          + "CREATE (u)-[:ESCRIBE]->(c:Comentario { "
          + "  id: $id, "
          + "  postId: $postId, "
          + "  content: $content, "
          + "  createdAt: datetime($createdAt) "
          + "})-[:EN_POST]->(p) "
          + "RETURN c.id AS id";

  private static final String FIND_BY_POST_ID_QUERY =
      "MATCH (u:Usuario)-[:ESCRIBE]->(c:Comentario {postId: $postId})-[:EN_POST]->(p:Post) "
          + "RETURN c.id AS id, c.postId AS postId, c.content AS content, c.createdAt AS createdAt, "
          + "       u.id AS authorId, u.username AS authorUsername, "
          + "       u.fullName AS authorFullName, u.avatarUrl AS authorAvatarUrl "
          + "ORDER BY c.createdAt ASC "
          + "SKIP $skip LIMIT $limit";

  private static final String COUNT_BY_POST_ID_QUERY =
      "MATCH (c:Comentario {postId: $postId}) " + "RETURN count(c) AS total";

  private static final String FIND_BY_ID_QUERY =
      "MATCH (u:Usuario)-[:ESCRIBE]->(c:Comentario {id: $commentId}) "
          + "RETURN c.id AS id, c.postId AS postId, c.content AS content, c.createdAt AS createdAt, "
          + "       u.id AS authorId, u.username AS authorUsername, "
          + "       u.fullName AS authorFullName, u.avatarUrl AS authorAvatarUrl";

  private static final String DELETE_BY_ID_QUERY =
      "MATCH (c:Comentario {id: $commentId}) DETACH DELETE c";

  @Override
  public Comment save(Comment comment) {
    try (Session session = driver.session()) {
      session.executeWrite(
          tx ->
              tx.run(
                      SAVE_QUERY,
                      Values.parameters(
                          "id",
                          comment.id(),
                          "authorId",
                          comment.author().id(),
                          "postId",
                          comment.postId(),
                          "content",
                          comment.content(),
                          "createdAt",
                          comment.createdAt().toString()))
                  .consume());
      Log.infof(
          "Comment saved to Neo4j: id=%s, postId=%s, authorId=%s",
          comment.id(), comment.postId(), comment.author().id());
      return comment;
    } catch (Exception e) {
      Log.errorf(e, "Failed to save Comment to Neo4j: id=%s", comment.id());
      throw new PostPersistenceException("Failed to persist comment id=" + comment.id(), e);
    }
  }

  @Override
  public List<Comment> findByPostId(String postId, int page, int pageSize) {
    int skip = Math.max(0, (page - 1) * pageSize);
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(
                      FIND_BY_POST_ID_QUERY,
                      Values.parameters("postId", postId, "skip", skip, "limit", pageSize))
                  .list(this::mapRecordToComment));
    } catch (Exception e) {
      Log.errorf(e, "Failed to query comments by postId: %s", postId);
      throw new PostPersistenceException("Failed to query comments for postId=" + postId, e);
    }
  }

  @Override
  public long countByPostId(String postId) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            var result = tx.run(COUNT_BY_POST_ID_QUERY, Values.parameters("postId", postId));
            if (result.hasNext()) {
              return result.next().get("total").asLong();
            }
            return 0L;
          });
    } catch (Exception e) {
      Log.errorf(e, "Failed to count comments for postId: %s", postId);
      throw new PostPersistenceException("Failed to count comments for postId=" + postId, e);
    }
  }

  @Override
  public Optional<Comment> findById(String commentId) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(FIND_BY_ID_QUERY, Values.parameters("commentId", commentId)).list().stream()
                  .findFirst()
                  .map(this::mapRecordToComment));
    } catch (Exception e) {
      Log.errorf(e, "Failed to query Comment by id: %s", commentId);
      throw new PostPersistenceException("Failed to query Comment by id=" + commentId, e);
    }
  }

  @Override
  public void deleteById(String commentId) {
    try (Session session = driver.session()) {
      session.executeWrite(
          tx -> tx.run(DELETE_BY_ID_QUERY, Values.parameters("commentId", commentId)).consume());
      Log.infof("Comment deleted from Neo4j: id=%s", commentId);
    } catch (Exception e) {
      Log.errorf(e, "Failed to delete Comment from Neo4j: id=%s", commentId);
      throw new PostPersistenceException("Failed to delete comment id=" + commentId, e);
    }
  }

  private Comment mapRecordToComment(Record record) {
    String id = record.get("id").asString();
    String postId = record.get("postId").asString();
    String content = record.get("content").asString();
    Instant createdAt = parseInstant(record.get("createdAt"));

    String authorId = record.get("authorId").asString();
    String authorUsername =
        record.get("authorUsername").isNull() ? "" : record.get("authorUsername").asString();
    String authorFullName =
        record.get("authorFullName").isNull() ? "" : record.get("authorFullName").asString();
    String authorAvatarUrl =
        record.get("authorAvatarUrl").isNull() ? null : record.get("authorAvatarUrl").asString();

    Author author = new Author(authorId, authorUsername, authorFullName, authorAvatarUrl);
    return new Comment(id, postId, author, content, createdAt);
  }

  private static Instant parseInstant(org.neo4j.driver.Value val) {
    if (val == null || val.isNull()) {
      return Instant.now();
    }
    try {
      return val.asZonedDateTime().toInstant();
    } catch (Exception e) {
      try {
        return Instant.parse(val.asString());
      } catch (Exception ex) {
        return Instant.now();
      }
    }
  }
}
