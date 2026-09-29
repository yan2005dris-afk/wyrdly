package com.yaga.user.domain.exception;

public class SelfFollowNotAllowedException extends RuntimeException {

  private final String userId;

  public SelfFollowNotAllowedException(String userId) {
    super("No puedes seguirte a ti mismo.");
    this.userId = userId;
  }

  public String getUserId() {
    return userId;
  }
}
