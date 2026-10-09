package com.wyrdly.post.infrastructure.persistence;

import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;
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
              tx
                  .run(
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
                        Instant createdAt = parseInstant(record.get("createdAt"));
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
   * Feed query combining: 1. Posts from users the caller follows 2. The caller's own posts
   *
   * <p>Counts reactions via the unified {@code [:REACCIONA {tipo}]} relationship introduced in HU09
   * (post V100 migration; legacy {@code [:LIKE|:LOVE|:CELEBRATE]} relationships have been migrated
   * and are no longer present in the graph). Comments are aggregated with a {@code COUNT {}}
   * subquery (HU10) so that adding the {@code commentsCount} projection does NOT introduce a
   * cartesian explosion with the reaction OPTIONAL MATCH chains.
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
          + "OPTIONAL MATCH (p)<-[rlike:REACCIONA {tipo: 'LIKE'}]-() "
          + "OPTIONAL MATCH (p)<-[rlove:REACCIONA {tipo: 'LOVE'}]-() "
          + "OPTIONAL MATCH (p)<-[rceleb:REACCIONA {tipo: 'CELEBRATE'}]-() "
          + "OPTIONAL MATCH (me)-[ur:REACCIONA]->(p) "
          + "WITH DISTINCT p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
          + "              p.createdAt AS createdAt, author.id AS authorId, "
          + "              author.username AS authorUsername, author.fullName AS authorFullName, "
          + "              author.avatarUrl AS authorAvatarUrl, "
          + "              count(DISTINCT rlike) AS likeCount, "
          + "              count(DISTINCT rlove) AS loveCount, "
          + "              count(DISTINCT rceleb) AS celebrateCount, "
          + "              ur.tipo AS userReactionType, "
          + "              COUNT { (p)<-[:EN_POST]-(:Comentario) } AS commentsCount, "
          + "              COUNT { (p)<-[:COMPARTE]-() } AS repostsCount, "
          + "              EXISTS { (me)-[:COMPARTE]->(p) } AS userHasReposted "
          + "ORDER BY createdAt DESC "
          + "SKIP $skip LIMIT $limit "
          + "RETURN id, content, mediaUrl, createdAt, authorId, authorUsername, "
          + "       authorFullName, authorAvatarUrl, likeCount, loveCount, "
          + "       celebrateCount, commentsCount, repostsCount, userReactionType, userHasReposted";

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

  /**
   * Atomically toggles the {@code (:Usuario)-[:REACCIONA]->(:Post)} relationship for the supplied
   * {@code (userId, postId)} pair. Detects the existing state, then runs the matching {@code
   * FOREACH} branch to either remove, update, or create the relationship. Counts the resulting
   * total reactions for the post.
   *
   * <p>If the post does not exist (the {@code MATCH} for {@code (:Post {id: $postId})} returns 0
   * rows), this query returns no record and the caller surfaces a {@link PostNotFoundException}.
   */
  private static final String REACT_QUERY =
      "MATCH (u:Usuario {id: $userId}) "
          + "MATCH (p:Post {id: $postId}) "
          + "OPTIONAL MATCH (u)-[existing:REACCIONA]->(p) "
          + "WITH u, p, existing, "
          + "     CASE "
          + "       WHEN existing IS NULL              THEN 'ADDED' "
          + "       WHEN existing.tipo = $tipo         THEN 'REMOVED' "
          + "       ELSE                                    'UPDATED' "
          + "     END AS status "
          + "FOREACH (_ IN CASE WHEN status = 'REMOVED' THEN [1] ELSE [] END | "
          + "  DELETE existing "
          + ") "
          + "FOREACH (_ IN CASE WHEN status = 'UPDATED' THEN [1] ELSE [] END | "
          + "  SET existing.tipo = $tipo, existing.updatedAt = datetime() "
          + ") "
          + "FOREACH (_ IN CASE WHEN status = 'ADDED' THEN [1] ELSE [] END | "
          + "  CREATE (u)-[r:REACCIONA {tipo: $tipo, createdAt: datetime(), updatedAt: datetime()}]->(p) "
          + ") "
          + "WITH p, status "
          + "OPTIONAL MATCH (p)<-[allR:REACCIONA]-() "
          + "WITH p, status, count(allR) AS totalReactions "
          + "RETURN status, "
          + "       CASE WHEN status = 'REMOVED' THEN null ELSE $tipo END AS reactionType, "
          + "       totalReactions";

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
    Instant createdAt = parseInstant(record.get("createdAt"));

    String authorId = record.get("authorId").asString();
    String authorUsername = record.get("authorUsername").asString();
    String authorFullName = record.get("authorFullName").asString();
    String authorAvatarUrl =
        record.get("authorAvatarUrl").isNull() ? null : record.get("authorAvatarUrl").asString();

    long likeCount = record.get("likeCount").asLong();
    long loveCount = record.get("loveCount").asLong();
    long celebrateCount = record.get("celebrateCount").asLong();
    long commentsCount =
        record.containsKey("commentsCount") && !record.get("commentsCount").isNull()
            ? record.get("commentsCount").asLong()
            : 0L;
    long repostsCount =
        record.containsKey("repostsCount") && !record.get("repostsCount").isNull()
            ? record.get("repostsCount").asLong()
            : 0L;
    String userReactionType =
        record.get("userReactionType").isNull() ? null : record.get("userReactionType").asString();
    boolean userHasReposted =
        record.containsKey("userHasReposted")
            && !record.get("userHasReposted").isNull()
            && record.get("userHasReposted").asBoolean();

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
        commentsCount,
        repostsCount,
        userReactionType,
        userHasReposted);
  }

  private static final String FIND_POST_BY_ID_QUERY =
      "MATCH (author:Usuario)-[:PUBLICA]->(p:Post {id: $postId}) "
          + "OPTIONAL MATCH (p)<-[rlike:REACCIONA {tipo: 'LIKE'}]-() "
          + "OPTIONAL MATCH (p)<-[rlove:REACCIONA {tipo: 'LOVE'}]-() "
          + "OPTIONAL MATCH (p)<-[rceleb:REACCIONA {tipo: 'CELEBRATE'}]-() "
          + "OPTIONAL MATCH (me:Usuario {id: $userId})-[myR:REACCIONA]->(p) "
          + "WITH DISTINCT p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
          + "              p.createdAt AS createdAt, author.id AS authorId, "
          + "              author.username AS authorUsername, author.fullName AS authorFullName, "
          + "              author.avatarUrl AS authorAvatarUrl, "
          + "              count(DISTINCT rlike) AS likeCount, "
          + "              count(DISTINCT rlove) AS loveCount, "
          + "              count(DISTINCT rceleb) AS celebrateCount, "
          + "              myR.tipo AS userReactionType, "
          + "              COUNT { (p)<-[:EN_POST]-(:Comentario) } AS commentsCount, "
          + "              COUNT { (p)<-[:COMPARTE]-() } AS repostsCount, "
          + "              ($userId IS NOT NULL AND EXISTS { (:Usuario {id: $userId})-[:COMPARTE]->(p) }) AS userHasReposted "
          + "RETURN id, content, mediaUrl, createdAt, authorId, authorUsername, "
          + "       authorFullName, authorAvatarUrl, likeCount, loveCount, "
          + "       celebrateCount, commentsCount, repostsCount, userReactionType, userHasReposted";

  @Override
  public Optional<FeedPost> findFeedPostById(String postId, String userId) {
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx
                  .run(FIND_POST_BY_ID_QUERY, Values.parameters("postId", postId, "userId", userId))
                  .list(this::mapRecordToFeedPost)
                  .stream()
                  .findFirst());
    } catch (Exception e) {
      Log.errorf(e, "Failed to query Post by id: %s", postId);
      throw new PostPersistenceException("Failed to query Post by id=" + postId, e);
    }
  }

  private static final String FIND_BY_AUTHOR_QUERY =
      "MATCH (author:Usuario {id: $authorId})-[:PUBLICA]->(p:Post) "
          + "OPTIONAL MATCH (p)<-[rlike:REACCIONA {tipo: 'LIKE'}]-() "
          + "OPTIONAL MATCH (p)<-[rlove:REACCIONA {tipo: 'LOVE'}]-() "
          + "OPTIONAL MATCH (p)<-[rceleb:REACCIONA {tipo: 'CELEBRATE'}]-() "
          + "OPTIONAL MATCH (viewer:Usuario {id: $viewerId})-[ur:REACCIONA]->(p) "
          + "WITH DISTINCT p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, "
          + "              p.createdAt AS createdAt, author.id AS authorId, "
          + "              author.username AS authorUsername, author.fullName AS authorFullName, "
          + "              author.avatarUrl AS authorAvatarUrl, "
          + "              count(DISTINCT rlike) AS likeCount, "
          + "              count(DISTINCT rlove) AS loveCount, "
          + "              count(DISTINCT rceleb) AS celebrateCount, "
          + "              ur.tipo AS userReactionType, "
          + "              COUNT { (p)<-[:EN_POST]-(:Comentario) } AS commentsCount, "
          + "              COUNT { (p)<-[:COMPARTE]-() } AS repostsCount, "
          + "              ($viewerId IS NOT NULL AND EXISTS { (:Usuario {id: $viewerId})-[:COMPARTE]->(p) }) AS userHasReposted "
          + "ORDER BY createdAt DESC "
          + "SKIP $skip LIMIT $limit "
          + "RETURN id, content, mediaUrl, createdAt, authorId, authorUsername, "
          + "       authorFullName, authorAvatarUrl, likeCount, loveCount, "
          + "       celebrateCount, commentsCount, repostsCount, userReactionType, userHasReposted";

  /**
   * Finds all posts published by a specific author, with pagination, reactions, and comments count.
   */
  @Override
  public List<FeedPost> findByAuthor(String authorId, String viewerId, int page, int pageSize) {
    int skip = Math.max(0, (page <= 0 ? 0 : page - 1) * pageSize);
    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(
                      FIND_BY_AUTHOR_QUERY,
                      Values.parameters(
                          "authorId", authorId,
                          "viewerId", viewerId,
                          "skip", skip,
                          "limit", pageSize))
                  .list(this::mapRecordToFeedPost));
    } catch (Exception e) {
      Log.errorf(e, "Failed to query posts by author: %s", authorId);
      throw new PostPersistenceException("Failed to query posts for authorId=" + authorId, e);
    }
  }

  @Override
  public List<FeedPost> findByAuthor(String authorId, int page, int pageSize) {
    return findByAuthor(authorId, null, page, pageSize);
  }

  /** Counts total posts published by a specific author. */
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
   * Maps a Neo4j record to a Post domain model. Used by findByAuthor().
   *
   * <p>Expects: id, content, mediaUrl, createdAt, authorId fields.
   */
  private Post mapRecordToPost(Record record) {
    String id = record.get("id").asString();
    String content = record.get("content").asString();
    String mediaUrl = record.get("mediaUrl").isNull() ? null : record.get("mediaUrl").asString();
    Instant createdAt = parseInstant(record.get("createdAt"));
    String authorId = record.get("authorId").asString();

    return new Post(id, authorId, content, mediaUrl, createdAt);
  }

  /**
   * Atomically toggles a user's reaction on a post in a single write transaction.
   *
   * <p>If the post does not exist the underlying {@code REACT_QUERY} matches zero rows, the
   * returned record stream is empty, and a {@link PostNotFoundException} is raised. Any other
   * driver-level failure is wrapped in {@link PostPersistenceException} so the caller can
   * distinguish a not-found condition from a transient Neo4j problem.
   */
  @Override
  public ReactionResult react(String userId, String postId, ReactionType type) {
    try (Session session = driver.session()) {
      Optional<Record> result =
          session.executeWrite(
              tx -> {
                var run =
                    tx.run(
                        REACT_QUERY,
                        Values.parameters("userId", userId, "postId", postId, "tipo", type.name()));
                if (run.hasNext()) {
                  return Optional.of(run.next());
                }
                return Optional.<Record>empty();
              });

      if (result.isEmpty()) {
        throw new PostNotFoundException(postId);
      }

      Record record = result.get();
      String status = record.get("status").asString();
      ReactionStatus reactionStatus = ReactionStatus.valueOf(status);
      String reactionTypeStr =
          record.get("reactionType").isNull() ? null : record.get("reactionType").asString();
      ReactionType reactionType =
          reactionTypeStr == null ? null : ReactionType.valueOf(reactionTypeStr);
      long total = record.get("totalReactions").asLong();

      return new ReactionResult(postId, reactionStatus, reactionType, total);
    } catch (PostNotFoundException e) {
      throw e;
    } catch (Exception e) {
      Log.errorf(e, "Failed to react to post: userId=%s postId=%s", userId, postId);
      throw new PostPersistenceException("Failed to react to post=" + postId, e);
    }
  }

  private static Instant parseInstant(Value val) {
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
