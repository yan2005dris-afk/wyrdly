package com.wyrdly.notifications.infrastructure.crypto;

import com.wyrdly.notifications.domain.model.VapidKeyPair;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Loads (and on-demand generates) the Web Push VAPID key pair from the filesystem.
 *
 * <p>Behavior:
 *
 * <ol>
 *   <li>If both configured files exist and are non-empty, they are loaded as-is. No regeneration.
 *   <li>If {@code wyrdly.push.vapid.generate-if-missing} is true (default) and at least one file is
 *       missing, a fresh P-256 key pair is generated, written to disk atomically, and exposed for
 *       the rest of the bounded context.
 *   <li>If generation is disabled and the files are missing, startup fails fast.
 * </ol>
 *
 * <p>This bean is initialized on {@link StartupEvent} so subsequent consumers (the push dispatcher,
 * subscription resource, health endpoints) can rely on a non-null {@link VapidKeyPair}.
 */
@ApplicationScoped
public class VapidKeyProvider {

  /** NIST P-256 / secp256r1 / prime256v1 — three aliases for the same curve. */
  static final String EC_CURVE_NAME = "secp256r1";

  private static final Logger LOG = Logger.getLogger(VapidKeyProvider.class);

  private final Path publicKeyPath;
  private final Path privateKeyPath;
  private final boolean generateIfMissing;

  private volatile VapidKeyPair keyPair;

  public VapidKeyProvider(
      @ConfigProperty(
              name = "wyrdly.push.vapid.public-key-location",
              defaultValue = "vapid/publicKey.txt")
          String publicKeyLocation,
      @ConfigProperty(
              name = "wyrdly.push.vapid.private-key-location",
              defaultValue = "vapid/privateKey.txt")
          String privateKeyLocation,
      @ConfigProperty(name = "wyrdly.push.vapid.generate-if-missing", defaultValue = "true")
          boolean generateIfMissing) {
    this.publicKeyPath = Path.of(publicKeyLocation);
    this.privateKeyPath = Path.of(privateKeyLocation);
    this.generateIfMissing = generateIfMissing;
  }

  void onStart(@Observes StartupEvent event) {
    try {
      this.keyPair = loadOrGenerate();
      LOG.infof(
          "VAPID key pair ready (public=%s, private=%s)",
          publicKeyPath.toAbsolutePath(), privateKeyPath.toAbsolutePath());
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Failed to initialize VAPID key pair (public="
              + publicKeyPath.toAbsolutePath()
              + ", private="
              + privateKeyPath.toAbsolutePath()
              + ")",
          ex);
    }
  }

  /** Package-private accessor for the load/generate flow so unit tests can drive it directly. */
  VapidKeyPair invokeLoadOrGenerate() throws Exception {
    VapidKeyPair result = loadOrGenerate();
    this.keyPair = result;
    return result;
  }

  /**
   * Returns the loaded VAPID key pair. Throws if the provider was not yet started (which should not
   * happen in a normal Quarkus lifecycle).
   */
  public VapidKeyPair getKeyPair() {
    VapidKeyPair snapshot = keyPair;
    if (snapshot == null) {
      throw new IllegalStateException("VapidKeyProvider not yet initialized");
    }
    return snapshot;
  }

  public String getPublicKey() {
    return getKeyPair().publicKey();
  }

  public String getPrivateKey() {
    return getKeyPair().privateKey();
  }

  /** True when both key files are present on disk. Used by health checks. */
  public boolean areKeysPersisted() {
    return Files.isRegularFile(publicKeyPath) && Files.isRegularFile(privateKeyPath);
  }

  // ---- internals ---------------------------------------------------------

  VapidKeyPair loadOrGenerate() throws Exception {
    if (Files.isRegularFile(publicKeyPath) && Files.isRegularFile(privateKeyPath)) {
      String publicKey = Files.readString(publicKeyPath, StandardCharsets.UTF_8).trim();
      String privateKey = Files.readString(privateKeyPath, StandardCharsets.UTF_8).trim();
      if (!publicKey.isEmpty() && !privateKey.isEmpty()) {
        return new VapidKeyPair(publicKey, privateKey);
      }
      LOG.warnf(
          "VAPID key files exist but are empty: public=%s private=%s",
          publicKeyPath, privateKeyPath);
    }

    if (!generateIfMissing) {
      throw new IllegalStateException(
          "VAPID keys not found and wyrdly.push.vapid.generate-if-missing=false. "
              + "Run scripts/init-vapid-keys.sh or mount a volume with the keys.");
    }

    LOG.warnf(
        "VAPID keys missing — generating fresh P-256 pair at %s / %s",
        publicKeyPath, privateKeyPath);
    VapidKeyPair generated = generateKeyPair();
    writeAtomically(publicKeyPath, generated.publicKey());
    writeAtomically(privateKeyPath, generated.privateKey());
    return generated;
  }

public static VapidKeyPair generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec(EC_CURVE_NAME));
    KeyPair pair = generator.generateKeyPair();

    ECPublicKey ecPublic = (ECPublicKey) pair.getPublic();
    byte[] publicUncompressed = encodeUncompressedPoint(ecPublic);
    String publicKeyB64 =
        Base64.getUrlEncoder().withoutPadding().encodeToString(publicUncompressed);

    byte[] privatePkcs8 = pair.getPrivate().getEncoded();
    String privateKeyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(privatePkcs8);

    return new VapidKeyPair(publicKeyB64, privateKeyB64);
  }

  private static byte[] encodeUncompressedPoint(ECPublicKey publicKey) {
    ECPoint w = publicKey.getW();
    byte[] x = toFixed32(w.getAffineX().toByteArray());
    byte[] y = toFixed32(w.getAffineY().toByteArray());
    byte[] result = new byte[65];
    result[0] = 0x04; // uncompressed point tag
    System.arraycopy(x, 0, result, 1, 32);
    System.arraycopy(y, 0, result, 33, 32);
    return result;
  }

  private static byte[] toFixed32(byte[] src) {
    if (src.length == 32) return src;
    byte[] dst = new byte[32];
    if (src.length > 32) {
      // strip leading sign byte
      System.arraycopy(src, src.length - 32, dst, 0, 32);
    } else {
      System.arraycopy(src, 0, dst, 32 - src.length, src.length);
    }
    return dst;
  }

  /** Used in tests to verify round-trip after loading. Package-private on purpose. */
  static boolean verifyPair(VapidKeyPair pair) {
    // Decoding smoke test: round-trip via JCA.
    byte[] pubBytes = Base64.getUrlDecoder().decode(pair.publicKey());
    byte[] privBytes = Base64.getUrlDecoder().decode(pair.privateKey());
    if (pubBytes.length != 65 || pubBytes[0] != 0x04) return false;
    BigInteger x = new BigInteger(1, pubBytes, 1, 32);
    BigInteger y = new BigInteger(1, pubBytes, 33, 32);
    try {
      // Build an ECParameterSpec from the curve name via AlgorithmParameters
      // — portable and avoids sun.* internals.
      java.security.AlgorithmParameters ap = java.security.AlgorithmParameters.getInstance("EC");
      ap.init(new ECGenParameterSpec(EC_CURVE_NAME));
      java.security.spec.ECParameterSpec ecSpec =
          ap.getParameterSpec(java.security.spec.ECParameterSpec.class);

      KeyFactory kf = KeyFactory.getInstance("EC");
      ECPublicKeySpec pubSpec = new ECPublicKeySpec(new ECPoint(x, y), ecSpec);
      PublicKey pub = kf.generatePublic(pubSpec);
      java.security.interfaces.ECPrivateKey priv =
          (java.security.interfaces.ECPrivateKey)
              kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(privBytes));
      // Self-verify with a tiny ECDSA signature.
      java.security.Signature sig = java.security.Signature.getInstance("SHA256withECDSA");
      sig.initSign(priv);
      sig.update("vapid-self-test".getBytes(StandardCharsets.UTF_8));
      byte[] signature = sig.sign();
      sig.initVerify(pub);
      sig.update("vapid-self-test".getBytes(StandardCharsets.UTF_8));
      return sig.verify(signature);
    } catch (Exception e) {
      return false;
    }
  }

  private static void writeAtomically(Path target, String content) throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path tmp = Files.createTempFile(parent, "vapid-", ".tmp");
    try {
      Files.writeString(tmp, content, StandardCharsets.UTF_8);
      try {
        Files.move(
            tmp,
            target,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
      }
    }
  }

  // For tests / diagnostics.
  Path getPublicKeyPath() {
    return publicKeyPath;
  }

  Path getPrivateKeyPath() {
    return privateKeyPath;
  }
}
