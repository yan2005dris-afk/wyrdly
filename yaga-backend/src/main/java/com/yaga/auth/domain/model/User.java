package com.yaga.auth.domain.model;

import java.time.Instant;

public record User(
    String id,
    String username,
    String email,
    String passwordHash,
    String fullName,
    String bio,
    String avatarUrl,
    Instant createdAt) {

  public static User createNew(
      String id,
      String username,
      String email,
      String passwordHash,
      String fullName,
      String bio,
      String avatarUrl) {
    return new User(id, username, email, passwordHash, fullName, bio, avatarUrl, Instant.now());
  }
}
