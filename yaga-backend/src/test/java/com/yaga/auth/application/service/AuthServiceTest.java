package com.yaga.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yaga.auth.application.dto.AuthResponse;
import com.yaga.auth.application.dto.LoginRequest;
import com.yaga.auth.application.dto.RefreshTokenRequest;
import com.yaga.auth.application.dto.RegisterRequest;
import com.yaga.auth.application.port.PasswordHasherPort;
import com.yaga.auth.application.port.TokenProviderPort;
import com.yaga.auth.domain.exception.InvalidCredentialsException;
import com.yaga.auth.domain.exception.UserAlreadyExistsException;
import com.yaga.auth.domain.exception.UserNotFoundException;
import com.yaga.auth.domain.model.AuthTokens;
import com.yaga.auth.domain.model.User;
import com.yaga.auth.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

  private UserRepository userRepository;
  private PasswordHasherPort passwordHasher;
  private TokenProviderPort tokenProvider;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    passwordHasher = mock(PasswordHasherPort.class);
    tokenProvider = mock(TokenProviderPort.class);
    authService = new AuthService(userRepository, passwordHasher, tokenProvider);
  }

  @Test
  void register_Success() {
    RegisterRequest request =
        new RegisterRequest(
            "testuser", "test@yaga.social", "Password123!", "Test User", "Bio test", null);

    when(userRepository.existsByUsername("testuser")).thenReturn(false);
    when(userRepository.existsByEmail("test@yaga.social")).thenReturn(false);
    when(passwordHasher.hash("Password123!")).thenReturn("hashed_pass");

    User savedUser =
        new User(
            "usr_123",
            "testuser",
            "test@yaga.social",
            "hashed_pass",
            "Test User",
            "Bio test",
            "",
            Instant.now());

    when(userRepository.save(any(User.class))).thenReturn(savedUser);
    when(tokenProvider.generateTokens(savedUser))
        .thenReturn(new AuthTokens("access_token", "refresh_token", 900));

    AuthResponse response = authService.register(request);

    assertNotNull(response);
    assertEquals("access_token", response.token());
    assertEquals("refresh_token", response.refreshToken());
    assertEquals("testuser", response.user().username());
    assertEquals("test@yaga.social", response.user().email());
    verify(userRepository).save(any(User.class));
  }

  @Test
  void register_ThrowsWhenUsernameExists() {
    RegisterRequest request =
        new RegisterRequest(
            "existinguser", "test@yaga.social", "Password123!", "Existing User", null, null);

    when(userRepository.existsByUsername("existinguser")).thenReturn(true);

    assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
  }

  @Test
  void register_ThrowsWhenEmailExists() {
    RegisterRequest request =
        new RegisterRequest(
            "newuser", "existing@yaga.social", "Password123!", "New User", null, null);

    when(userRepository.existsByUsername("newuser")).thenReturn(false);
    when(userRepository.existsByEmail("existing@yaga.social")).thenReturn(true);

    assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
  }

  @Test
  void authenticate_Success() {
    LoginRequest request = new LoginRequest("testuser", "Password123!");
    User user =
        new User(
            "usr_123",
            "testuser",
            "test@yaga.social",
            "hashed_pass",
            "Test User",
            "Bio test",
            "",
            Instant.now());

    when(userRepository.findByUsernameOrEmail("testuser")).thenReturn(Optional.of(user));
    when(passwordHasher.verify("Password123!", "hashed_pass")).thenReturn(true);
    when(tokenProvider.generateTokens(user))
        .thenReturn(new AuthTokens("access_token", "refresh_token", 900));

    AuthResponse response = authService.authenticate(request);

    assertNotNull(response);
    assertEquals("access_token", response.token());
    assertEquals("testuser", response.user().username());
  }

  @Test
  void authenticate_ThrowsWhenUserNotFound() {
    LoginRequest request = new LoginRequest("unknown", "Password123!");
    when(userRepository.findByUsernameOrEmail("unknown")).thenReturn(Optional.empty());

    assertThrows(InvalidCredentialsException.class, () -> authService.authenticate(request));
  }

  @Test
  void authenticate_ThrowsWhenPasswordInvalid() {
    LoginRequest request = new LoginRequest("testuser", "WrongPassword!");
    User user =
        new User(
            "usr_123",
            "testuser",
            "test@yaga.social",
            "hashed_pass",
            "Test User",
            "",
            "",
            Instant.now());

    when(userRepository.findByUsernameOrEmail("testuser")).thenReturn(Optional.of(user));
    when(passwordHasher.verify("WrongPassword!", "hashed_pass")).thenReturn(false);

    assertThrows(InvalidCredentialsException.class, () -> authService.authenticate(request));
  }

  @Test
  void refresh_Success() {
    RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");
    User user =
        new User(
            "usr_123",
            "testuser",
            "test@yaga.social",
            "hashed_pass",
            "Test User",
            "",
            "",
            Instant.now());

    when(tokenProvider.extractUserIdFromRefreshToken("valid_refresh_token")).thenReturn("usr_123");
    when(userRepository.findById("usr_123")).thenReturn(Optional.of(user));
    when(tokenProvider.generateTokens(user))
        .thenReturn(new AuthTokens("new_access_token", "new_refresh_token", 900));

    AuthResponse response = authService.refresh(request);

    assertNotNull(response);
    assertEquals("new_access_token", response.token());
    assertEquals("new_refresh_token", response.refreshToken());
  }

  @Test
  void refresh_ThrowsWhenUserNotFound() {
    RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");
    when(tokenProvider.extractUserIdFromRefreshToken("valid_refresh_token"))
        .thenReturn("usr_non_existent");
    when(userRepository.findById("usr_non_existent")).thenReturn(Optional.empty());

    assertThrows(UserNotFoundException.class, () -> authService.refresh(request));
  }
}
