package com.yaga.user.infrastructure.security;

import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Reads the subject claim of an optional bearer token without enforcing authentication. Used by
 * endpoints where auth is optional (e.g. GET /api/users/{username}): a missing, malformed or
 * expired token must never break a route that doesn't require one.
 */
@ApplicationScoped
public class OptionalJwtSubjectExtractor {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JWTParser jwtParser;

  @Inject
  public OptionalJwtSubjectExtractor(JWTParser jwtParser) {
    this.jwtParser = Objects.requireNonNull(jwtParser, "jwtParser must not be null");
  }

  public String extractSubject(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }
    try {
      String token = authorizationHeader.substring(BEARER_PREFIX.length());
      JsonWebToken parsed = jwtParser.parse(token);
      String subject = parsed.getSubject();
      return (subject == null || subject.isBlank()) ? null : subject;
    } catch (Exception e) {
      return null;
    }
  }
}
