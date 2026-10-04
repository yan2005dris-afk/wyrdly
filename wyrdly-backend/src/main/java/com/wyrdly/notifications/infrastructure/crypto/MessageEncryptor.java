package com.wyrdly.notifications.infrastructure.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 8291 (Message Encryption for Web Push) implementation.
 *
 * <p>Generates an ephemeral ECDH key pair, derives the AES-128 content encryption key and 96-bit
 * GCM nonce via HKDF over the ECDH shared secret (per RFC 8291 §3), and emits the standard wire
 * format:
 *
 * <pre>
 *   salt (16 bytes) || recordSize (4 bytes, big-endian) || idlen (1 byte) ||
 *   ephemeralPublicKey (65 bytes uncompressed) || ciphertext || 16-byte GCM tag
 * </pre>
 *
 * <p>This file is intentionally self-contained: no third-party crypto library is needed.
 */
public final class MessageEncryptor {

  private static final int RECORD_SIZE = 4096;
  private static final String CURVE = VapidKeyProvider.EC_CURVE_NAME;

  private MessageEncryptor() {}

  /**
   * Encrypts {@code plaintext} for the recipient identified by {@code peerPublicKeyB64Url} and
   * {@code authB64Url} (both base64url-encoded).
   */
  public static byte[] encrypt(byte[] plaintext, String peerPublicKeyB64Url, String authB64Url)
      throws Exception {
    if (plaintext == null) throw new IllegalArgumentException("plaintext must not be null");
    if (peerPublicKeyB64Url == null || peerPublicKeyB64Url.isBlank()) {
      throw new IllegalArgumentException("peerPublicKeyB64Url is required");
    }
    if (authB64Url == null || authB64Url.isBlank()) {
      throw new IllegalArgumentException("authB64Url is required");
    }

    byte[] peerPublicRaw = Base64.getUrlDecoder().decode(peerPublicKeyB64Url);
    if (peerPublicRaw.length != 65 || peerPublicRaw[0] != 0x04) {
      throw new IllegalArgumentException(
          "peer public key must be 65 bytes uncompressed EC point (got "
              + peerPublicRaw.length
              + ")");
    }
    byte[] authSecret = Base64.getUrlDecoder().decode(authB64Url);
    if (authSecret.length < 16) {
      throw new IllegalArgumentException(
          "auth secret must be at least 16 bytes (got " + authSecret.length + ")");
    }

    // Ephemeral ECDH key pair on the same curve as VAPID.
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec(CURVE));
    KeyPair ephemeral = generator.generateKeyPair();
    byte[] ephemeralPublicRaw = encodeUncompressedPoint((ECPublicKey) ephemeral.getPublic());

    // Shared secret via ECDH between ephemeral private and peer public.
    ECParameterSpec ecSpec = ecParameterSpec();
    ECPublicKey peerPublic = peerPublicKey(peerPublicRaw, ecSpec);
    KeyAgreement ka = KeyAgreement.getInstance("ECDH");
    ka.init(ephemeral.getPrivate());
    ka.doPhase(peerPublic, true);
    byte[] sharedSecret = ka.generateSecret();

    // RFC 8291 §3.2 / §3.3: PRK_key = HMAC(auth_secret, ecdh_secret), then
    // key_info = "WebPush: info\\0" || ua_public || as_public; cek_info and nonce_info
    // append "Content-Encoding: aes128gcm\\0" and "Content-Encoding: nonce\\0" respectively.
    byte[] prk = hmac(authSecret, sharedSecret);
    byte[] baseInfo =
        concat(
            "WebPush: info\\0".getBytes(StandardCharsets.UTF_8),
            concat(peerPublicRaw, ephemeralPublicRaw));
    byte[] cekInfo =
        concat("Content-Encoding: aes128gcm\\0".getBytes(StandardCharsets.UTF_8), baseInfo);
    byte[] nonceInfo =
        concat("Content-Encoding: nonce\\0".getBytes(StandardCharsets.UTF_8), baseInfo);
    byte[] cek = hkdfExpand(prk, cekInfo, 16);
    byte[] nonce = hkdfExpand(prk, nonceInfo, 12);

    // AES-128-GCM with AAD = ephemeralPublicRaw (the receiver needs this to derive the
    // shared secret on its side).
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
    cipher.updateAAD(ephemeralPublicRaw);
    byte[] ciphertextAndTag = cipher.doFinal(plaintext);

    // 16-byte salt for the receiver's HKDF (salt is unused for the actual encryption
    // since we already derived PRK from auth_secret, but the RFC requires this field
    // to be present so receivers can locate the ephemeral public key block).
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);

    return ByteBuffer.allocate(16 + 4 + 1 + 65 + ciphertextAndTag.length)
        .put(salt)
        .putInt(RECORD_SIZE)
        .put((byte) 65)
        .put(ephemeralPublicRaw)
        .put(ciphertextAndTag)
        .array();
  }

  private static byte[] hmac(byte[] key, byte[] data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data);
  }

  private static byte[] hkdfExpand(byte[] prk, byte[] info, int outLen) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(prk, "HmacSHA256"));
    mac.update(info);
    mac.update((byte) 0x01);
    byte[] t = mac.doFinal();
    byte[] out = new byte[outLen];
    System.arraycopy(t, 0, out, 0, Math.min(outLen, t.length));
    return out;
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  private static ECParameterSpec ecParameterSpec() throws Exception {
    AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
    ap.init(new ECGenParameterSpec(CURVE));
    return ap.getParameterSpec(ECParameterSpec.class);
  }

  private static ECPublicKey peerPublicKey(byte[] uncompressed, ECParameterSpec ecSpec)
      throws Exception {
    BigInteger x = new BigInteger(1, uncompressed, 1, 32);
    BigInteger y = new BigInteger(1, uncompressed, 33, 32);
    ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(x, y), ecSpec);
    return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
  }

  private static byte[] encodeUncompressedPoint(ECPublicKey publicKey) {
    BigInteger x = publicKey.getW().getAffineX();
    BigInteger y = publicKey.getW().getAffineY();
    byte[] xBytes = toFixed32(x.toByteArray());
    byte[] yBytes = toFixed32(y.toByteArray());
    byte[] result = new byte[65];
    result[0] = 0x04;
    System.arraycopy(xBytes, 0, result, 1, 32);
    System.arraycopy(yBytes, 0, result, 33, 32);
    return result;
  }

  private static byte[] toFixed32(byte[] src) {
    if (src.length == 32) return src;
    byte[] dst = new byte[32];
    if (src.length > 32) System.arraycopy(src, src.length - 32, dst, 0, 32);
    else System.arraycopy(src, 0, dst, 32 - src.length, src.length);
    return dst;
  }
}
