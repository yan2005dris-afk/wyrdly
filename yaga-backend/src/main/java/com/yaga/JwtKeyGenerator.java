package com.yaga;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Generates RSA JWT key pair if it doesn't exist.
 *
 * <p>Invoked during Maven's process-test-classes phase to ensure JWT signing/verification keys
 * exist before tests run. Uses only JDK built-ins (KeyPairGenerator).
 *
 * <p>Keys are generated in PKCS#8 (private) and X.509 (public) PEM formats. This ensures tests
 * always have valid credentials, and keys are NEVER committed to repo. Production keys are
 * generated separately via CI/deployment scripts.
 */
public class JwtKeyGenerator {

  private static final String PRIVATE_KEY_PATH = "src/main/resources/jwt/privateKey.pem";
  private static final String PUBLIC_KEY_PATH = "src/main/resources/jwt/publicKey.pem";

  public static void main(String[] args) throws Exception {
    if (Files.exists(Paths.get(PRIVATE_KEY_PATH)) && Files.exists(Paths.get(PUBLIC_KEY_PATH))) {
      System.out.println("[JWT] Keys already exist at src/main/resources/jwt/");
      return;
    }

    System.out.println("[JWT] Generating 2048-bit RSA key pair...");

    Files.createDirectories(Paths.get("src/main/resources/jwt"));

    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    var keyPair = keyGen.generateKeyPair();

    PrivateKey privateKey = keyPair.getPrivate();
    PublicKey publicKey = keyPair.getPublic();

    writePemPrivateKey(privateKey, PRIVATE_KEY_PATH);
    writePemPublicKey(publicKey, PUBLIC_KEY_PATH);

    System.out.println("[JWT] Keys generated successfully:");
    System.out.println("[JWT]   Private: " + PRIVATE_KEY_PATH);
    System.out.println("[JWT]   Public:  " + PUBLIC_KEY_PATH);
  }

  private static void writePemPrivateKey(PrivateKey key, String filepath) throws Exception {
    String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
    StringBuilder pem = new StringBuilder();
    pem.append("-----BEGIN PRIVATE KEY-----\n");
    for (int i = 0; i < encoded.length(); i += 64) {
      pem.append(encoded, i, Math.min(i + 64, encoded.length())).append("\n");
    }
    pem.append("-----END PRIVATE KEY-----\n");

    try (FileWriter writer = new FileWriter(filepath)) {
      writer.write(pem.toString());
    }
  }

  private static void writePemPublicKey(PublicKey key, String filepath) throws Exception {
    String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
    StringBuilder pem = new StringBuilder();
    pem.append("-----BEGIN PUBLIC KEY-----\n");
    for (int i = 0; i < encoded.length(); i += 64) {
      pem.append(encoded, i, Math.min(i + 64, encoded.length())).append("\n");
    }
    pem.append("-----END PUBLIC KEY-----\n");

    try (FileWriter writer = new FileWriter(filepath)) {
      writer.write(pem.toString());
    }
  }
}

