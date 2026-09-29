package com.wyrdly.post.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.CreatePostUseCase;
import com.wyrdly.post.domain.exception.PostValidationException;
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
            new AuthorDto("usr_123", "testuser", "Test User", "http://avatar.jpg"));

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
            new AuthorDto("usr_123", "testuser", "Test User", ""));

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
        .body("violations[0].message", equalTo("Content is required"));
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
        .body("violations[0].message", equalTo("Content must be between 1 and 1000 characters"));
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
        .body("violations.message", hasItem("mediaUrl must be a valid URL"));
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
        .body("violations.message", hasItem("mediaUrl must use http or https protocol"));
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
        .body("violations[0].message", equalTo("Content is required"));
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
        .body("violations.message", hasItem("Content must be between 1 and 1000 characters"));
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
        .body("violations[0].message", equalTo("Content is required"));
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
            new AuthorDto("usr_123", "testuser", "Test User", "http://avatar.jpg"));

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
            new AuthorDto("usr_123", "testuser", "Test User", ""));

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
            new AuthorDto("usr_456", "anotheruser", "Another User", ""));

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
}
