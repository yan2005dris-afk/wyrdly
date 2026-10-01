package com.yaga.chat.application.service;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;

@Mock
@ApplicationScoped
public class JwtValidationServiceTestMock {

  private static final Logger LOGGER = Logger.getLogger(JwtValidationServiceTestMock.class.getName());

  public String validateAndExtractUserId(String token) {
    if (token == null || token.isEmpty()) {
      LOGGER.warning("JWT: token vacío");
      return null;
    }

    // Para tests, tokens son simples: "user123" o "user-id"
    if (token.startsWith("invalid")) {
      LOGGER.warning("JWT: token inválido");
      return null;
    }

    String userId = token.replace("token-", "");
    LOGGER.fine("JWT test mock validated for userId: " + userId);
    return userId;
  }
}
