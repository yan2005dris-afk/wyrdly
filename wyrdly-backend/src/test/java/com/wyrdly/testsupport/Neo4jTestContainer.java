package com.wyrdly.testsupport;

import ac.simons.neo4j.migrations.core.Migrations;
import com.wyrdly.infrastructure.database.DatabaseMigrationService;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.neo4j.Neo4jContainer;

/**
 * One Neo4j 5.26 container per test JVM, shared by every integration test (singleton container
 * pattern): starting a container per test class was the bulk of the IT run time. The database is
 * migrated once with the production migrations, so tests exercise the real schema (constraints and
 * indexes) instead of an empty one. Testcontainers' Ryuk removes the container when the JVM exits.
 *
 * <p>Tests own their data, not the schema: call {@link #deleteAllData()} between tests.
 */
public final class Neo4jTestContainer {

  private static final Neo4jContainer CONTAINER =
      new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

  private static final Driver DRIVER;

  static {
    CONTAINER.start();
    DRIVER = GraphDatabase.driver(CONTAINER.getBoltUrl(), AuthTokens.none());
    new Migrations(DatabaseMigrationService.migrationsConfig(), DRIVER).apply();
    Runtime.getRuntime().addShutdownHook(new Thread(DRIVER::close));
  }

  private Neo4jTestContainer() {}

  public static Driver driver() {
    return DRIVER;
  }

  public static String boltUrl() {
    return CONTAINER.getBoltUrl();
  }

  /** Deletes every node except the migration bookkeeping, which belongs with the schema. */
  public static void deleteAllData() {
    try (Session session = DRIVER.session()) {
      session
          .run(
              "MATCH (n) WHERE NOT n:__Neo4jMigration AND NOT n:__Neo4jMigrationsLock "
                  + "DETACH DELETE n")
          .consume();
    }
  }
}
