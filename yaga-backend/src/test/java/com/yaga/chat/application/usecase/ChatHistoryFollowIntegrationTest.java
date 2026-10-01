package com.yaga.chat.application.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.yaga.chat.application.dto.ChatHistoryPage;
import com.yaga.chat.application.port.FollowValidationPort;
import com.yaga.chat.infrastructure.persistence.Neo4jDirectMessageRepositoryAdapter;
import com.yaga.user.domain.repository.UserProfileRepository;
import com.yaga.user.infrastructure.persistence.Neo4jUserProfileRepositoryAdapter;
import com.yaga.user.infrastructure.qualifier.ResilientNeo4j;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

@QuarkusTest
@QuarkusTestResource(com.yaga.chat.infrastructure.persistence.Neo4jTestResource.class)
@DisplayName("Chat History Follow Integration Tests")
class ChatHistoryFollowIntegrationTest {

  @Inject
  GetChatHistoryUseCase getChatHistoryUseCase;

  @Inject
  @ResilientNeo4j
  UserProfileRepository userProfileRepository;

  @Inject
  FollowValidationPort followValidationPort;

  @Inject
  Driver neo4jDriver;

  private String userId1;
  private String userId2;

  @BeforeEach
  void setUp() {
    userId1 = "usr_integration_test_1";
    userId2 = "usr_integration_test_2";

    // Create test users in Neo4j
    seedUser(userId1, "testuser1", "Test User 1", "Bio 1", "");
    seedUser(userId2, "testuser2", "Test User 2", "Bio 2", "");
  }

  private void seedUser(String id, String username, String fullName, String bio, String avatarUrl) {
    try (var session = neo4jDriver.session()) {
      session.run(
          "CREATE (u:Usuario {id: $id, username: $username, fullName: $fullName, "
              + "bio: $bio, avatarUrl: $avatarUrl, email: $email, passwordHash: 'x', "
              + "createdAt: datetime($createdAt)})",
          java.util.Map.of(
              "id", id,
              "username", username,
              "fullName", fullName,
              "bio", bio,
              "avatarUrl", avatarUrl,
              "email", username + "@yaga.social",
              "createdAt", java.time.Instant.parse("2026-01-01T00:00:00Z").toString()));
    }
  }

  @Test
  @DisplayName("Should retrieve chat history when users are mutual followers")
  void testChatHistoryWithMutualFollowers() {
    // Setup: Make users follow each other
    userProfileRepository.followUser(userId1, userId2);
    userProfileRepository.followUser(userId2, userId1);

    // Verify follow relationships exist
    boolean user1FollowsUser2 = userProfileRepository.isFollowing(userId1, userId2);
    boolean user2FollowsUser1 = userProfileRepository.isFollowing(userId2, userId1);

    assertTrue(user1FollowsUser2, "User 1 should follow User 2");
    assertTrue(user2FollowsUser1, "User 2 should follow User 1");

    // Verify mutual followers
    boolean areMutual = followValidationPort.areMutualFollowers(userId1, userId2);
    assertTrue(areMutual, "Users should be mutual followers");

    // Test: Should be able to get chat history
    assertDoesNotThrow(() -> {
      ChatHistoryPage history = getChatHistoryUseCase.execute(userId1, userId2, 1, 50);
      assertNotNull(history, "Chat history should not be null");
      assertNotNull(history.getData(), "Messages list should not be null");
    }, "Should not throw exception when retrieving history for mutual followers");
  }

  @Test
  @DisplayName("Should deny access when users are NOT mutual followers")
  void testChatHistoryWithoutMutualFollowers() {
    String nonFollowerUserId = "usr_non_follower";

    // Don't set up any follow relationship
    boolean areMutual = followValidationPort.areMutualFollowers(userId1, nonFollowerUserId);
    assertFalse(areMutual, "Non-followers should not be mutual");

    // Should throw exception
    assertThrows(
        com.yaga.chat.domain.exception.UsersNotFollowingException.class,
        () -> getChatHistoryUseCase.execute(userId1, nonFollowerUserId, 1, 50),
        "Should throw UsersNotFollowingException for non-mutual followers");
  }

  @Test
  @DisplayName("Should verify Neo4j SIGUE relationships")
  void testNeo4jFollowRelationships() {
    // Create follow relationships
    userProfileRepository.followUser(userId1, userId2);
    userProfileRepository.followUser(userId2, userId1);

    // Direct query verification
    boolean isFollowing1To2 = userProfileRepository.isFollowing(userId1, userId2);
    boolean isFollowing2To1 = userProfileRepository.isFollowing(userId2, userId1);

    System.out.println("=== Neo4j SIGUE Relationship Verification ===");
    System.out.println("User 1 (" + userId1 + ") → User 2 (" + userId2 + "): " + isFollowing1To2);
    System.out.println("User 2 (" + userId2 + ") → User 1 (" + userId1 + "): " + isFollowing2To1);
    System.out.println("Mutual: " + (isFollowing1To2 && isFollowing2To1));

    assertTrue(isFollowing1To2, "SIGUE relationship 1→2 should exist in Neo4j");
    assertTrue(isFollowing2To1, "SIGUE relationship 2→1 should exist in Neo4j");
  }
}
