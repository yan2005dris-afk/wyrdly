package com.wyrdly.user.infrastructure.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

@Liveness
@ApplicationScoped
public class Neo4jHealthCheck implements HealthCheck {

  @Inject Driver driver;

  @Override
  public HealthCheckResponse call() {
    try (Session session = driver.session()) {
      session.run("RETURN 1").consume();
      return HealthCheckResponse.named("neo4j").up().build();
    } catch (Exception e) {
      return HealthCheckResponse.named("neo4j").down().build();
    }
  }
}
