package com.wyrdly.notifications.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * MessageEncryptor unit tests that validate the wire format (header layout per RFC 8291) without
 * depending on an external crypto library to decrypt. The {@link MessageEncryptor#encrypt} method's
 * main invariants are:
 *
 * <ul>
 *   <li>Length == 16 (salt) + 4 (record size) + 1 (idlen) + 65 (ephemeral pub) + ciphertext
 *   <li>recordSize == 4096 (single record)
 *   <li>idlen == 65 (uncompressed point)
 *   <li>first byte of the ephemeral pub == 0x04
 * </ul>
 */
class MessageEncryptorTest {

  @Test
  void emitsCorrectWireFormat() throws Exception {
    String peerPublicB64Url = browserKey();
    String authB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    byte[] plaintext = "{\"title\":\"hola\"}".getBytes(StandardCharsets.UTF_8);

    byte[] out = MessageEncryptor.encrypt(plaintext, peerPublicB64Url, authB64Url);

    assertNotNull(out);
    int minExpected = 16 + 4 + 1 + 65 + 16; // min ciphertext is the 16-byte GCM tag
    assertTrue(
        out.length >= minExpected,
        "ciphertext should be at least " + minExpected + " bytes, was " + out.length);

    ByteBuffer buf = ByteBuffer.wrap(out);
    byte[] salt = new byte[16];
    buf.get(salt);
    int rs = buf.getInt();
    byte idlen = buf.get();
    byte[] ephemeralPub = new byte[idlen & 0xff];
    buf.get(ephemeralPub);

    assertEquals(4096, rs, "recordSize must be 4096 (single record)");
    assertEquals(65, idlen, "idlen must be 65 (uncompressed point)");
    assertEquals((byte) 0x04, ephemeralPub[0], "ephemeral pub must start with 0x04");
    assertEquals(
        plaintext.length + 16,
        out.length - 16 - 4 - 1 - 65,
        "ciphertext+tag length must match plaintext + 16-byte GCM tag");
  }

  @Test
  void rejectsNonUncompressedPeerPublicKey() {
    String bad = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1, 2, 3});
    String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    assertThrows(
        IllegalArgumentException.class, () -> MessageEncryptor.encrypt("x".getBytes(), bad, auth));
  }

  @Test
  void rejectsAuthShorterThan16Bytes() throws Exception {
    String peerPublicB64Url = browserKey();
    String shortAuth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]);
    assertThrows(
        IllegalArgumentException.class,
        () -> MessageEncryptor.encrypt("x".getBytes(), peerPublicB64Url, shortAuth));
  }

  @Test
  void rejectsNullOrBlankInputs() {
    String validPub =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                new byte[65]); // 65 zeros — invalid but only checked after the null guards
    String validAuth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    assertThrows(
        IllegalArgumentException.class, () -> MessageEncryptor.encrypt(null, validPub, validAuth));
    assertThrows(
        IllegalArgumentException.class,
        () -> MessageEncryptor.encrypt("x".getBytes(), null, validAuth));
    assertThrows(
        IllegalArgumentException.class,
        () -> MessageEncryptor.encrypt("x".getBytes(), "  ", validAuth));
    assertThrows(
        IllegalArgumentException.class,
        () -> MessageEncryptor.encrypt("x".getBytes(), validPub, ""));
  }

  @Test
  void eachEncryptUsesFreshEphemeralKey() throws Exception {
    String peerPublicB64Url = browserKey();
    String authB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    byte[] a = MessageEncryptor.encrypt("hello".getBytes(), peerPublicB64Url, authB64Url);
    byte[] b = MessageEncryptor.encrypt("hello".getBytes(), peerPublicB64Url, authB64Url);
    // Ephemeral pub starts at byte 21 (16 salt + 4 rs + 1 idlen).
    byte[] pubA = java.util.Arrays.copyOfRange(a, 21, 21 + 65);
    byte[] pubB = java.util.Arrays.copyOfRange(b, 21, 21 + 65);
    assertFalse(pubA, pubB);
  }

  /** Helper: build a fresh browser-style EC P-256 public key (uncompressed, base64url). */
  private static String browserKey() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
    g.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = g.generateKeyPair();
    ECPublicKey pub = (ECPublicKey) pair.getPublic();
    byte[] uncompressed = new byte[65];
    uncompressed[0] = 0x04;
    System.arraycopy(toFixed32(pub.getW().getAffineX().toByteArray()), 0, uncompressed, 1, 32);
    System.arraycopy(toFixed32(pub.getW().getAffineY().toByteArray()), 0, uncompressed, 33, 32);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
  }

  private static byte[] toFixed32(byte[] src) {
    if (src.length == 32) return src;
    byte[] dst = new byte[32];
    if (src.length > 32) System.arraycopy(src, src.length - 32, dst, 0, 32);
    else System.arraycopy(src, 0, dst, 32 - src.length, src.length);
    return dst;
  }

  private static void assertFalse(byte[] a, byte[] b) {
    if (java.util.Arrays.equals(a, b)) {
      throw new AssertionError("expected ephemeral pub keys to differ across calls");
    }
  }
}
