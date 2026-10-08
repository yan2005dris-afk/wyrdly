package com.wyrdly;

import com.wyrdly.testsupport.Neo4jTestResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Boots the packaged application, which applies the Neo4j migrations on startup and aborts if they
 * fail, so it gets a real database instead of whatever runs on localhost.
 */
@QuarkusIntegrationTest
@WithTestResource(Neo4jTestResource.class)
class GreetingResourceIT extends GreetingResourceTest {
  // Execute the same tests but in packaged mode.
}
