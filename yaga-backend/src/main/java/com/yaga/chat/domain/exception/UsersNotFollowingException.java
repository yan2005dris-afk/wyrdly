package com.yaga.chat.domain.exception;

public class UsersNotFollowingException extends RuntimeException {
  public UsersNotFollowingException(String message) {
    super(message);
  }
}
