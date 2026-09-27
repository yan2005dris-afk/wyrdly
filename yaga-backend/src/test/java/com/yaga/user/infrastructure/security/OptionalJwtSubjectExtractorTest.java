package com.yaga.user.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OptionalJwtSubjectExtractorTest {

  private JWTParser jwtParser;
  private OptionalJwtSubjectExtractor extractor;

  @BeforeEach
  void setUp() {
    jwtParser = mock(JWTParser.class);
    extractor = new OptionalJwtSubjectExtractor(jwtParser);
  }

  @Test
  void extractSubject_ReturnsNull_WhenHeaderIsNull() {
    assertNull(extractor.extractSubject(null));
  }

  @Test
  void extractSubject_ReturnsNull_WhenHeaderHasNoBearerPrefix() {
    assertNull(extractor.extractSubject("Basic abc123"));
  }

  @Test
  void extractSubject_ReturnsSubject_WhenTokenIsValid() throws ParseException {
    JsonWebToken token = mock(JsonWebToken.class);
    when(token.getSubject()).thenReturn("usr_123");
    when(jwtParser.parse("valid.token.value")).thenReturn(token);

    assertEquals("usr_123", extractor.extractSubject("Bearer valid.token.value"));
  }

  @Test
  void extractSubject_ReturnsNull_WhenTokenParsingFails() throws ParseException {
    when(jwtParser.parse("expired.token.value")).thenThrow(new ParseException("Token expired"));

    assertNull(extractor.extractSubject("Bearer expired.token.value"));
  }

  @Test
  void extractSubject_ReturnsNull_WhenSubjectClaimIsBlank() throws ParseException {
    JsonWebToken token = mock(JsonWebToken.class);
    when(token.getSubject()).thenReturn("  ");
    when(jwtParser.parse("valid.token.value")).thenReturn(token);

    assertNull(extractor.extractSubject("Bearer valid.token.value"));
  }
}
