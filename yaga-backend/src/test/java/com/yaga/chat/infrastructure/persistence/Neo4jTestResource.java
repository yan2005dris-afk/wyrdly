package com.yaga.chat.infrastructure.persistence;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;
import org.testcontainers.containers.Neo4jContainer;

public class Neo4jTestResource implements QuarkusTestResourceLifecycleManager {

  private Neo4jContainer<?> neo4j;

  @Override
  public Map<String, String> start() {
    neo4j = new Neo4jContainer<>("neo4j:5.26-community")
        .withAdminPassword("password");
    neo4j.start();

    return Collections.singletonMap(
        "quarkus.neo4j.uri",
        neo4j.getBoltUrl()
    );
  }

  @Override
  public void stop() {
    if (neo4j != null) {
      neo4j.stop();
    }
  }
}
