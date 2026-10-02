package com.wyrdly.chat.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.wyrdly.chat.application.port.FollowValidationPort;
import com.wyrdly.chat.domain.repository.DirectMessageRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChatResourceTest {

  @InjectMock FollowValidationPort followValidationPort;

  @InjectMock DirectMessageRepository directMessageRepository;

  private static final String USER_1_ID = "user1-uuid";
  private static final String USER_2_ID = "user2-uuid";

  @BeforeEach
  void setUp() {
    when(followValidationPort.areMutualFollowers(USER_1_ID, USER_2_ID)).thenReturn(true);
    when(followValidationPort.areMutualFollowers(USER_2_ID, USER_1_ID)).thenReturn(true);

    when(directMessageRepository.findBetweenUsers(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(new ArrayList<>());
    when(directMessageRepository.countBetweenUsers(anyString(), anyString())).thenReturn(0L);
  }

  @Test
  @TestSecurity(user = USER_1_ID)
  @JwtSecurity(claims = {@Claim(key = "sub", value = USER_1_ID)})
  void shouldReturnChatHistoryWithAuthentication() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/api/chat/" + USER_2_ID + "/history?page=1&pageSize=50")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("meta", notNullValue())
        .body("meta.page", equalTo(1))
        .body("meta.pageSize", equalTo(50));
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
  @TestSecurity(user = USER_1_ID)
  @JwtSecurity(claims = {@Claim(key = "sub", value = USER_1_ID)})
  void shouldRejectWhenUsersDoNotFollowEachOther() {
    when(followValidationPort.areMutualFollowers(USER_1_ID, USER_2_ID)).thenReturn(false);

    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/api/chat/" + USER_2_ID + "/history?page=1&pageSize=50")
        .then()
        .statusCode(403);
  }
}
