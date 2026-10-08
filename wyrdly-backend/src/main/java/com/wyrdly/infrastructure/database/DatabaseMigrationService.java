package com.wyrdly.infrastructure.database;

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

  @ConfigProperty(name = "wyrdly.database.migration.enabled", defaultValue = "true")
  boolean migrationEnabled;

  /**
   * How the schema migrations are discovered and run. Public so integration tests migrate their
   * databases exactly like production does.
   */
  public static MigrationsConfig migrationsConfig() {
    return MigrationsConfig.builder().withLocationsToScan("classpath:neo4j/migrations").build();
  }

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
      Migrations migrations = new Migrations(migrationsConfig(), driver);
      migrations.apply();
      LOG.info("DatabaseMigrationService: Neo4j migrations applied successfully!");
    } catch (RuntimeException e) {
      // Fail fast: serving traffic on a partially migrated schema hides the problem behind a
      // healthy container. Aborting startup makes the deploy fail where it can be seen.
      LOG.error("DatabaseMigrationService: Neo4j migrations failed, aborting startup", e);
      throw e;
    }
  }
}
