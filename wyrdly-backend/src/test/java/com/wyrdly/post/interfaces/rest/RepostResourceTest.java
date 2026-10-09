package com.wyrdly.post.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.RepostResponse;
import com.wyrdly.post.application.usecase.RepostPostUseCase;
import com.wyrdly.post.application.usecase.UndoRepostUseCase;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RepostResourceTest {

  @InjectMock RepostPostUseCase repostPostUseCase;
  @InjectMock UndoRepostUseCase undoRepostUseCase;

  @Test
  void repost_Returns401_WhenNotAuthenticated() {
    given().when().put("/api/posts/pst_1/repost").then().statusCode(401);
  }

  @Test
  void unrepost_Returns401_WhenNotAuthenticated() {
    given().when().delete("/api/posts/pst_1/repost").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void repost_Returns200_WhenValid() {
    when(repostPostUseCase.repost("usr_123", "pst_1")).thenReturn(new RepostResponse(true, 12L));

    given()
        .when()
        .put("/api/posts/pst_1/repost")
        .then()
        .statusCode(200)
        .body("reposted", equalTo(true))
        .body("repostsCount", equalTo(12));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void unrepost_Returns200_WhenValid() {
    when(undoRepostUseCase.unrepost("usr_123", "pst_1")).thenReturn(new RepostResponse(false, 11L));

    given()
        .when()
        .delete("/api/posts/pst_1/repost")
        .then()
        .statusCode(200)
        .body("reposted", equalTo(false))
        .body("repostsCount", equalTo(11));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void repost_Returns404_WhenPostNotFound() {
    when(repostPostUseCase.repost("usr_123", "pst_missing"))
        .thenThrow(new PostNotFoundException("pst_missing"));

    given()
        .when()
        .put("/api/posts/pst_missing/repost")
        .then()
        .statusCode(404)
        .body("code", equalTo("POST_NOT_FOUND"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void unrepost_Returns404_WhenPostNotFound() {
    when(undoRepostUseCase.unrepost("usr_123", "pst_missing"))
        .thenThrow(new PostNotFoundException("pst_missing"));

    given()
        .when()
        .delete("/api/posts/pst_missing/repost")
        .then()
        .statusCode(404)
        .body("code", equalTo("POST_NOT_FOUND"));
  }
}
