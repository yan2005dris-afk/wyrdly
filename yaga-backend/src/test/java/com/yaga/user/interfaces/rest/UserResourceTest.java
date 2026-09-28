package com.yaga.user.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yaga.user.application.dto.UpdateProfileRequest;
import com.yaga.user.application.dto.UserProfileResponse;
import com.yaga.user.application.usecase.GetUserProfileUseCase;
import com.yaga.user.application.usecase.UpdateUserProfileUseCase;
import com.yaga.user.domain.exception.UserProfileNotFoundException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
class UserResourceTest {

  @InjectMock GetUserProfileUseCase getUserProfileUseCase;

  @InjectMock UpdateUserProfileUseCase updateUserProfileUseCase;

  @Test
  void getProfile_Returns200_WhenNoTokenProvided() {
    UserProfileResponse response =
        new UserProfileResponse(
            "usr_123",
            "juanperez",
            "Juan Perez",
            "Bio",
            "",
            42L,
            18L,
            false,
            Instant.parse("2026-09-24T18:30:00Z"));

    when(getUserProfileUseCase.getProfile(eq("juanperez"), isNull())).thenReturn(response);

    given()
        .when()
        .get("/api/users/juanperez")
        .then()
        .statusCode(200)
        .body("username", equalTo("juanperez"))
        .body("followersCount", equalTo(42))
        .body("isFollowing", equalTo(false));
  }

  @Test
  void getProfile_Returns404_WhenUserDoesNotExist() {
    when(getUserProfileUseCase.getProfile(eq("ghost"), isNull()))
        .thenThrow(new UserProfileNotFoundException("El usuario 'ghost' no existe."));

    given()
        .when()
        .get("/api/users/ghost")
        .then()
        .statusCode(404)
        .body("error", equalTo("Not Found"))
        .body("message", equalTo("El usuario 'ghost' no existe."));
  }

  @Test
  void updateProfile_Returns401_WhenNoTokenProvided() {
    UpdateProfileRequest request = new UpdateProfileRequest("New Name", null, null);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .put("/api/users/profile")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void updateProfile_Returns200_WhenAuthenticated() {
    UpdateProfileRequest request =
        new UpdateProfileRequest("Juan Carlos Perez", "Nueva bio", "http://new-avatar");
    UserProfileResponse response =
        new UserProfileResponse(
            "usr_123",
            "juanperez",
            "Juan Carlos Perez",
            "Nueva bio",
            "http://new-avatar",
            42L,
            18L,
            false,
            Instant.parse("2026-09-24T18:30:00Z"));

    when(updateUserProfileUseCase.updateProfile(eq("usr_123"), eq(request))).thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .put("/api/users/profile")
        .then()
        .statusCode(200)
        .body("fullName", equalTo("Juan Carlos Perez"))
        .body("bio", equalTo("Nueva bio"));

    verify(updateUserProfileUseCase).updateProfile(eq("usr_123"), eq(request));
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void updateProfile_Returns400_WhenAvatarUrlIsNotHttpOrHttps() {
    UpdateProfileRequest request =
        new UpdateProfileRequest(null, null, "javascript:alert(document.cookie)");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .put("/api/users/profile")
        .then()
        .statusCode(400);

    verifyNoInteractions(updateUserProfileUseCase);
  }

  @Test
  @TestSecurity(user = "usr_123")
  @JwtSecurity(claims = {@Claim(key = "sub", value = "usr_123")})
  void updateProfile_Returns200_WhenAvatarUrlIsNull() {
    UpdateProfileRequest request = new UpdateProfileRequest("Juan Perez", null, null);
    UserProfileResponse response =
        new UserProfileResponse(
            "usr_123",
            "juanperez",
            "Juan Perez",
            "",
            "",
            0L,
            0L,
            false,
            Instant.parse("2026-09-24T18:30:00Z"));

    when(updateUserProfileUseCase.updateProfile(eq("usr_123"), eq(request))).thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .put("/api/users/profile")
        .then()
        .statusCode(200);
  }
}
