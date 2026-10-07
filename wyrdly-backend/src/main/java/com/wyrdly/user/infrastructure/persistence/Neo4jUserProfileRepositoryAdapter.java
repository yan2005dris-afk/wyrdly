package com.wyrdly.user.infrastructure.persistence;

import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.model.UserProfile;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.infrastructure.qualifier.Neo4jDirect;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

@ApplicationScoped
@Neo4jDirect
public class Neo4jUserProfileRepositoryAdapter implements UserProfileRepository {

  private static final String PROFILE_COUNTS_RETURN =
      "u.id AS id, u.username AS username, u.fullName AS fullName, u.bio AS bio, "
          + "u.avatarUrl AS avatarUrl, u.createdAt AS createdAt, "
          + "COUNT { (u)<-[:SIGUE]-() } AS followersCount, "
          + "COUNT { (u)-[:SIGUE]->() } AS followingCount, "
          + "COUNT { (u)-[:PUBLICA]->() } AS postsCount";

  private final Driver driver;

  @Inject
  public Neo4jUserProfileRepositoryAdapter(Driver driver) {
    this.driver = Objects.requireNonNull(driver, "driver must not be null");
  }

  @Override
  public Optional<UserProfile> findProfileByUsername(String username, String viewerId) {
    String cypher =
        "MATCH (u:Usuario {username: $username}) "
            + "RETURN "
            + PROFILE_COUNTS_RETURN
            + ", "
            + "($viewerId IS NOT NULL AND EXISTS { "
            + "    MATCH (viewer:Usuario {id: $viewerId})-[:SIGUE]->(u) "
            + "}) AS isFollowing";

    Map<String, Object> params = new HashMap<>();
    params.put("username", username);
    params.put("viewerId", viewerId);

    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, params);
            if (result.hasNext()) {
              return Optional.of(mapRecordToProfile(result.next(), true));
            }
            return Optional.empty();
          });
    }
  }

  @Override
  public Optional<UserProfile> updateProfile(
      String userId, String fullName, String bio, String avatarUrl) {
    String cypher =
        "MATCH (u:Usuario {id: $userId}) "
            + "SET u.fullName = coalesce($fullName, u.fullName), "
            + "    u.bio = coalesce($bio, u.bio), "
            + "    u.avatarUrl = coalesce($avatarUrl, u.avatarUrl) "
            + "RETURN "
            + PROFILE_COUNTS_RETURN;

    Map<String, Object> params = new HashMap<>();
    params.put("userId", userId);
    params.put("fullName", fullName);
    params.put("bio", bio);
    params.put("avatarUrl", avatarUrl);

    try (Session session = driver.session()) {
      return session.executeWrite(
          tx -> {
            Result result = tx.run(cypher, params);
            if (result.hasNext()) {
              return Optional.of(mapRecordToProfile(result.next(), false));
            }
            return Optional.empty();
          });
    }
  }

  @Override
  public void followUser(String followerId, String followingId) {
    String cypher =
        "MATCH (follower:Usuario {id: $followerId}), (following:Usuario {id: $followingId}) "
            + "MERGE (follower)-[r:SIGUE {fecha: datetime()}]->(following)";

    Map<String, Object> params = new HashMap<>();
    params.put("followerId", followerId);
    params.put("followingId", followingId);

    try (Session session = driver.session()) {
      session.executeWrite(
          tx -> {
            tx.run(cypher, params).consume();
            return null;
          });
    }
  }

  @Override
  public void unfollowUser(String followerId, String followingId) {
    String cypher =
        "MATCH (follower:Usuario {id: $followerId})-[r:SIGUE]->(following:Usuario {id: $followingId}) "
            + "DELETE r";

    Map<String, Object> params = new HashMap<>();
    params.put("followerId", followerId);
    params.put("followingId", followingId);

    try (Session session = driver.session()) {
      session.executeWrite(
          tx -> {
            tx.run(cypher, params).consume();
            return null;
          });
    }
  }

  @Override
  public boolean isFollowing(String followerId, String followingId) {
    String cypher =
        "OPTIONAL MATCH (follower:Usuario {id: $followerId})-[r:SIGUE]->(following:Usuario {id: $followingId}) "
            + "RETURN r IS NOT NULL AS following";

    Map<String, Object> params = new HashMap<>();
    params.put("followerId", followerId);
    params.put("followingId", followingId);

    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, params);
            if (result.hasNext()) {
              return result.next().get("following").asBoolean(false);
            }
            return false;
          });
    }
  }

  @Override
  public void validateUserExists(String userId) {
    String cypher = "MATCH (u:Usuario {id: $userId}) RETURN COUNT(u) > 0 AS exists";

    Map<String, Object> params = new HashMap<>();
    params.put("userId", userId);

    try (Session session = driver.session()) {
      boolean exists =
          session.executeRead(
              tx -> {
                Result result = tx.run(cypher, params);
                if (result.hasNext()) {
                  return result.next().get("exists").asBoolean(false);
                }
                return false;
              });

      if (!exists) {
        throw new UserProfileNotFoundException("El usuario '" + userId + "' no existe.");
      }
    }
  }

  private UserProfile mapRecordToProfile(Record record, boolean includeIsFollowing) {
    String id = record.get("id").asString();
    String username = record.get("username").asString();
    String fullName = record.get("fullName").asString("");
    String bio = record.get("bio").isNull() ? "" : record.get("bio").asString("");
    String avatarUrl = record.get("avatarUrl").isNull() ? "" : record.get("avatarUrl").asString("");
    long followersCount = record.get("followersCount").asLong(0);
    long followingCount = record.get("followingCount").asLong(0);
    long postsCount = record.get("postsCount").asLong(0);
    boolean isFollowing = includeIsFollowing && record.get("isFollowing").asBoolean(false);

    Instant createdAt;
    Value createdVal = record.get("createdAt");
    if (createdVal.isNull()) {
      createdAt = Instant.now();
    } else {
      try {
        createdAt = createdVal.asZonedDateTime().toInstant();
      } catch (Exception e) {
        try {
          createdAt = Instant.parse(createdVal.asString());
        } catch (Exception ex2) {
          createdAt = Instant.now();
        }
      }
    }

    return new UserProfile(
        id,
        username,
        fullName,
        bio,
        avatarUrl,
        followersCount,
        followingCount,
        postsCount,
        isFollowing,
        createdAt);
  }

  @Override
  public List<FollowerSummary> findFollowers(
      String userId, String viewerId, int page, int pageSize) {
    long skip = (long) page * pageSize;
    String cypher =
        "MATCH (target:Usuario {id: $userId})<-[:SIGUE]-(follower:Usuario) "
            + "OPTIONAL MATCH (viewer:Usuario {id: $viewerId})-[:SIGUE]->(follower) "
            + "RETURN follower.id AS id, follower.username AS username, "
            + "       follower.fullName AS fullName, follower.avatarUrl AS avatarUrl, "
            + "       viewer IS NOT NULL AS isFollowing "
            + "ORDER BY follower.username ASC "
            + "SKIP $skip LIMIT $limit";

    Map<String, Object> params = new HashMap<>();
    params.put("userId", userId);
    params.put("viewerId", viewerId);
    params.put("skip", skip);
    params.put("limit", (long) pageSize);

    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(cypher, params).list().stream()
                  .map(
                      record ->
                          new UserProfileRepository.FollowerSummary(
                              record.get("id").asString(),
                              record.get("username").asString(),
                              record.get("fullName").asString(""),
                              record.get("avatarUrl").isNull()
                                  ? ""
                                  : record.get("avatarUrl").asString(""),
                              record.get("isFollowing").asBoolean(false)))
                  .toList());
    } catch (Exception e) {
      Log.errorf(e, "Failed to query followers for userId=%s", userId);
      throw new RuntimeException("Failed to query followers", e);
    }
  }

  @Override
  public List<UserProfileRepository.FollowerSummary> findFollowing(
      String userId, String viewerId, int page, int pageSize) {
    long skip = (long) page * pageSize;
    String cypher =
        "MATCH (target:Usuario {id: $userId})-[:SIGUE]->(followed:Usuario) "
            + "OPTIONAL MATCH (viewer:Usuario {id: $viewerId})-[:SIGUE]->(followed) "
            + "RETURN followed.id AS id, followed.username AS username, "
            + "       followed.fullName AS fullName, followed.avatarUrl AS avatarUrl, "
            + "       viewer IS NOT NULL AS isFollowing "
            + "ORDER BY followed.username ASC "
            + "SKIP $skip LIMIT $limit";

    Map<String, Object> params = new HashMap<>();
    params.put("userId", userId);
    params.put("viewerId", viewerId);
    params.put("skip", skip);
    params.put("limit", (long) pageSize);

    try (Session session = driver.session()) {
      return session.executeRead(
          tx ->
              tx.run(cypher, params).list().stream()
                  .map(
                      record ->
                          new UserProfileRepository.FollowerSummary(
                              record.get("id").asString(),
                              record.get("username").asString(),
                              record.get("fullName").asString(""),
                              record.get("avatarUrl").isNull()
                                  ? ""
                                  : record.get("avatarUrl").asString(""),
                              record.get("isFollowing").asBoolean(false)))
                  .toList());
    } catch (Exception e) {
      Log.errorf(e, "Failed to query following for userId=%s", userId);
      throw new RuntimeException("Failed to query following", e);
    }
  }

  @Override
  public Map<String, UserProfileRepository.FollowerSummary> findProfileSummariesByIds(
      Set<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    String cypher =
        "MATCH (u:Usuario) WHERE u.id IN $ids "
            + "RETURN u.id AS id, u.username AS username, u.fullName AS fullName, "
            + "       u.avatarUrl AS avatarUrl, false AS isFollowing";

    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("ids", userIds));
            Map<String, UserProfileRepository.FollowerSummary> out = new HashMap<>();
            while (result.hasNext()) {
              var record = result.next();
              String id = record.get("id").asString();
              out.put(
                  id,
                  new UserProfileRepository.FollowerSummary(
                      id,
                      record.get("username").asString(""),
                      record.get("fullName").asString(""),
                      record.get("avatarUrl").isNull() ? "" : record.get("avatarUrl").asString(""),
                      false));
            }
            return out;
          });
    } catch (Exception e) {
      Log.errorf(e, "Failed to query profile summaries for ids=%s", userIds);
      throw new RuntimeException("Failed to query profile summaries", e);
    }
  }
}
