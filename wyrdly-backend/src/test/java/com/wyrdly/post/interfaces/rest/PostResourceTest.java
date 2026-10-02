package com.wyrdly.post.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.CreatePostUseCase;
import com.wyrdly.post.application.usecase.ReactToPostUseCase;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PostResourceTest {

  @InjectMock CreatePostUseCase createPostUseCase;
  @InjectMock ReactToPostUseCase reactToPostUseCase;

  @Test
  void createPost_Returns401_WhenNoAuthenticationProvided() {
    CreatePostRequest request = new CreatePostRequest("Test post", null);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns201_WhenValidPostCreated() {
    CreatePostRequest request = new CreatePostRequest("This is a test post", null);
    PostResponse response =
        new PostResponse(
            "pst_abc123",
            "This is a test post",
            null,
            Instant.parse("2026-09-28T12:00:00Z"),
            new AuthorDto("usr_123", "testuser", "Test User", "http://avatar.jpg"),
            new PostResponse.ReactionCounts(0, 0, 0),
            null);

    when(createPostUseCase.createPost(eq("usr_123"), any(CreatePostRequest.class)))
        .thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(201)
        .body("id", equalTo("pst_abc123"))
        .body("content", equalTo("This is a test post"))
        .body("author.username", equalTo("testuser"))
        .body("author.fullName", equalTo("Test User"));

    verify(createPostUseCase).createPost(eq("usr_123"), any(CreatePostRequest.class));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns201_WithMediaUrl() {
    CreatePostRequest request =
        new CreatePostRequest("Post with media", "https://example.com/image.jpg");
    PostResponse response =
        new PostResponse(
            "pst_abc123",
            "Post with media",
            "https://example.com/image.jpg",
            Instant.parse("2026-09-28T12:00:00Z"),
            new AuthorDto("usr_123", "testuser", "Test User", ""),
            new PostResponse.ReactionCounts(0, 0, 0),
            null);

    when(createPostUseCase.createPost(eq("usr_123"), any(CreatePostRequest.class)))
        .thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("content", equalTo("Post with media"))
        .body("mediaUrl", equalTo("https://example.com/image.jpg"));

    verify(createPostUseCase).createPost(eq("usr_123"), any(CreatePostRequest.class));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenContentIsBlank() {
    CreatePostRequest request = new CreatePostRequest("   ", null);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("message", containsString("Content is required"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenContentExceedsMaxLength() {
    String tooLongContent = "a".repeat(1001);
    CreatePostRequest request = new CreatePostRequest(tooLongContent, null);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("message", containsString("Content must be between 1 and 1000 characters"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenMediaUrlIsMalformed() {
    CreatePostRequest request = new CreatePostRequest("Valid content", "not-a-valid-url");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("message", containsString("mediaUrl must be a valid URL"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenMediaUrlUsesInvalidProtocol() {
    CreatePostRequest request =
        new CreatePostRequest("Valid content", "ftp://example.com/image.jpg");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("message", containsString("mediaUrl must use http or https protocol"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenBodyIsEmptyJsonObject() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("attributeName", equalTo("content"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenContentIsEmptyLiteral() {
    CreatePostRequest request = new CreatePostRequest("", "https://example.com/image.jpg");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("message", containsString("Content must be between 1 and 1000 characters"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenContentIsNullWithMediaUrl() {
    CreatePostRequest request = new CreatePostRequest(null, "https://example.com/image.jpg");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("message", containsString("Content is required"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_Returns400_WhenAuthorNotFound() {
    CreatePostRequest request = new CreatePostRequest("Valid content", null);

    when(createPostUseCase.createPost(eq("usr_123"), any(CreatePostRequest.class)))
        .thenThrow(new PostValidationException("Author user not found"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(400)
        .body("error", equalTo("Bad Request"))
        .body("message", equalTo("Author user not found"));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_ReturnsCorrectStructure_WithAllFields() {
    CreatePostRequest request = new CreatePostRequest("Test post", "https://example.com/img.jpg");
    PostResponse response =
        new PostResponse(
            "pst_abc123",
            "Test post",
            "https://example.com/img.jpg",
            Instant.parse("2026-09-28T12:00:00Z"),
            new AuthorDto("usr_123", "testuser", "Test User", "http://avatar.jpg"),
            new PostResponse.ReactionCounts(0, 0, 0),
            null);

    when(createPostUseCase.createPost(eq("usr_123"), any(CreatePostRequest.class)))
        .thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("content", notNullValue())
        .body("mediaUrl", notNullValue())
        .body("createdAt", notNullValue())
        .body("author", notNullValue())
        .body("author.id", notNullValue())
        .body("author.username", notNullValue())
        .body("author.fullName", notNullValue());
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void createPost_PassesCorrectUserIdFromJwt() {
    CreatePostRequest request = new CreatePostRequest("Test post", null);
    PostResponse response =
        new PostResponse(
            "pst_abc123",
            "Test post",
            null,
            Instant.parse("2026-09-28T12:00:00Z"),
            new AuthorDto("usr_123", "testuser", "Test User", ""),
            new PostResponse.ReactionCounts(0, 0, 0),
            null);

    when(createPostUseCase.createPost(eq("usr_123"), any(CreatePostRequest.class)))
        .thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(201);

    verify(createPostUseCase).createPost(eq("usr_123"), any(CreatePostRequest.class));
  }

  @Test
  @TestSecurity(user = "usr_456")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_456")})
  void createPost_UsesCorrectUserFromDifferentUsers() {
    CreatePostRequest request = new CreatePostRequest("Another user post", null);
    PostResponse response =
        new PostResponse(
            "pst_xyz789",
            "Another user post",
            null,
            Instant.parse("2026-09-28T12:00:00Z"),
            new AuthorDto("usr_456", "anotheruser", "Another User", ""),
            new PostResponse.ReactionCounts(0, 0, 0),
            null);

    when(createPostUseCase.createPost(eq("usr_456"), any(CreatePostRequest.class)))
        .thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/posts")
        .then()
        .statusCode(201)
        .body("author.id", equalTo("usr_456"));

    verify(createPostUseCase).createPost(eq("usr_456"), any(CreatePostRequest.class));
  }

  // ---------------------------------------------------------------------------
  // HU09 — reactions endpoint
  // ---------------------------------------------------------------------------

  @Test
  void react_Returns401_WhenNoAuth() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"type\":\"LIKE\"}")
        .when()
        .post("/api/posts/pst_abc/react")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void react_Returns200_Added_WhenValidRequest() {
    when(reactToPostUseCase.react(eq("usr_123"), eq("pst_abc"), eq(ReactionType.LIKE)))
        .thenReturn(new ReactionResult("pst_abc", ReactionStatus.ADDED, ReactionType.LIKE, 1L));

    given()
        .contentType(ContentType.JSON)
        .body("{\"type\":\"LIKE\"}")
        .when()
        .post("/api/posts/pst_abc/react")
        .then()
        .statusCode(200)
        .body("status", equalTo("ADDED"))
        .body("reactionType", equalTo("LIKE"))
        .body("totalReactions", equalTo(1));

    verify(reactToPostUseCase).react("usr_123", "pst_abc", ReactionType.LIKE);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void react_Returns200_Removed_WhenToggledIdentical() {
    when(reactToPostUseCase.react(eq("usr_123"), eq("pst_abc"), eq(ReactionType.LIKE)))
        .thenReturn(new ReactionResult("pst_abc", ReactionStatus.REMOVED, null, 0L));

    given()
        .contentType(ContentType.JSON)
        .body("{\"type\":\"LIKE\"}")
        .when()
        .post("/api/posts/pst_abc/react")
        .then()
        .statusCode(200)
        .body("status", equalTo("REMOVED"))
        .body("reactionType", nullValue())
        .body("totalReactions", equalTo(0));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void react_Returns200_Updated_WhenDifferentType() {
    when(reactToPostUseCase.react(eq("usr_123"), eq("pst_abc"), eq(ReactionType.LOVE)))
        .thenReturn(new ReactionResult("pst_abc", ReactionStatus.UPDATED, ReactionType.LOVE, 5L));

    given()
        .contentType(ContentType.JSON)
        .body("{\"type\":\"LOVE\"}")
        .when()
        .post("/api/posts/pst_abc/react")
        .then()
        .statusCode(200)
        .body("status", equalTo("UPDATED"))
        .body("reactionType", equalTo("LOVE"))
        .body("totalReactions", equalTo(5));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void react_Returns400_WhenTypeFieldMissing() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when()
        .post("/api/posts/pst_abc/react")
        .then()
        .statusCode(400);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void react_Returns400_WhenTypeIsInvalidEnum() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"type\":\"DISLIKE\"}")
        .when()
        .post("/api/posts/pst_abc/react")
        .then()
        .statusCode(400);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void react_Returns404_WhenPostNotFound() {
    when(reactToPostUseCase.react(eq("usr_123"), eq("pst_missing"), eq(ReactionType.LIKE)))
        .thenThrow(new PostNotFoundException("pst_missing"));

    given()
        .contentType(ContentType.JSON)
        .body("{\"type\":\"LIKE\"}")
        .when()
        .post("/api/posts/pst_missing/react")
        .then()
        .statusCode(404)
        .body("code", equalTo("POST_NOT_FOUND"));
  }
}
