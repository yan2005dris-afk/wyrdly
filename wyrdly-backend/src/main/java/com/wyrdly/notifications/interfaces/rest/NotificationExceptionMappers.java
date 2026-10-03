package com.wyrdly.notifications.interfaces.rest;

import com.wyrdly.notifications.domain.exception.InvalidSubscriptionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response.StatusType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exception mappers for the notifications bounded context. Mirrors the {@code Map.of("status",
 * "error", "message", "timestamp")} convention used across the codebase.
 */
public final class NotificationExceptionMappers {

  private NotificationExceptionMappers() {}

  static Map<String, Object> body(String error, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", error);
    body.put("message", message);
    body.put("timestamp", Instant.now().toString());
    return body;
  }

  static String format(ConstraintViolation<?> v) {
    String path = v.getPropertyPath() == null ? "" : v.getPropertyPath().toString();
    return path + ": " + v.getMessage();
  }

  @Provider
  public static class InvalidSubscriptionMapper
      implements ExceptionMapper<InvalidSubscriptionException> {
    @Override
    public jakarta.ws.rs.core.Response toResponse(InvalidSubscriptionException ex) {
      return jakarta.ws.rs.core.Response.status(400).entity(body("error", ex.getMessage())).build();
    }
  }

  @Provider
  public static class ConstraintViolationMapper
      implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public jakarta.ws.rs.core.Response toResponse(ConstraintViolationException ex) {
      List<String> details =
          ex.getConstraintViolations().stream()
              .map(NotificationExceptionMappers::format)
              .collect(Collectors.toList());
      Map<String, Object> body = body("error", "Validation failed");
      body.put("violations", details);
      return jakarta.ws.rs.core.Response.status(400).entity(body).build();
    }
  }

  /** Defensive 500 fallback for unexpected runtime exceptions in this module. */
  @Provider
  public static class GenericNotificationsMapper implements ExceptionMapper<RuntimeException> {
    @Override
    public jakarta.ws.rs.core.Response toResponse(RuntimeException ex) {
      String pkg = ex.getClass().getPackageName();
      if (!pkg.startsWith("com.wyrdly.notifications")) {
        throw ex;
      }
      StatusType status = jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
      return jakarta.ws.rs.core.Response.status(status)
          .entity(body("error", ex.getMessage() == null ? "internal error" : ex.getMessage()))
          .build();
    }
  }
}
