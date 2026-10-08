package com.wyrdly.notifications.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class VapidJwtSignerTest {

  @Test
  void producesValidEs256JwtVerifiableBySamePublicKey() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
    g.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = g.generateKeyPair();

    String jwt =
        VapidJwtSigner.sign(
            "https://fcm.googleapis.com", "mailto:ops@wyrdly.com", pair.getPrivate());
    String[] parts = jwt.split("\\.");
    assertEquals(3, parts.length);

    // Header must be {"typ":"JWT","alg":"ES256"} base64url-encoded.
    String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    assertTrue(headerJson.contains("\"alg\":\"ES256\""), "header=" + headerJson);
    assertTrue(headerJson.contains("\"typ\":\"JWT\""), "header=" + headerJson);

    // Payload claims.
    String payloadJson =
        new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    assertTrue(payloadJson.contains("\"aud\":\"https://fcm.googleapis.com\""), payloadJson);
    assertTrue(payloadJson.contains("\"sub\":\"mailto:ops@wyrdly.com\""), payloadJson);
    long exp = extractLong(payloadJson, "exp");
    assertTrue(exp > Instant.now().getEpochSecond(), "exp must be in the future, was " + exp);

    // Signature must verify with the public key (DER → P1363 conversion is correct).
    byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
    assertEquals(64, signature.length, "ES256 P1363 signature must be 64 bytes");

    // Verify in P1363 form directly so the test doesn't depend on a hand-rolled DER encoder.
    Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
    verifier.initVerify(pair.getPublic());
    verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
    assertTrue(
        verifier.verify(signature),
        "Signature must verify against the public key (DER → P1363 conversion must be correct)");
  }

  @Test
  void rejectsBlankAudience() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
    g.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = g.generateKeyPair();
    assertThrows(
        IllegalArgumentException.class,
        () -> VapidJwtSigner.sign("", "mailto:ops@example.com", pair.getPrivate()));
  }

  @Test
  void rejectsBlankSubject() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
    g.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = g.generateKeyPair();
    assertThrows(
        IllegalArgumentException.class,
        () -> VapidJwtSigner.sign("https://example.com", "", pair.getPrivate()));
  }

  @Test
  void rejectsNullSigningKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> VapidJwtSigner.sign("https://example.com", "mailto:ops@example.com", null));
  }

  @Test
  void twoSignaturesForSameInputAreBothProduced() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
    g.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = g.generateKeyPair();
    String a =
        VapidJwtSigner.sign("https://example.com", "mailto:ops@example.com", pair.getPrivate());
    String b =
        VapidJwtSigner.sign("https://example.com", "mailto:ops@example.com", pair.getPrivate());
    assertFalse(a.isEmpty());
    assertFalse(b.isEmpty());
  }

  @Test
  void signatureVerifiesWithExternallyBuiltPublicKey() throws Exception {
    // Build the public key independently from raw coords to make sure we didn't depend
    // on identity tricks with the original KeyPair.
    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
    g.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = g.generateKeyPair();
    ECPublicKey pub = (ECPublicKey) pair.getPublic();
    byte[] uncompressed = new byte[65];
    uncompressed[0] = 0x04;
    System.arraycopy(toFixed32(pub.getW().getAffineX().toByteArray()), 0, uncompressed, 1, 32);
    System.arraycopy(toFixed32(pub.getW().getAffineY().toByteArray()), 0, uncompressed, 33, 32);
    java.security.AlgorithmParameters ap = java.security.AlgorithmParameters.getInstance("EC");
    ap.init(new java.security.spec.ECGenParameterSpec("secp256r1"));
    ECPublicKey rebuilt =
        (ECPublicKey)
            KeyFactory.getInstance("EC")
                .generatePublic(
                    new ECPublicKeySpec(
                        new ECPoint(
                            new java.math.BigInteger(1, uncompressed, 1, 32),
                            new java.math.BigInteger(1, uncompressed, 33, 32)),
                        ap.getParameterSpec(java.security.spec.ECParameterSpec.class)));

    String jwt =
        VapidJwtSigner.sign("https://example.com", "mailto:ops@example.com", pair.getPrivate());
    String[] parts = jwt.split("\\.");
    byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
    Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
    verifier.initVerify(rebuilt);
    verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
    assertTrue(verifier.verify(signature));
  }

  // ---- helpers -----------------------------------------------------------

  private static long extractLong(String json, String key) {
    int idx = json.indexOf("\"" + key + "\":");
    int start = idx + key.length() + 3;
    int end = start;
    while (end < json.length()
        && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
      end++;
    }
    return Long.parseLong(json.substring(start, end));
  }

  private static byte[] toFixed32(byte[] src) {
    if (src.length == 32) return src;
    byte[] dst = new byte[32];
    if (src.length > 32) System.arraycopy(src, src.length - 32, dst, 0, 32);
    else System.arraycopy(src, 0, dst, 32 - src.length, src.length);
    return dst;
  }
}
