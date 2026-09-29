package com.yaga.infrastructure.database;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

@ApplicationScoped
public class DatabaseSeeder {

  private static final Logger LOG = Logger.getLogger(DatabaseSeeder.class);

  @Inject Driver driver;

  @ConfigProperty(name = "yaga.database.seed.enabled", defaultValue = "false")
  boolean seedEnabled;

  void onStart(@Observes StartupEvent ev) {
    if (!seedEnabled) {
      LOG.info("DatabaseSeeder: Seeding is disabled (yaga.database.seed.enabled=false).");
      return;
    }

    LOG.info("DatabaseSeeder: Running development seed script (seed.cypher)...");
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream("neo4j/seeds/seed.cypher")) {
      if (is == null) {
        LOG.warn("DatabaseSeeder: seed.cypher not found in classpath.");
        return;
      }

      String content =
          new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
              .lines()
              .collect(Collectors.joining("\n"));

      // Separar sentencias Cypher por punto y coma ignorando comentarios
      String[] statements = content.split(";");

      try (Session session = driver.session()) {
        for (String rawStmt : statements) {
          String cleanStmt = cleanStatement(rawStmt);
          if (!cleanStmt.isBlank()) {
            session.executeWrite(tx -> tx.run(cleanStmt).consume());
          }
        }
      }
      LOG.info("DatabaseSeeder: Seed data applied successfully!");
    } catch (Exception e) {
      LOG.error("DatabaseSeeder: Error applying seed data: " + e.getMessage(), e);
    }
  }

  private String cleanStatement(String stmt) {
    return Arrays.stream(stmt.split("\n"))
        .filter(line -> !line.trim().startsWith("//"))
        .collect(Collectors.joining("\n"))
        .trim();
  }
}
