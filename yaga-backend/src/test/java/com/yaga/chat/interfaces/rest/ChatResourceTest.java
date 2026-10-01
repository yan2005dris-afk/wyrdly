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
import com.yaga.chat.infrastructure.jwt.JwtTestTokenGenerator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChatResourceTest {

  @Inject JwtTestTokenGenerator tokenGenerator;

  @InjectMock
  FollowValidationPort followValidationPort;

  @InjectMock
  DirectMessageRepository directMessageRepository;

  private String user1Token;
  private String user2Token;
  private static final String USER_1_ID = "user1-uuid";
  private static final String USER_2_ID = "user2-uuid";

  @BeforeEach
  void setUp() {
    user1Token = tokenGenerator.generateToken(USER_1_ID, "user1");
    user2Token = tokenGenerator.generateToken(USER_2_ID, "user2");

    when(followValidationPort.areMutualFollowers(USER_1_ID, USER_2_ID)).thenReturn(true);
    when(followValidationPort.areMutualFollowers(USER_2_ID, USER_1_ID)).thenReturn(true);

    when(directMessageRepository.findBetweenUsers(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(new ArrayList<>());
    when(directMessageRepository.countBetweenUsers(anyString(), anyString()))
        .thenReturn(0L);
  }

  @Test
  void shouldReturnChatHistoryWithAuthentication() {
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + user1Token)
        .when()
        .get("/api/chat/" + USER_2_ID + "/history?page=1&pageSize=50")
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
        .get("/api/chat/" + USER_2_ID + "/history?page=1&pageSize=50")
        .then()
        .statusCode(401);
  }

  @Test
  void shouldRejectInvalidPageSize() {
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + user1Token)
        .when()
        .get("/api/chat/" + USER_2_ID + "/history?page=1&pageSize=200")
        .then()
        .statusCode(200)
        .body("meta.pageSize", equalTo(50));
  }
}
