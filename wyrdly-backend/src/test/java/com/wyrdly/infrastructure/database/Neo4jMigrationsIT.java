package com.wyrdly.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.simons.neo4j.migrations.core.MigrationState;
import ac.simons.neo4j.migrations.core.Migrations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

/**
 * Applies every migration in {@code classpath:neo4j/migrations} against a real Neo4j 5.26, the same
 * way {@link DatabaseMigrationService} does on startup, over data shaped like the legacy follow
 * writes: duplicated relationships and a mix of {@code fecha} (app) and {@code createdAt} (seed).
 *
 * <p>Uses its own container instead of the shared, already migrated {@code Neo4jTestContainer}:
 * migrations only run once, so testing them needs a database that has not seen them yet.
 */
@Testcontainers
class Neo4jMigrationsIT {

  @Container
  static final Neo4jContainer NEO4J_CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  static Driver driver;

  @BeforeAll
  static void migrateLegacyData() {
    driver = GraphDatabase.driver(NEO4J_CONTAINER.getBoltUrl(), AuthTokens.none());
    try (Session session = driver.session()) {
      session.run(
          "CREATE (alice:Usuario {id: 'usr_alice'}), (bob:Usuario {id: 'usr_bob'}),"
              + " (carol:Usuario {id: 'usr_carol'}),"
              // bob -> alice: seed-style original plus two duplicates from repeated follows
              + " (bob)-[:SIGUE {createdAt: datetime('2026-01-01T00:00:00Z')}]->(alice),"
              + " (bob)-[:SIGUE {fecha: datetime('2026-02-01T00:00:00Z')}]->(alice),"
              + " (bob)-[:SIGUE {fecha: datetime('2026-03-01T00:00:00Z')}]->(alice),"
              // carol -> alice: a single app-written follow
              + " (carol)-[:SIGUE {fecha: datetime('2026-04-01T00:00:00Z')}]->(alice)");
    }

    migrations().apply();
  }

  @AfterAll
  static void tearDownDriver() {
    driver.close();
  }

  private static Migrations migrations() {
    return new Migrations(DatabaseMigrationService.migrationsConfig(), driver);
  }

  @Test
  void allMigrationsAreApplied() {
    assertTrue(
        migrations().info().getElements().stream()
            .allMatch(e -> e.getState() == MigrationState.APPLIED));
  }

  @Test
  void duplicatedFollowsCollapseToTheOldestOne() {
    List<Record> rows = follows("usr_bob");

    assertEquals(1, rows.size());
    assertEquals(
        Instant.parse("2026-01-01T00:00:00Z"),
        rows.get(0).get("createdAt").asZonedDateTime().toInstant());
  }

  @Test
  void legacyFechaIsRenamedToCreatedAt() {
    List<Record> rows = follows("usr_carol");

    assertEquals(1, rows.size());
    assertEquals(List.of("createdAt"), rows.get(0).get("keys").asList(v -> v.asString()));
    assertEquals(
        Instant.parse("2026-04-01T00:00:00Z"),
        rows.get(0).get("createdAt").asZonedDateTime().toInstant());
  }

  @Test
  void noFollowKeepsTheLegacyProperty() {
    try (Session session = driver.session()) {
      long legacy =
          session
              .run("MATCH ()-[r:SIGUE]->() WHERE r.fecha IS NOT NULL RETURN count(r) AS c")
              .single()
              .get("c")
              .asLong();
      assertEquals(0L, legacy);
    }
  }

  private List<Record> follows(String followerId) {
    try (Session session = driver.session()) {
      return session
          .run(
              "MATCH (:Usuario {id: $followerId})-[r:SIGUE]->(:Usuario {id: 'usr_alice'}) "
                  + "RETURN keys(r) AS keys, r.createdAt AS createdAt",
              Map.of("followerId", followerId))
          .list();
    }
  }
}
