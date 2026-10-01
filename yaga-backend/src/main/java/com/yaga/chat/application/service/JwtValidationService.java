package com.yaga.chat.application.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class JwtValidationService {

  private static final Logger LOGGER =
      Logger.getLogger(JwtValidationService.class.getName());

  @Inject
  @ConfigProperty(name = "mp.jwt.verify.publickey.location")
  String jwtPublicKeyLocation;

  @Inject
  @ConfigProperty(name = "mp.jwt.verify.issuer")
  String expectedIssuer;

  public String validateAndExtractUserId(String token) {
    try {
      if (token == null || token.isEmpty()) {
        LOGGER.warning("JWT validation failed: token is empty");
        return null;
      }

      PublicKey key = loadPublicKey();
      if (key == null) {
        LOGGER.severe("JWT public key not available");
        return null;
      }

      Claims claims = Jwts.parser()
          .verifyWith(key)
          .requireIssuer(expectedIssuer)
          .build()
          .parseSignedClaims(token)
          .getPayload();

      String userId = claims.getSubject();
      if (userId == null || userId.isEmpty()) {
        LOGGER.warning("JWT validation failed: missing 'sub' claim");
        return null;
      }

      LOGGER.fine("JWT validated successfully for userId: " + userId);
      return userId;

    } catch (JwtException e) {
      LOGGER.warning("JWT validation failed: " + e.getMessage());
      return null;
    } catch (Exception e) {
      LOGGER.warning("JWT validation error: " + e.getMessage());
      return null;
    }
  }

  private PublicKey loadPublicKey() {
    try {
      String keyPath = jwtPublicKeyLocation;
      if (keyPath.startsWith("classpath:")) {
        keyPath = keyPath.replace("classpath:", "");
      }

      byte[] keyBytes;
      try (var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(keyPath)) {
        if (is == null) {
          LOGGER.warning("JWT public key not found in classpath: " + keyPath);
          return null;
        }
        keyBytes = is.readAllBytes();
      }

      String keyContent = new String(keyBytes)
          .replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replaceAll("\\s", "");

      byte[] decodedKey = java.util.Base64.getDecoder().decode(keyContent);
      X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePublic(spec);
    } catch (Exception e) {
      LOGGER.warning("Error loading JWT public key: " + e.getMessage());
      return null;
    }
  }
}
