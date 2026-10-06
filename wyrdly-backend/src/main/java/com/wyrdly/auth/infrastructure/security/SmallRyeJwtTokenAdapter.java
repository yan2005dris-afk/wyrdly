package com.wyrdly.auth.infrastructure.security;

import com.wyrdly.auth.application.port.TokenProviderPort;
import com.wyrdly.auth.domain.exception.InvalidTokenException;
import com.wyrdly.auth.domain.model.AuthTokens;
import com.wyrdly.auth.domain.model.User;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SmallRyeJwtTokenAdapter implements TokenProviderPort {

  private static final Logger LOG = Logger.getLogger(SmallRyeJwtTokenAdapter.class);

  private static final Duration ACCESS_TOKEN_EXPIRATION = Duration.ofMinutes(15);
  private static final Duration REFRESH_TOKEN_EXPIRATION = Duration.ofDays(7);

  private final String issuer;
  private final JWTParser jwtParser;
  private final PrivateKey signingKey;

  @Inject
  public SmallRyeJwtTokenAdapter(
      @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "https://wyrdly.com/issuer")
          String issuer,
      @ConfigProperty(name = "smallrye.jwt.sign.key.location", defaultValue = "jwt/privateKey.pem")
          String privateKeyLocation,
      JWTParser jwtParser) {
    this.issuer = issuer;
    this.jwtParser = jwtParser;
    this.signingKey = loadPrivateKey(privateKeyLocation);
  }

  /**
   * Loads the JWT signing private key from {@code location}.
   *
   * <p>Supported forms:
   *
   * <ul>
   *   <li>Absolute filesystem path: {@code /deployments/jwt/privateKey.pem}
   *   <li>{@code file://} URL with absolute path: {@code file:///deployments/jwt/privateKey.pem}
   *       (preferred for prod — matches {@code mp.jwt.verify.publickey.location} conventions).
   *   <li>Classpath-relative path: {@code jwt/privateKey.pem} (used in tests via {@code
   *       JwtKeyGenerator}).
   * </ul>
   *
   * <p>Failures fail fast with an {@link IllegalStateException} whose message includes the
   * configured location. Previously this method had four silent {@code catch (Exception ignored)}
   * fallbacks that swallowed the real cause and surfaced an NPE deep inside SmallRye's {@code
   * KeyUtils.readPrivateKey}, making the deploy cascade failure very hard to trace.
   */
  private PrivateKey loadPrivateKey(String location) {
    Path path = resolveKeyLocation(location);
    if (path == null) {
      throw new IllegalStateException(
          "Could not resolve JWT signing private key location: " + location);
    }
    if (!Files.exists(path)) {
      throw new IllegalStateException(
          "JWT signing private key file does not exist at: "
              + path
              + " (configured location: "
              + location
              + ")");
    }
    try {
      String pem = Files.readString(path);
      return KeyUtils.decodePrivateKey(pem);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to read or decode JWT signing private key from: "
              + path
              + " (configured location: "
              + location
              + ")",
          e);
    }
  }

  /**
   * Resolves a JWT key {@code location} config value to a filesystem {@link Path}.
   *
   * <p>Order:
   *
   * <ol>
   *   <li>{@code file:} or {@code file://} prefix → strip scheme, treat as absolute path.
   *   <li>Absolute filesystem path → return as-is.
   *   <li>Relative path that exists in the current working dir → return absolute version.
   *   <li>Classpath resource lookup → return the file URL as a {@link Path}.
   * </ol>
   *
   * Returns {@code null} if none of the above resolve.
   */
  private Path resolveKeyLocation(String location) {
    // 1. file:// URL → strip scheme prefix and treat as absolute path.
    //    Accepts both "file:///abs/path" and "file:/abs/path" forms.
    if (location.startsWith("file:")) {
      String stripped = location.substring("file:".length());
      while (stripped.startsWith("//")) {
        stripped = stripped.substring(1);
      }
      return Paths.get(stripped);
    }

    // 2. Absolute filesystem path.
    Path direct = Paths.get(location);
    if (direct.isAbsolute()) {
      return direct;
    }

    // 3. Relative path that resolves in the working directory.
    Path absolute = direct.toAbsolutePath();
    if (Files.exists(absolute)) {
      return absolute;
    }

    // 4. Classpath resource (default "jwt/privateKey.pem" for tests/dev).
    URL classpathUrl = Thread.currentThread().getContextClassLoader().getResource(location);
    if (classpathUrl != null) {
      try {
        return Paths.get(classpathUrl.toURI());
      } catch (URISyntaxException e) {
        LOG.warnf("Could not convert to Path (was %s): %s", classpathUrl, e.getMessage());
      }
    }

    LOG.warnf("Could not resolve JWT key location: %s", location);
    return null;
  }

  @Override
  public AuthTokens generateTokens(User user) {
    String accessToken =
        Jwt.issuer(issuer)
            .upn(user.username())
            .subject(user.id())
            .groups(Set.of("User"))
            .claim("email", user.email())
            .claim("fullName", user.fullName())
            .claim("type", "access")
            .expiresIn(ACCESS_TOKEN_EXPIRATION)
            .sign(signingKey);

    String refreshToken =
        Jwt.issuer(issuer)
            .upn(user.username())
            .subject(user.id())
            .claim("type", "refresh")
            .expiresIn(REFRESH_TOKEN_EXPIRATION)
            .sign(signingKey);

    return new AuthTokens(accessToken, refreshToken, ACCESS_TOKEN_EXPIRATION.toSeconds());
  }

  @Override
  public String extractUserIdFromRefreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new InvalidTokenException("Refresh token cannot be null or empty");
    }
    try {
      JsonWebToken jwt = jwtParser.parse(refreshToken);
      String tokenType = jwt.getClaim("type");
      if (!"refresh".equals(tokenType)) {
        throw new InvalidTokenException("Invalid token type: expected refresh token");
      }
      String subject = jwt.getSubject();
      if (subject == null || subject.isBlank()) {
        throw new InvalidTokenException("Invalid token: missing subject claim");
      }
      return subject;
    } catch (ParseException e) {
      throw new InvalidTokenException("Failed to verify refresh token: " + e.getMessage());
    }
  }
}
