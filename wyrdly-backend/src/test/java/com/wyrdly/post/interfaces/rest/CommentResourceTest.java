package com.wyrdly.post.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.CommentListResponseDto;
import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.CreateCommentUseCase;
import com.wyrdly.post.application.usecase.DeleteCommentUseCase;
import com.wyrdly.post.application.usecase.ListCommentsByPostUseCase;
import com.wyrdly.post.domain.exception.CommentNotFoundException;
import com.wyrdly.post.domain.exception.UnauthorizedCommentActionException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Endpoint tests for {@code /api/posts/{postId}/comments}. */
@QuarkusTest
class CommentResourceTest {

  @InjectMock CreateCommentUseCase createCommentUseCase;
  @InjectMock ListCommentsByPostUseCase listCommentsByPostUseCase;
  @InjectMock DeleteCommentUseCase deleteCommentUseCase;

  private static CommentResponse sampleComment(String id, String postId, String authorId) {
    return new CommentResponse(
        id,
        postId,
        authorId,
        "Hello world",
        Instant.parse("2026-10-08T12:00:00Z"),
        new AuthorDto(authorId, "alice", "Alice", "https://avatar.jpg"));
  }

  // ---------------------------------------------------------------------------
  // POST /api/posts/{postId}/comments
  // ---------------------------------------------------------------------------

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createCommentReturns201WhenValid() {
    CreateCommentRequest request = new CreateCommentRequest("Hello world");
    CommentResponse response = sampleComment("cmt_1", "pst_abc", "usr_123");

    when(createCommentUseCase.createComment(
            eq("pst_abc"), eq("usr_123"), any(CreateCommentRequest.class)))
        .thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts/pst_abc/comments")
        .then()
        .statusCode(201)
        .body("id", equalTo("cmt_1"))
        .body("postId", equalTo("pst_abc"))
        .body("authorId", equalTo("usr_123"))
        .body("content", equalTo("Hello world"));

    verify(createCommentUseCase)
        .createComment(eq("pst_abc"), eq("usr_123"), any(CreateCommentRequest.class));
  }

  @Test
  void createCommentReturns401WhenNoAuth() {
    given()
        .contentType(ContentType.JSON)
        .body(new CreateCommentRequest("Hi"))
        .when()
        .post("/api/posts/pst_abc/comments")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createCommentReturns400WhenContentBlank() {
    given()
        .contentType(ContentType.JSON)
        .body(new CreateCommentRequest("   "))
        .when()
        .post("/api/posts/pst_abc/comments")
        .then()
        .statusCode(400)
        .body("message", containsString("vacío"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createCommentReturns400WhenContentTooLong() {
    given()
        .contentType(ContentType.JSON)
        .body(new CreateCommentRequest("x".repeat(1001)))
        .when()
        .post("/api/posts/pst_abc/comments")
        .then()
        .statusCode(400)
        .body("message", containsString("1000"));
  }

  // ---------------------------------------------------------------------------
  // GET /api/posts/{postId}/comments
  // ---------------------------------------------------------------------------

  @Test
  void getCommentsReturns200WithPagination() {
    CommentListResponseDto response =
        new CommentListResponseDto(
            List.of(
                sampleComment("cmt_1", "pst_abc", "usr_alice"),
                sampleComment("cmt_2", "pst_abc", "usr_bob")),
            2L,
            1,
            20);
    when(listCommentsByPostUseCase.getComments(eq("pst_abc"), eq(1), eq(20))).thenReturn(response);

    given()
        .when()
        .get("/api/posts/pst_abc/comments")
        .then()
        .statusCode(200)
        .body("data", hasSize(2))
        .body("totalCount", equalTo(2))
        .body("page", equalTo(1))
        .body("pageSize", equalTo(20));

    verify(listCommentsByPostUseCase).getComments("pst_abc", 1, 20);
  }

  // ---------------------------------------------------------------------------
  // DELETE /api/posts/{postId}/comments/{commentId}
  // ---------------------------------------------------------------------------

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void deleteCommentReturns204ForAuthor() {
    given().when().delete("/api/posts/pst_abc/comments/cmt_1").then().statusCode(204);

    verify(deleteCommentUseCase).deleteComment("pst_abc", "cmt_1", "usr_123");
  }

  @Test
  @TestSecurity(user = "usr_bob")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_bob")})
  void deleteCommentReturns204ForPostOwner() {
    given().when().delete("/api/posts/pst_abc/comments/cmt_1").then().statusCode(204);

    verify(deleteCommentUseCase).deleteComment("pst_abc", "cmt_1", "usr_bob");
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void deleteCommentReturns403ForOtherUser() {
    doThrow(new UnauthorizedCommentActionException("Not allowed"))
        .when(deleteCommentUseCase)
        .deleteComment(eq("pst_abc"), eq("cmt_1"), eq("usr_123"));

    given()
        .when()
        .delete("/api/posts/pst_abc/comments/cmt_1")
        .then()
        .statusCode(403)
        .body("code", equalTo("FORBIDDEN"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void deleteCommentReturns404WhenCommentMissing() {
    doThrow(new CommentNotFoundException("cmt_missing"))
        .when(deleteCommentUseCase)
        .deleteComment(eq("pst_abc"), eq("cmt_missing"), eq("usr_123"));

    given()
        .when()
        .delete("/api/posts/pst_abc/comments/cmt_missing")
        .then()
        .statusCode(404)
        .body("code", equalTo("COMMENT_NOT_FOUND"));
  }

  @Test
  void deleteCommentReturns401WhenNoAuth() {
    given().when().delete("/api/posts/pst_abc/comments/cmt_1").then().statusCode(401);
  }
}
