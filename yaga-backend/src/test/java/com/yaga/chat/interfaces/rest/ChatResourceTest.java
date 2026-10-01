package com.yaga.chat.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.yaga.chat.application.port.FollowValidationPort;
import com.yaga.chat.application.dto.ChatHistoryPage;
import com.yaga.chat.domain.repository.DirectMessageRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChatResourceTest {

  @InjectMock
  FollowValidationPort followValidationPort;

  @InjectMock
  DirectMessageRepository directMessageRepository;

  @BeforeEach
  void setUp() {
    when(followValidationPort.areMutualFollowers("user1", "user2")).thenReturn(true);
    when(followValidationPort.areMutualFollowers("user2", "user1")).thenReturn(true);

    ChatHistoryPage emptyHistory = new ChatHistoryPage(
        new ArrayList<>(),
        new ChatHistoryPage.ChatHistoryMeta(0, 1, 50)
    );
    when(directMessageRepository.findBetweenUsers(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(new ArrayList<>());
    when(directMessageRepository.countBetweenUsers(anyString(), anyString()))
        .thenReturn(0L);
  }

  @Test
  @TestSecurity(user = "user1", roles = "user")
  void shouldReturnChatHistoryWithAuthentication() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/api/chat/user2/history?page=1&pageSize=50")
        .then()
        .statusCode(200)
        .body("meta.total", notNullValue())
        .body("meta.page", equalTo(1))
        .body("meta.pageSize", equalTo(50))
        .body("data", notNullValue());
  }

  @Test
  void shouldRejectUnauthenticatedRequest() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/api/chat/user2/history?page=1&pageSize=50")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "user1", roles = "user")
  void shouldRejectInvalidPageSize() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/api/chat/user2/history?page=1&pageSize=200")
        .then()
        .statusCode(200)
        .body("meta.pageSize", equalTo(50));
  }
}
