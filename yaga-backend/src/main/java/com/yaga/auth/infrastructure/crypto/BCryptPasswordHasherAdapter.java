package com.yaga.auth.infrastructure.crypto;

import com.yaga.auth.application.port.PasswordHasherPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

  private static final int LOG_ROUNDS = 12;

  @Override
  public String hash(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new IllegalArgumentException("Password cannot be null or empty");
    }
    return BCrypt.hashpw(rawPassword, BCrypt.gensalt(LOG_ROUNDS));
  }

  @Override
  public boolean verify(String rawPassword, String hashedPassword) {
    if (rawPassword == null || hashedPassword == null) {
      return false;
    }
    try {
      return BCrypt.checkpw(rawPassword, hashedPassword);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
