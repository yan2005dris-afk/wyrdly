package com.yaga.auth.interfaces.rest;

import com.yaga.auth.application.dto.AuthResponse;
import com.yaga.auth.application.dto.LoginRequest;
import com.yaga.auth.application.dto.RefreshTokenRequest;
import com.yaga.auth.application.dto.RegisterRequest;
import com.yaga.auth.application.dto.UserDto;
import com.yaga.auth.application.usecase.AuthenticateUserUseCase;
import com.yaga.auth.application.usecase.RefreshTokenUseCase;
import com.yaga.auth.application.usecase.RegisterUserUseCase;
import com.yaga.auth.domain.exception.UserNotFoundException;
import com.yaga.auth.domain.repository.UserRepository;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Objects;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AuthResource {

  private final RegisterUserUseCase registerUserUseCase;
  private final AuthenticateUserUseCase authenticateUserUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final UserRepository userRepository;
  private final JsonWebToken jwt;

  @ConfigProperty(name = "yaga.auth.cookie.secure", defaultValue = "false")
  boolean cookieSecure;

  @ConfigProperty(name = "yaga.auth.cookie.same-site", defaultValue = "LAX")
  String cookieSameSite;

  @ConfigProperty(name = "yaga.auth.cookie.refresh-token-name", defaultValue = "refreshToken")
  String refreshTokenCookieName;

  @ConfigProperty(name = "yaga.auth.cookie.max-age", defaultValue = "604800")
  int cookieMaxAge;

  @Inject
  public AuthResource(
      RegisterUserUseCase registerUserUseCase,
      AuthenticateUserUseCase authenticateUserUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      UserRepository userRepository,
      JsonWebToken jwt) {
    this.registerUserUseCase =
        Objects.requireNonNull(registerUserUseCase, "registerUserUseCase must not be null");
    this.authenticateUserUseCase =
        Objects.requireNonNull(authenticateUserUseCase, "authenticateUserUseCase must not be null");
    this.refreshTokenUseCase =
        Objects.requireNonNull(refreshTokenUseCase, "refreshTokenUseCase must not be null");
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @POST
  @Path("/register")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response register(@Valid RegisterRequest request) {
    AuthResponse response = registerUserUseCase.register(request);
    NewCookie refreshCookie = buildRefreshTokenCookie(response.refreshToken());
    return Response.status(Response.Status.CREATED).cookie(refreshCookie).entity(response).build();
  }

  @POST
  @Path("/login")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response login(@Valid LoginRequest request) {
    AuthResponse response = authenticateUserUseCase.authenticate(request);
    NewCookie refreshCookie = buildRefreshTokenCookie(response.refreshToken());
    return Response.ok(response).cookie(refreshCookie).build();
  }

  @POST
  @Path("/refresh")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response refreshWithBody(
      @CookieParam("refreshToken") Cookie cookieToken, RefreshTokenRequest request) {
    String token =
        (request != null && request.refreshToken() != null && !request.refreshToken().isBlank())
            ? request.refreshToken()
            : (cookieToken != null ? cookieToken.getValue() : null);
    return executeRefresh(token);
  }

  @POST
  @Path("/refresh")
  public Response refreshWithCookie(@CookieParam("refreshToken") Cookie cookieToken) {
    String token = cookieToken != null ? cookieToken.getValue() : null;
    return executeRefresh(token);
  }

  private Response executeRefresh(String token) {
    if (token == null || token.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("message", "Refresh token is required"))
          .build();
    }

    AuthResponse response = refreshTokenUseCase.refresh(new RefreshTokenRequest(token));
    NewCookie refreshCookie = buildRefreshTokenCookie(response.refreshToken());
    return Response.ok(response).cookie(refreshCookie).build();
  }

  @POST
  @Path("/logout")
  public Response logout() {
    NewCookie clearCookie = buildClearRefreshTokenCookie();
    return Response.ok(Map.of("message", "Logged out successfully")).cookie(clearCookie).build();
  }

  @GET
  @Path("/me")
  @Authenticated
  public Response getCurrentUser() {
    String userId = jwt.getSubject();
    if (userId == null || userId.isBlank()) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    UserDto userDto =
        userRepository
            .findById(userId)
            .map(UserDto::fromDomain)
            .orElseThrow(() -> new UserNotFoundException("Current user not found"));

    return Response.ok(userDto).build();
  }

  private NewCookie buildRefreshTokenCookie(String refreshToken) {
    NewCookie.SameSite sameSite =
        "STRICT".equalsIgnoreCase(cookieSameSite)
            ? NewCookie.SameSite.STRICT
            : NewCookie.SameSite.LAX;

    return new NewCookie.Builder(refreshTokenCookieName)
        .value(refreshToken != null ? refreshToken : "")
        .path("/api/auth")
        .maxAge(cookieMaxAge)
        .secure(cookieSecure)
        .httpOnly(true)
        .sameSite(sameSite)
        .build();
  }

  private NewCookie buildClearRefreshTokenCookie() {
    NewCookie.SameSite sameSite =
        "STRICT".equalsIgnoreCase(cookieSameSite)
            ? NewCookie.SameSite.STRICT
            : NewCookie.SameSite.LAX;

    return new NewCookie.Builder(refreshTokenCookieName)
        .value("")
        .path("/api/auth")
        .maxAge(0)
        .secure(cookieSecure)
        .httpOnly(true)
        .sameSite(sameSite)
        .build();
  }
}
