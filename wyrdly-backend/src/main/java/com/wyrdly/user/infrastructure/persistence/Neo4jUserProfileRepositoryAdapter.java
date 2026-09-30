package com.wyrdly.user.infrastructure.persistence;

import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.model.UserProfile;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

@ApplicationScoped
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
        "MATCH (follower:Usuario {id: $followerId}), (following:Usuario {id: $followingId}) "
            + "RETURN EXISTS { (follower)-[r:SIGUE]->(following) } AS following";

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
}
