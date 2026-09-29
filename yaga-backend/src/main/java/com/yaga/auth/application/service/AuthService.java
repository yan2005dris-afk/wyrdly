package com.yaga.auth.application.service;

import com.yaga.auth.application.dto.AuthResponse;
import com.yaga.auth.application.dto.LoginRequest;
import com.yaga.auth.application.dto.RefreshTokenRequest;
import com.yaga.auth.application.dto.RegisterRequest;
import com.yaga.auth.application.dto.UserDto;
import com.yaga.auth.application.port.PasswordHasherPort;
import com.yaga.auth.application.port.TokenProviderPort;
import com.yaga.auth.application.usecase.AuthenticateUserUseCase;
import com.yaga.auth.application.usecase.RefreshTokenUseCase;
import com.yaga.auth.application.usecase.RegisterUserUseCase;
import com.yaga.auth.domain.exception.InvalidCredentialsException;
import com.yaga.auth.domain.exception.UserAlreadyExistsException;
import com.yaga.auth.domain.exception.UserNotFoundException;
import com.yaga.auth.domain.model.AuthTokens;
import com.yaga.auth.domain.model.User;
import com.yaga.auth.domain.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class AuthService
    implements RegisterUserUseCase, AuthenticateUserUseCase, RefreshTokenUseCase {

  private final UserRepository userRepository;
  private final PasswordHasherPort passwordHasher;
  private final TokenProviderPort tokenProvider;

  @Inject
  public AuthService(
      UserRepository userRepository,
      PasswordHasherPort passwordHasher,
      TokenProviderPort tokenProvider) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
    this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
  }

  @Override
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.username())) {
      throw new UserAlreadyExistsException("Username already registered: " + request.username());
    }
    if (userRepository.existsByEmail(request.email())) {
      throw new UserAlreadyExistsException("Email already registered: " + request.email());
    }

    String userId = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String passwordHash = passwordHasher.hash(request.password());

    User newUser =
        User.createNew(
            userId,
            request.username().trim().toLowerCase(),
            request.email().trim().toLowerCase(),
            passwordHash,
            request.fullName().trim(),
            request.bio() != null ? request.bio().trim() : "",
            request.avatarUrl() != null ? request.avatarUrl().trim() : "");

    User savedUser = userRepository.save(newUser);
    AuthTokens tokens = tokenProvider.generateTokens(savedUser);

    return new AuthResponse(
        tokens.accessToken(),
        tokens.refreshToken(),
        tokens.expiresIn(),
        UserDto.fromDomain(savedUser));
  }

  @Override
  public AuthResponse authenticate(LoginRequest request) {
    String identifier = request.usernameOrEmail().trim();
    User user =
        userRepository
            .findByUsernameOrEmail(identifier)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

    if (!passwordHasher.verify(request.password(), user.passwordHash())) {
      throw new InvalidCredentialsException("Invalid username or password");
    }

    AuthTokens tokens = tokenProvider.generateTokens(user);

    return new AuthResponse(
        tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(), UserDto.fromDomain(user));
  }

  @Override
  public AuthResponse refresh(RefreshTokenRequest request) {
    String userId = tokenProvider.extractUserIdFromRefreshToken(request.refreshToken());
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found for provided token"));

    AuthTokens tokens = tokenProvider.generateTokens(user);

    return new AuthResponse(
        tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(), UserDto.fromDomain(user));
  }
}
