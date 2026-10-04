package com.wyrdly.notifications.domain.model;

import java.util.Objects;

/**
 * Immutable Web Push VAPID key pair (RFC 8292).
 *
 * <p>Both {@code publicKey} and {@code privateKey} are base64url-encoded without padding:
 *
 * <ul>
 *   <li>{@code publicKey}: uncompressed EC point per ANSI X9.62 — {@code 0x04 || X (32 bytes) || Y
 *       (32 bytes)} = 65 bytes raw, 87 chars base64url.
 *   <li>{@code privateKey}: PKCS#8 encoded {@code ECPrivateKey} ASN.1 structure, base64url.
 * </ul>
 *
 * <p>This is a pure value object with no behavior; persistence and signing live in the
 * infrastructure layer.
 */
public record VapidKeyPair(String publicKey, String privateKey) {

  public VapidKeyPair {
    Objects.requireNonNull(publicKey, "publicKey must not be null");
    Objects.requireNonNull(privateKey, "privateKey must not be null");
    if (publicKey.isBlank()) {
      throw new IllegalArgumentException("publicKey must not be blank");
    }
    if (privateKey.isBlank()) {
      throw new IllegalArgumentException("privateKey must not be blank");
    }
  }
}
