package com.wyrdly.post.domain.exception;

/**
 * Thrown when a Neo4j operation against the post store fails for reasons unrelated to the request
 * payload (driver errors, network failures, constraint violations, etc.).
 *
 * <p>Mapped to {@code 500 Internal Server Error} by {@code PostExceptionMappers}. Distinguished
 * from {@link PostValidationException}, which represents input-level validation failures.
 */
public class PostPersistenceException extends RuntimeException {

  public PostPersistenceException(String message) {
    super(message);
  }

  public PostPersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
