package com.yaga.chat.infrastructure.persistence;

import java.util.Map;

public class Neo4jTestProfile {
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.neo4j.uri", "neo4j://localhost:7687",
        "quarkus.neo4j.authentication.username", "neo4j",
        "quarkus.neo4j.authentication.password", "password"
    );
  }
}
