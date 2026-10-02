package com.wyrdly.post.infrastructure.persistence;

import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.PostRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
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
                          + "               p.createdAt = datetime($createdAt) "
                          + "MERGE (author)-[r:PUBLICA]->(p) "
                          + "ON CREATE SET r.createdAt = datetime($createdAt)",
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
  /**
   * Looks up a post by id, traversing {@code (:Usuario)-[:PUBLICA]->(:Post)} to also return the
   * author id.
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
              tx.run(
                      "MATCH (author:Usuario)-[:PUBLICA]->(p:Post {id: $id}) "
                          + "RETURN p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
                          + "       p.createdAt AS createdAt, author.id AS authorId",
                      Values.parameters("id", id))
                  .list()
                  .stream()
                  .findFirst()
                  .map(
                      record -> {
                        String postId = record.get("id").asString();
                        String content = record.get("content").asString();
                        String mediaUrl =
                            record.get("mediaUrl").isNull()
                                ? null
                                : record.get("mediaUrl").asString();
                        Instant createdAt =
                            Instant.parse(record.get("createdAt").asString());
                        String authorId = record.get("authorId").asString();

                        return new Post(postId, authorId, content, mediaUrl, createdAt);
                      }));

    } catch (Exception e) {
      Log.errorf(e, "Failed to query Post by id: %s", id);
      throw new PostPersistenceException("Failed to query Post by id=" + id, e);
    }
  }

  /**
   * Feed query combining: 1. Posts from users the caller follows 2. The caller's own posts
   *
   * <p>Counts reactions (LIKE, LOVE, CELEBRATE) and returns the caller's reaction type (if any).
   * Results ordered by createdAt DESC, with SKIP/LIMIT for pagination.
   */
  /**
   * Feed query combining:
   * 1. Posts from users the caller follows
   * 2. The caller's own posts
   *
   * <p>Counts reactions (LIKE, LOVE, CELEBRATE) and detects user's reaction type.
   * TODO: Extend to count both legacy (:LIKE/:LOVE/:CELEBRATE) and new (:REACCIONA {tipo}) relationships
   */
  private static final String FEED_QUERY =
      "MATCH (me:Usuario {id: $userId}) "
          + "CALL { "
          + "  WITH me "
          + "  MATCH (me)-[:SIGUE]->(author:Usuario)-[:PUBLICA]->(p:Post) "
          + "  RETURN p, author "
          + "  UNION ALL "
          + "  WITH me "
          + "  MATCH (me)-[:PUBLICA]->(p:Post) "
          + "  RETURN p, me AS author "
          + "} "
          + "OPTIONAL MATCH (p)<-[like:LIKE]-() "
          + "OPTIONAL MATCH (p)<-[love:LOVE]-() "
          + "OPTIONAL MATCH (p)<-[celebrate:CELEBRATE]-() "
          + "OPTIONAL MATCH (me)-[userReaction:LIKE|LOVE|CELEBRATE]->(p) "
          + "WITH DISTINCT p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
          + "              p.createdAt AS createdAt, author.id AS authorId, "
          + "              author.username AS authorUsername, author.fullName AS authorFullName, "
          + "              author.avatarUrl AS authorAvatarUrl, "
          + "              count(DISTINCT like) AS likeCount, "
          + "              count(DISTINCT love) AS loveCount, "
          + "              count(DISTINCT celebrate) AS celebrateCount, "
          + "              type(userReaction) AS userReactionType "
          + "ORDER BY createdAt DESC "
          + "SKIP $skip LIMIT $limit "
          + "RETURN id, content, mediaUrl, createdAt, authorId, authorUsername, "
          + "       authorFullName, authorAvatarUrl, likeCount, loveCount, "
          + "       celebrateCount, userReactionType";

  private static final String COUNT_FEED_QUERY =
      "MATCH (me:Usuario {id: $userId}) "
          + "CALL { "
          + "  WITH me "
          + "  MATCH (me)-[:SIGUE]->(author:Usuario)-[:PUBLICA]->(p:Post) "
          + "  RETURN p "
          + "  UNION ALL "
          + "  WITH me "
          + "  MATCH (me)-[:PUBLICA]->(p:Post) "
          + "  RETURN p "
          + "} "
          + "RETURN count(DISTINCT p) AS total";

  @Override
  public List<FeedPost> findFeedByUserId(String userId, int page, int pageSize) {
    int skip = Math.max(0, (page - 1) * pageSize);
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(
                      FEED_QUERY,
                      Values.parameters(
                          "userId", userId,
                          "skip", skip,
                          "limit", pageSize))
                  .list(this::mapRecordToFeedPost));
    } catch (Exception e) {
      Log.errorf(e, "Failed to query feed for userId: %s", userId);
      throw new PostPersistenceException("Failed to query feed for userId=" + userId, e);
    }
  }

  @Override
  public long countFeedByUserId(String userId) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            var result = tx.run(COUNT_FEED_QUERY, Values.parameters("userId", userId));
            if (result.hasNext()) {
              return result.next().get("total").asLong();
            }
            return 0L;
          });
    } catch (Exception e) {
      Log.errorf(e, "Failed to count feed for userId: %s", userId);
      throw new PostPersistenceException("Failed to count feed for userId=" + userId, e);
    }
  }

  private FeedPost mapRecordToFeedPost(Record record) {
    String id = record.get("id").asString();
    String content = record.get("content").asString();
    String mediaUrl = record.get("mediaUrl").isNull() ? null : record.get("mediaUrl").asString();
    Instant createdAt = Instant.parse(record.get("createdAt").asString());

    String authorId = record.get("authorId").asString();
    String authorUsername = record.get("authorUsername").asString();
    String authorFullName = record.get("authorFullName").asString();
    String authorAvatarUrl =
        record.get("authorAvatarUrl").isNull() ? null : record.get("authorAvatarUrl").asString();

    long likeCount = record.get("likeCount").asLong();
    long loveCount = record.get("loveCount").asLong();
    long celebrateCount = record.get("celebrateCount").asLong();
    String userReactionType =
        record.get("userReactionType").isNull() ? null : record.get("userReactionType").asString();

    Author author = new Author(authorId, authorUsername, authorFullName, authorAvatarUrl);
    return new FeedPost(
        id,
        content,
        mediaUrl,
        createdAt,
        author,
        likeCount,
        loveCount,
        celebrateCount,
        userReactionType);
  }

  /**
   * Finds all posts published by a specific author, with pagination.
   *
   * <p>Query: (:Usuario {id: authorId})-[:PUBLICA]->(:Post) traversal.
   * Returns basic post info without reaction counts (used for user profile page).
   */
  @Override
  public List<Post> findByAuthor(String authorId, int page, int pageSize) {
    int skip = Math.max(0, (page - 1) * pageSize);
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(
                      "MATCH (author:Usuario {id: $authorId})-[:PUBLICA]->(p:Post) "
                          + "RETURN p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
                          + "       p.createdAt AS createdAt, author.id AS authorId "
                          + "ORDER BY p.createdAt DESC "
                          + "SKIP $skip LIMIT $limit",
                      Values.parameters(
                          "authorId", authorId,
                          "skip", skip,
                          "limit", pageSize))
                  .list(this::mapRecordToPost));
    } catch (Exception e) {
      Log.errorf(e, "Failed to query posts by author: %s", authorId);
      throw new PostPersistenceException("Failed to query posts for authorId=" + authorId, e);
    }
  }

  /**
   * Counts total posts published by a specific author.
   */
  @Override
  public long countByAuthor(String authorId) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            var result =
                tx.run(
                    "MATCH (author:Usuario {id: $authorId})-[:PUBLICA]->(p:Post) "
                        + "RETURN count(DISTINCT p) AS total",
                    Values.parameters("authorId", authorId));
            if (result.hasNext()) {
              return result.next().get("total").asLong();
            }
            return 0L;
          });
    } catch (Exception e) {
      Log.errorf(e, "Failed to count posts by author: %s", authorId);
      throw new PostPersistenceException("Failed to count posts for authorId=" + authorId, e);
    }
  }

  /**
   * Maps a Neo4j record to a Post domain model.
   * Used by findByAuthor().
   *
   * <p>Expects: id, content, mediaUrl, createdAt, authorId fields.
   */
  private Post mapRecordToPost(Record record) {
    String id = record.get("id").asString();
    String content = record.get("content").asString();
    String mediaUrl = record.get("mediaUrl").isNull() ? null : record.get("mediaUrl").asString();
    Instant createdAt = Instant.parse(record.get("createdAt").asString());
    String authorId = record.get("authorId").asString();

    return new Post(id, authorId, content, mediaUrl, createdAt);
  }
}
