package com.wyrdly.notifications.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wyrdly.notifications.domain.model.VapidKeyPair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link VapidKeyProvider}. We drive the provider directly via its package-private
 * {@code invokeLoadOrGenerate()} so we can test both the missing-files (generate) and present-files
 * (load) paths deterministically without standing up the full Quarkus CDI container.
 */
class VapidKeyProviderTest {

  private VapidKeyProvider newProvider(Path publicKey, Path privateKey, boolean generate) {
    return new VapidKeyProvider(publicKey.toString(), privateKey.toString(), generate);
  }

  // ---- File missing -> generate on startup ------------------------------

  @Test
  void generatesFreshPairWhenFilesMissing(@TempDir Path workDir) throws Exception {
    Path pub = workDir.resolve("publicKey.txt");
    Path priv = workDir.resolve("privateKey.txt");
    VapidKeyProvider provider = newProvider(pub, priv, true);

    VapidKeyPair pair = provider.invokeLoadOrGenerate();

    assertNotNull(pair);
    assertEquals(87, pair.publicKey().length(), "public key must be 87 chars base64url");
    assertTrue(
        pair.privateKey().length() >= 80 && pair.privateKey().length() <= 200,
        "private key must be 80-200 chars base64url depending on PKCS8 variant (got "
            + pair.privateKey().length()
            + ")");
    assertTrue(
        provider.areKeysPersisted(), "files should have been persisted to disk after generation");
    assertEquals(pair.publicKey(), Files.readString(pub));
    assertEquals(pair.privateKey(), Files.readString(priv));
  }

  @Test
  void generatedKeysRoundTripViaEcdsa(@TempDir Path workDir) throws Exception {
    Path pub = workDir.resolve("publicKey.txt");
    Path priv = workDir.resolve("privateKey.txt");
    VapidKeyProvider provider = newProvider(pub, priv, true);

    VapidKeyPair pair = provider.invokeLoadOrGenerate();
    assertTrue(
        VapidKeyProvider.verifyPair(pair),
        "freshly generated keys must verify via SHA256withECDSA round-trip");
  }

  @Test
  void uncompressedPointStartsWithMagicByte(@TempDir Path workDir) throws Exception {
    Path pub = workDir.resolve("publicKey.txt");
    Path priv = workDir.resolve("privateKey.txt");
    VapidKeyProvider provider = newProvider(pub, priv, true);

    VapidKeyPair pair = provider.invokeLoadOrGenerate();
    byte[] raw = Base64.getUrlDecoder().decode(pair.publicKey());
    assertEquals(65, raw.length, "uncompressed point must be 65 bytes");
    assertEquals((byte) 0x04, raw[0], "first byte must be 0x04 (ANSI X9.62 uncompressed tag)");
  }

  // ---- Files present -> load without regenerating ------------------------

  @Test
  void loadsExistingKeysWithoutRegenerating(@TempDir Path workDir) throws Exception {
    Path pub = workDir.resolve("publicKey.txt");
    Path priv = workDir.resolve("privateKey.txt");
    VapidKeyPair preset = VapidKeyProvider.generateKeyPair();
    Files.writeString(pub, preset.publicKey());
    Files.writeString(priv, preset.privateKey());

    VapidKeyProvider provider = newProvider(pub, priv, true);
    VapidKeyPair loaded = provider.invokeLoadOrGenerate();

    assertEquals(preset.publicKey(), loaded.publicKey(), "must load the existing public key as-is");
    assertEquals(
        preset.privateKey(), loaded.privateKey(), "must load the existing private key as-is");
    assertTrue(VapidKeyProvider.verifyPair(loaded));
  }

  // ---- generate-if-missing=false and files missing -> fail fast ----------

  @Test
  void failsFastWhenGenerationDisabledAndFilesMissing(@TempDir Path workDir) {
    Path pub = workDir.resolve("publicKey.txt");
    Path priv = workDir.resolve("privateKey.txt");
    VapidKeyProvider provider = newProvider(pub, priv, false);

    Exception ex = assertThrows(Exception.class, provider::invokeLoadOrGenerate);
    assertTrue(
        ex.getMessage().toLowerCase().contains("vapid")
            && (ex.getMessage().contains("not found")
                || ex.getMessage().contains("generate-if-missing")),
        "error message should explain why, was: " + ex.getMessage());
  }

  // ---- Format / validity guards ------------------------------------------

  @Test
  void v4KeyPairRejectsBlankValues() {
    assertThrows(IllegalArgumentException.class, () -> new VapidKeyPair("", "priv"));
    assertThrows(IllegalArgumentException.class, () -> new VapidKeyPair("pub", "  "));
    assertThrows(NullPointerException.class, () -> new VapidKeyPair(null, "priv"));
    assertThrows(NullPointerException.class, () -> new VapidKeyPair("pub", null));
  }

  @Test
  void getKeyPairThrowsIfNotYetInitialized() {
    VapidKeyProvider fresh =
        newProvider(Path.of("/nonexistent/a.txt"), Path.of("/nonexistent/b.txt"), false);
    IllegalStateException ex = assertThrows(IllegalStateException.class, fresh::getKeyPair);
    assertTrue(
        ex.getMessage().toLowerCase().contains("not yet initialized"),
        "message should explain why, was: " + ex.getMessage());
  }

  @Test
  void areKeysPersistedReflectsFilesystem(@TempDir Path workDir) throws Exception {
    Path pub = workDir.resolve("publicKey.txt");
    Path priv = workDir.resolve("privateKey.txt");
    VapidKeyProvider provider = newProvider(pub, priv, true);
    provider.invokeLoadOrGenerate();

    assertTrue(provider.areKeysPersisted());
    Files.delete(pub);
    assertFalse(
        provider.areKeysPersisted(), "deleting public key file must make areKeysPersisted() false");
  }

  @Test
  void emptyFilesFallBackToRegenerationWhenAllowed(@TempDir Path workDir) throws Exception {
    Path pub = workDir.resolve("publicKey.txt");
    Path priv = workDir.resolve("privateKey.txt");
    Files.writeString(pub, "");
    Files.writeString(priv, "");

    VapidKeyProvider provider = newProvider(pub, priv, true);
    VapidKeyPair pair = provider.invokeLoadOrGenerate();
    assertNotNull(pair);
    assertEquals(87, pair.publicKey().length());
    assertTrue(provider.areKeysPersisted());
  }
}
