package com.wyrdly.user.domain.exception;

public class SearchValidationException extends RuntimeException {
  public SearchValidationException(String message) {
    super(message);
  }

  public SearchValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
