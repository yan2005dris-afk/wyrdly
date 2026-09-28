package com.yaga.auth.application.port;

public interface PasswordHasherPort {
  String hash(String rawPassword);

  boolean verify(String rawPassword, String hashedPassword);
}
