package com.wyrdly.testsupport;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Points a Quarkus test (including {@code @QuarkusIntegrationTest}, which boots the packaged app)
 * at the shared {@link Neo4jTestContainer}, so it never depends on a database running on the
 * developer's machine or in CI.
 */
public class Neo4jTestResource implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    return Map.of(
        "quarkus.neo4j.uri",
        Neo4jTestContainer.boltUrl(),
        "quarkus.neo4j.authentication.disabled",
        "true");
  }

  @Override
  public void stop() {
    // The container is shared across the test JVM and removed by Ryuk on exit.
  }
}
