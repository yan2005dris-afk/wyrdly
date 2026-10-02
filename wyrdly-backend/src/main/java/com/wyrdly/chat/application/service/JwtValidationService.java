package com.wyrdly.chat.application.service;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Servicio para validar JWT con verificación criptográfica completa usando SmallRye JWTParser. */
@ApplicationScoped
public class JwtValidationService {

  private static final Logger LOGGER = Logger.getLogger(JwtValidationService.class.getName());

  private final JWTParser jwtParser;

  @Inject
  public JwtValidationService(JWTParser jwtParser) {
    this.jwtParser = jwtParser;
  }

  /**
   * Valida criptográficamente el token JWT contra las claves públicas configuradas y extrae el
   * userId del claim 'sub'.
   *
   * @param token JWT token firmado
   * @return userId (subject) si el token es válido y no ha expirado, o null si es inválido
   */
  public String validateAndExtractUserId(String token) {
    if (token == null || token.isBlank()) {
      LOGGER.warning("JWT: token vacío o nulo");
      return null;
    }

    try {
      JsonWebToken parsedToken = jwtParser.parse(token);
      String subject = parsedToken.getSubject();
      if (subject == null || subject.isBlank()) {
        LOGGER.warning("JWT: claim 'sub' ausente o vacío");
        return null;
      }
      return subject;
    } catch (ParseException e) {
      LOGGER.warning("JWT: fallo en verificación criptográfica: " + e.getMessage());
      return null;
    } catch (Exception e) {
      LOGGER.warning("JWT: error inesperado en validación: " + e.getMessage());
      return null;
    }
  }
}
