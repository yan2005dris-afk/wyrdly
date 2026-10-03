package com.wyrdly.notifications.domain.exception;

/**
 * Thrown when a push subscription payload is invalid (missing endpoint, malformed keys, unsupported
 * URL scheme, etc.). The exception mapper translates this to {@code 400 Bad Request}.
 */
public class InvalidSubscriptionException extends RuntimeException {
  public InvalidSubscriptionException(String message) {
    super(message);
  }
}
