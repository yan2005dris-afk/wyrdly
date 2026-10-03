package com.wyrdly.notifications.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * RFC 8292 (VAPID) JWT producer: ES256-signed token with {@code aud}, {@code exp}, and {@code sub}
 * claims. The header carries {@code typ:"JWT"} and {@code alg:"ES256"}.
 *
 * <p>The token is sent in the HTTP {@code Authorization} header as {@code "vapid
 * t=<jwt>,k=<publicKeyB64Url>"}.
 */
public final class VapidJwtSigner {

  private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(10);

  private VapidJwtSigner() {}

  /** Returns a VAPID JWT for the given {@code audience} (the Push Service origin). */
  public static String sign(String audience, String subject, PrivateKey signingKey) {
    return sign(audience, subject, signingKey, DEFAULT_EXPIRATION);
  }

  public static String sign(
      String audience, String subject, PrivateKey signingKey, Duration expiresIn) {
    if (audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("audience is required");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    if (signingKey == null) {
      throw new IllegalArgumentException("signingKey is required");
    }

    String header = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";
    long exp = Instant.now().plus(expiresIn).getEpochSecond();
    String payload =
        "{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}";

    String headerB64 = base64Url(header.getBytes(StandardCharsets.UTF_8));
    String payloadB64 = base64Url(payload.getBytes(StandardCharsets.UTF_8));
    String signingInput = headerB64 + "." + payloadB64;

    byte[] signature;
    try {
      Signature signer = Signature.getInstance("SHA256withECDSA");
      signer.initSign(signingKey);
      signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
      signature = signer.sign();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign VAPID JWT", e);
    }

    // JWS expects the signature in the IEEE P1363 form (r || s), 64 bytes for P-256.
    // Java's SHA256withECDSA returns ASN.1 DER, which RFC 7515 §3.1 explicitly rejects.
    // Convert DER → P1363 if needed.
    byte[] p1363 = derToP1363(signature);
    return signingInput + "." + base64Url(p1363);
  }

  private static byte[] derToP1363(byte[] der) {
    // Expecting SEQUENCE { INTEGER r, INTEGER s }
    if (der.length < 8 || der[0] != 0x30) {
      throw new IllegalArgumentException("Unexpected DER signature header");
    }
    int offset = 2; // skip SEQUENCE + length
    // INTEGER r
    if (der[offset++] != 0x02) throw new IllegalArgumentException("Expected INTEGER r");
    int rLen = der[offset++] & 0xff;
    byte[] rBytes = new byte[rLen];
    System.arraycopy(der, offset, rBytes, 0, rLen);
    offset += rLen;
    // INTEGER s
    if (der[offset++] != 0x02) throw new IllegalArgumentException("Expected INTEGER s");
    int sLen = der[offset++] & 0xff;
    byte[] sBytes = new byte[sLen];
    System.arraycopy(der, offset, sBytes, 0, sLen);

    return concat(toFixed32(rBytes), toFixed32(sBytes));
  }

  private static byte[] toFixed32(byte[] src) {
    if (src.length == 32) return src;
    byte[] dst = new byte[32];
    if (src.length > 32) System.arraycopy(src, src.length - 32, dst, 0, 32);
    else System.arraycopy(src, 0, dst, 32 - src.length, src.length);
    return dst;
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  private static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
