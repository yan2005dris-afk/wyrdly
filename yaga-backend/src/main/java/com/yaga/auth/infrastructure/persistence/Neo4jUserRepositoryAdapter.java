package com.yaga.auth.infrastructure.persistence;

import com.yaga.auth.domain.model.User;
import com.yaga.auth.domain.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

@ApplicationScoped
public class Neo4jUserRepositoryAdapter implements UserRepository {

  private static final String USER_FIELDS =
      "u.id AS id, u.username AS username, u.email AS email, "
          + "u.passwordHash AS passwordHash, u.fullName AS fullName, "
          + "u.bio AS bio, u.avatarUrl AS avatarUrl, u.createdAt AS createdAt";

  private final Driver driver;

  @Inject
  public Neo4jUserRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public Optional<User> findById(String id) {
    String cypher = "MATCH (u:Usuario {id: $id}) RETURN " + USER_FIELDS;
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("id", id));
            if (result.hasNext()) {
              return Optional.of(mapRecordToUser(result.next()));
            }
            return Optional.empty();
          });
    }
  }

  @Override
  public Optional<User> findByUsername(String username) {
    String cypher = "MATCH (u:Usuario {username: $username}) RETURN " + USER_FIELDS;
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("username", username));
            if (result.hasNext()) {
              return Optional.of(mapRecordToUser(result.next()));
            }
            return Optional.empty();
          });
    }
  }

  @Override
  public Optional<User> findByEmail(String email) {
    String cypher = "MATCH (u:Usuario {email: $email}) RETURN " + USER_FIELDS;
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("email", email));
            if (result.hasNext()) {
              return Optional.of(mapRecordToUser(result.next()));
            }
            return Optional.empty();
          });
    }
  }

  @Override
  public Optional<User> findByUsernameOrEmail(String identifier) {
    String cypher =
        "MATCH (u:Usuario) WHERE u.username = $identifier OR u.email = $identifier "
            + "RETURN "
            + USER_FIELDS
            + " LIMIT 1";
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("identifier", identifier));
            if (result.hasNext()) {
              return Optional.of(mapRecordToUser(result.next()));
            }
            return Optional.empty();
          });
    }
  }

  @Override
  public boolean existsByUsername(String username) {
    String cypher = "MATCH (u:Usuario {username: $username}) RETURN count(u) > 0 AS exists";
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("username", username));
            if (result.hasNext()) {
              return result.next().get("exists").asBoolean(false);
            }
            return false;
          });
    }
  }

  @Override
  public boolean existsByEmail(String email) {
    String cypher = "MATCH (u:Usuario {email: $email}) RETURN count(u) > 0 AS exists";
    try (Session session = driver.session()) {
      return session.executeRead(
          tx -> {
            Result result = tx.run(cypher, Map.of("email", email));
            if (result.hasNext()) {
              return result.next().get("exists").asBoolean(false);
            }
            return false;
          });
    }
  }

  @Override
  public User save(User user) {
    String cypher =
        """
                MERGE (u:Usuario {id: $id})
                ON CREATE SET
                    u.username = $username,
                    u.email = $email,
                    u.passwordHash = $passwordHash,
                    u.fullName = $fullName,
                    u.bio = $bio,
                    u.avatarUrl = $avatarUrl,
                    u.createdAt = datetime($createdAt)
                ON MATCH SET
                    u.username = $username,
                    u.email = $email,
                    u.passwordHash = $passwordHash,
                    u.fullName = $fullName,
                    u.bio = $bio,
                    u.avatarUrl = $avatarUrl
                RETURN %s
                """
            .formatted(USER_FIELDS);

    Instant created = user.createdAt() != null ? user.createdAt() : Instant.now();
    Map<String, Object> params =
        Map.of(
            "id", user.id(),
            "username", user.username(),
            "email", user.email(),
            "passwordHash", user.passwordHash(),
            "fullName", user.fullName(),
            "bio", user.bio() != null ? user.bio() : "",
            "avatarUrl", user.avatarUrl() != null ? user.avatarUrl() : "",
            "createdAt", created.toString());

    try (Session session = driver.session()) {
      return session.executeWrite(
          tx -> {
            Result result = tx.run(cypher, params);
            if (result.hasNext()) {
              return mapRecordToUser(result.next());
            }
            throw new IllegalStateException("Failed to persist user in Neo4j: " + user.id());
          });
    }
  }

  private User mapRecordToUser(Record record) {
    String id = record.get("id").asString();
    String username = record.get("username").asString();
    String email = record.get("email").asString();
    String passwordHash = record.get("passwordHash").asString("");
    String fullName = record.get("fullName").asString("");
    String bio = record.get("bio").isNull() ? "" : record.get("bio").asString("");
    String avatarUrl = record.get("avatarUrl").isNull() ? "" : record.get("avatarUrl").asString("");

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

    return new User(id, username, email, passwordHash, fullName, bio, avatarUrl, createdAt);
  }
}
