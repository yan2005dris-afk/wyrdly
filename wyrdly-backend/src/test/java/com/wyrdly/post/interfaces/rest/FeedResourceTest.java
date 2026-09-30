package com.wyrdly.post.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.FeedResponseDto;
import com.wyrdly.post.application.dto.FeedResponseDto.PaginationMeta;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.GetFeedUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FeedResourceTest {

  @InjectMock GetFeedUseCase getFeedUseCase;

  @Test
  void getFeed_Returns401_WhenUnauthenticated() {
    given().when().get("/api/feed").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void getFeed_Returns200_WithFeedDataAndPagination() {
    PostResponse post =
        new PostResponse(
            "pst_1",
            "Feed post content",
            "https://example.com/media.webp",
            Instant.parse("2026-09-29T10:00:00Z"),
            new AuthorDto("usr_author", "author_user", "Author User", "http://avatar.jpg"));

    FeedResponseDto responseDto =
        new FeedResponseDto(List.of(post), new PaginationMeta(1, 20, 1L, 1, false));

    when(getFeedUseCase.getFeed(eq("usr_123"), eq(1), eq(20))).thenReturn(responseDto);

    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/api/feed")
        .then()
        .statusCode(200)
        .body("data", hasSize(1))
        .body("data[0].id", equalTo("pst_1"))
        .body("data[0].content", equalTo("Feed post content"))
        .body("data[0].mediaUrl", equalTo("https://example.com/media.webp"))
        .body("data[0].author.id", equalTo("usr_author"))
        .body("data[0].author.username", equalTo("author_user"))
        .body("meta.page", equalTo(1))
        .body("meta.pageSize", equalTo(20))
        .body("meta.totalElements", equalTo(1))
        .body("meta.totalPages", equalTo(1))
        .body("meta.hasNext", equalTo(false));

    verify(getFeedUseCase).getFeed(eq("usr_123"), eq(1), eq(20));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void getFeed_Returns200_WithEmptyFeed() {
    FeedResponseDto responseDto =
        new FeedResponseDto(List.of(), new PaginationMeta(1, 20, 0L, 0, false));

    when(getFeedUseCase.getFeed(eq("usr_123"), eq(1), eq(20))).thenReturn(responseDto);

    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/api/feed")
        .then()
        .statusCode(200)
        .body("data", hasSize(0))
        .body("meta.totalElements", equalTo(0))
        .body("meta.totalPages", equalTo(0))
        .body("meta.hasNext", equalTo(false));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void getFeed_PropagatesCustomPaginationParams() {
    FeedResponseDto responseDto =
        new FeedResponseDto(List.of(), new PaginationMeta(3, 10, 0L, 0, false));

    when(getFeedUseCase.getFeed(eq("usr_123"), eq(3), eq(10))).thenReturn(responseDto);

    given()
        .queryParam("page", 3)
        .queryParam("pageSize", 10)
        .when()
        .get("/api/feed")
        .then()
        .statusCode(200);

    verify(getFeedUseCase).getFeed(eq("usr_123"), eq(3), eq(10));
  }
}
