package com.wyrdly.user.domain.exception;

public class UserProfileNotFoundException extends RuntimeException {
  public UserProfileNotFoundException(String message) {
    super(message);
  }
}
