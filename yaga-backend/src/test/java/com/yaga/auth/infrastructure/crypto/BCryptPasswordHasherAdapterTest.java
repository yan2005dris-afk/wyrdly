package com.yaga.auth.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BCryptPasswordHasherAdapterTest {

  private BCryptPasswordHasherAdapter hasher;

  @BeforeEach
  void setUp() {
    hasher = new BCryptPasswordHasherAdapter();
  }

  @Test
  void hashAndVerify_Success() {
    String password = "StrongPassword123!";
    String hash = hasher.hash(password);

    assertNotNull(hash);
    assertTrue(hasher.verify(password, hash));
    assertFalse(hasher.verify("WrongPassword", hash));
  }

  @Test
  void hash_ThrowsOnNullOrEmpty() {
    assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    assertThrows(IllegalArgumentException.class, () -> hasher.hash("   "));
  }

  @Test
  void verify_ReturnsFalseOnNullOrInvalidHash() {
    assertFalse(hasher.verify(null, "somehash"));
    assertFalse(hasher.verify("password", null));
    assertFalse(hasher.verify("password", "not_a_valid_bcrypt_hash"));
  }
}
