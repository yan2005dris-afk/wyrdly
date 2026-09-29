package com.wyrdly.user.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.wyrdly.user.application.dto.UserSearchResponseDto;
import com.wyrdly.user.application.dto.UserSearchResponseDto.Meta;
import com.wyrdly.user.application.dto.UserSearchResultDto;
import com.wyrdly.user.application.usecase.SearchUsersUseCase;
import com.wyrdly.user.domain.exception.SearchValidationException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class UserSearchResourceTest {

  @InjectMock SearchUsersUseCase searchUsersUseCase;

  @Test
  void searchUsers_returns401_whenNoAuthentication() {
    given().when().get("/api/users/search?q=alice").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void searchUsers_returns200_withResults() {
    UserSearchResponseDto response =
        new UserSearchResponseDto(
            List.of(
                new UserSearchResultDto(
                    "usr_alice", "alice", "Alice Chen", null, "Backend dev", false, null),
                new UserSearchResultDto(
                    "usr_alicia", "alicia", "Alicia Keys", null, null, true, "2 amigos en común")),
            new Meta(0, 20, 2));

    when(searchUsersUseCase.searchUsers(eq("usr_123"), eq("alice"), eq(0), eq(20)))
        .thenReturn(response);

    given()
        .when()
        .get("/api/users/search?q=alice")
        .then()
        .statusCode(200)
        .body("data", hasSize(2))
        .body("data[0].id", equalTo("usr_alice"))
        .body("data[0].username", equalTo("alice"))
        .body("data[0].fullName", equalTo("Alice Chen"))
        .body("data[0].bio", equalTo("Backend dev"))
        .body("data[0].isFollowing", equalTo(false))
        .body("data[0].mutualConnectionSnippet", equalTo(null))
        .body("data[1].id", equalTo("usr_alicia"))
        .body("data[1].isFollowing", equalTo(true))
        .body("data[1].mutualConnectionSnippet", equalTo("2 amigos en común"))
        .body("meta.page", equalTo(0))
        .body("meta.pageSize", equalTo(20))
        .body("meta.totalResults", equalTo(2));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void searchUsers_returns200_emptyData_whenNoResults() {
    UserSearchResponseDto response = new UserSearchResponseDto(List.of(), new Meta(0, 20, 0));

    when(searchUsersUseCase.searchUsers(eq("usr_123"), eq("zzzzz"), eq(0), eq(20)))
        .thenReturn(response);

    given()
        .when()
        .get("/api/users/search?q=zzzzz")
        .then()
        .statusCode(200)
        .body("data", hasSize(0))
        .body("meta.totalResults", equalTo(0));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void searchUsers_returns400_whenQueryMissing() {
    when(searchUsersUseCase.searchUsers(eq("usr_123"), eq(null), anyInt(), anyInt()))
        .thenThrow(new SearchValidationException("La búsqueda debe tener al menos 2 caracteres."));

    given()
        .when()
        .get("/api/users/search")
        .then()
        .statusCode(400)
        .body("status", equalTo(400))
        .body("error", equalTo("Bad Request"))
        .body("message", equalTo("La búsqueda debe tener al menos 2 caracteres."))
        .body("timestamp", notNullValue());
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void searchUsers_returns400_whenQueryShorterThanMinLength() {
    when(searchUsersUseCase.searchUsers(eq("usr_123"), eq("a"), anyInt(), anyInt()))
        .thenThrow(new SearchValidationException("La búsqueda debe tener al menos 2 caracteres."));

    given()
        .when()
        .get("/api/users/search?q=a")
        .then()
        .statusCode(400)
        .body("message", equalTo("La búsqueda debe tener al menos 2 caracteres."));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void searchUsers_returns400_whenPageSizeExceedsMax() {
    when(searchUsersUseCase.searchUsers(eq("usr_123"), anyString(), anyInt(), eq(51)))
        .thenThrow(new SearchValidationException("El tamaño de página debe estar entre 1 y 50."));

    given()
        .when()
        .get("/api/users/search?q=alice&pageSize=51")
        .then()
        .statusCode(400)
        .body("message", equalTo("El tamaño de página debe estar entre 1 y 50."));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void searchUsers_returns400_whenPageIsNegative() {
    when(searchUsersUseCase.searchUsers(eq("usr_123"), anyString(), eq(-1), anyInt()))
        .thenThrow(new SearchValidationException("La página debe ser mayor o igual a 0."));

    given()
        .when()
        .get("/api/users/search?q=alice&page=-1")
        .then()
        .statusCode(400)
        .body("message", equalTo("La página debe ser mayor o igual a 0."));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void searchUsers_passesCorrectPaginationToUseCase() {
    UserSearchResponseDto response = new UserSearchResponseDto(List.of(), new Meta(2, 10, 0));

    when(searchUsersUseCase.searchUsers(eq("usr_123"), eq("alice"), eq(2), eq(10)))
        .thenReturn(response);

    given()
        .when()
        .get("/api/users/search?q=alice&page=2&pageSize=10")
        .then()
        .statusCode(200)
        .body("meta.page", equalTo(2))
        .body("meta.pageSize", equalTo(10));
  }
}
