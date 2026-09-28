package com.yaga.infrastructure.database;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;

@ApplicationScoped
public class DatabaseMigrationService {

  private static final Logger LOG = Logger.getLogger(DatabaseMigrationService.class);

  @Inject Driver driver;

  @ConfigProperty(name = "yaga.database.migration.enabled", defaultValue = "true")
  boolean migrationEnabled;

  void onStart(@Observes StartupEvent ev) {
    // Si estamos en perfil test y no se especificó explicitamente, omitir para permitir mocks /
    // unit tests aislados
    if (ConfigUtils.getProfiles().contains("test") && !migrationEnabled) {
      LOG.info("DatabaseMigrationService: Skipped in test profile.");
      return;
    }

    if (!migrationEnabled) {
      LOG.info("DatabaseMigrationService: Migrations are disabled.");
      return;
    }

    LOG.info("DatabaseMigrationService: Applying Neo4j schema migrations...");
    try {
      MigrationsConfig config =
          MigrationsConfig.builder().withLocationsToScan("classpath:neo4j/migrations").build();

      Migrations migrations = new Migrations(config, driver);
      migrations.apply();
      LOG.info("DatabaseMigrationService: Neo4j migrations applied successfully!");
    } catch (Exception e) {
      LOG.warn(
          "DatabaseMigrationService: Could not connect to Neo4j to apply migrations ("
              + e.getMessage()
              + "). Ensure Neo4j is running.");
    }
  }
}
