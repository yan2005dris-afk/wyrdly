package com.yaga.auth.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yaga.auth.application.dto.AuthResponse;
import com.yaga.auth.application.dto.LoginRequest;
import com.yaga.auth.application.dto.RefreshTokenRequest;
import com.yaga.auth.application.dto.RegisterRequest;
import com.yaga.auth.application.dto.UserDto;
import com.yaga.auth.application.usecase.AuthenticateUserUseCase;
import com.yaga.auth.application.usecase.RefreshTokenUseCase;
import com.yaga.auth.application.usecase.RegisterUserUseCase;
import com.yaga.auth.domain.exception.InvalidCredentialsException;
import com.yaga.auth.domain.exception.UserAlreadyExistsException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuthResourceTest {

  @InjectMock RegisterUserUseCase registerUserUseCase;

  @InjectMock AuthenticateUserUseCase authenticateUserUseCase;

  @InjectMock RefreshTokenUseCase refreshTokenUseCase;

  @Test
  void register_Returns201OnSuccess() {
    RegisterRequest request =
        new RegisterRequest(
            "newuser", "newuser@yaga.social", "Password123!", "New User", "Bio", null);

    UserDto userDto =
        new UserDto("usr_123", "newuser", "newuser@yaga.social", "New User", "Bio", "");
    AuthResponse response = new AuthResponse("token_123", "refresh_123", 900, userDto);

    when(registerUserUseCase.register(any(RegisterRequest.class))).thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/auth/register")
        .then()
        .statusCode(201)
        .cookie("refreshToken", equalTo("refresh_123"))
        .body("token", equalTo("token_123"))
        .body("refreshToken", equalTo("refresh_123"))
        .body("user.username", equalTo("newuser"));
  }

  @Test
  void register_Returns409WhenUserExists() {
    RegisterRequest request =
        new RegisterRequest(
            "existinguser", "existing@yaga.social", "Password123!", "Existing User", null, null);

    when(registerUserUseCase.register(any(RegisterRequest.class)))
        .thenThrow(new UserAlreadyExistsException("Username already registered: existinguser"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/auth/register")
        .then()
        .statusCode(409)
        .body("error", equalTo("Conflict"))
        .body("message", equalTo("Username already registered: existinguser"));
  }

  @Test
  void login_Returns200OnSuccess() {
    LoginRequest request = new LoginRequest("testuser", "Password123!");
    UserDto userDto = new UserDto("usr_123", "testuser", "test@yaga.social", "Test User", "", "");
    AuthResponse response = new AuthResponse("token_123", "refresh_123", 900, userDto);

    when(authenticateUserUseCase.authenticate(any(LoginRequest.class))).thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/auth/login")
        .then()
        .statusCode(200)
        .cookie("refreshToken", equalTo("refresh_123"))
        .body("token", equalTo("token_123"))
        .body("user.username", equalTo("testuser"));
  }

  @Test
  void login_Returns401OnInvalidCredentials() {
    LoginRequest request = new LoginRequest("testuser", "WrongPass");

    when(authenticateUserUseCase.authenticate(any(LoginRequest.class)))
        .thenThrow(new InvalidCredentialsException("Invalid username or password"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/auth/login")
        .then()
        .statusCode(401)
        .body("error", equalTo("Unauthorized"))
        .body("message", equalTo("Invalid username or password"));
  }

  @Test
  void refresh_Returns200OnSuccessWithBody() {
    RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");
    UserDto userDto = new UserDto("usr_123", "testuser", "test@yaga.social", "Test User", "", "");
    AuthResponse response = new AuthResponse("new_token", "new_refresh", 900, userDto);

    when(refreshTokenUseCase.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/auth/refresh")
        .then()
        .statusCode(200)
        .cookie("refreshToken", equalTo("new_refresh"))
        .body("token", equalTo("new_token"))
        .body("refreshToken", equalTo("new_refresh"));
  }

  @Test
  void refresh_Returns200OnSuccessWithCookieOnly() {
    UserDto userDto = new UserDto("usr_123", "testuser", "test@yaga.social", "Test User", "", "");
    AuthResponse response = new AuthResponse("new_token", "new_refresh", 900, userDto);

    when(refreshTokenUseCase.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

    given()
        .cookie("refreshToken", "cookie_refresh_token")
        .when()
        .post("/api/auth/refresh")
        .then()
        .statusCode(200)
        .cookie("refreshToken", equalTo("new_refresh"))
        .body("token", equalTo("new_token"));
  }

  @Test
  void refresh_Returns400WhenNoTokenProvided() {
    given()
        .when()
        .post("/api/auth/refresh")
        .then()
        .statusCode(400)
        .body("message", equalTo("Refresh token is required"));
  }

  @Test
  void logout_Returns200AndClearsCookie() {
    given()
        .when()
        .post("/api/auth/logout")
        .then()
        .statusCode(200)
        .cookie("refreshToken", equalTo(""))
        .body("message", equalTo("Logged out successfully"));
  }
}
