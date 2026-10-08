package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.domain.exception.CommentNotFoundException;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.exception.UnauthorizedCommentActionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Maps domain exceptions to RFC-7807-style error responses. Rate-limit rejections are
 * short-circuited by {@link ReactionRateLimitFilter} before reaching this mapper.
 */
@ApplicationScoped
public class PostExceptionMappers {

  @ServerExceptionMapper
  public Response handlePostValidation(PostValidationException ex) {
    return buildResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handlePostNotFound(PostNotFoundException ex) {
    return buildResponse(Response.Status.NOT_FOUND, "POST_NOT_FOUND", ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleCommentNotFound(CommentNotFoundException ex) {
    return buildResponse(Response.Status.NOT_FOUND, "COMMENT_NOT_FOUND", ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleUnauthorizedCommentAction(UnauthorizedCommentActionException ex) {
    return buildResponse(Response.Status.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handlePostPersistence(PostPersistenceException ex) {
    return buildResponse(
        Response.Status.INTERNAL_SERVER_ERROR,
        "DEPENDENCY_DOWN",
        "Servicio temporalmente no disponible. Reintenta en unos segundos.");
  }

  @ServerExceptionMapper
  public Response handleCircuitBreakerOpen(CircuitBreakerOpenException ex) {
    return buildResponse(
        Response.Status.SERVICE_UNAVAILABLE,
        "DEPENDENCY_DOWN",
        "Servicio temporalmente no disponible. Reintenta en unos segundos.");
  }

  @ServerExceptionMapper
  public Response handleConstraintViolation(ConstraintViolationException ex) {
    String violations =
        ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));
    return buildResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", violations);
  }

  @ServerExceptionMapper
  public Response handleBadRequest(BadRequestException ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : "Invalid request";
    return buildResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  @ServerExceptionMapper
  public Response handleJsonMappingException(
      com.fasterxml.jackson.databind.JsonMappingException ex) {
    String message = ex.getOriginalMessage() != null ? ex.getOriginalMessage() : "Invalid request";
    return buildResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  @ServerExceptionMapper
  public Response handleJsonParseException(com.fasterxml.jackson.core.JsonParseException ex) {
    String message = ex.getOriginalMessage() != null ? ex.getOriginalMessage() : "Invalid JSON";
    return buildResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  @ServerExceptionMapper
  public Response handleIOException(java.io.IOException ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : "Invalid request";
    if (message.contains("type")
        || message.contains("enum")
        || message.contains("missing")
        || message.contains("required")) {
      return buildResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
    // If it's another IO error, re-throw (or return 500)
    throw new RuntimeException(ex);
  }

  private Response buildResponse(Response.Status status, String code, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status.getStatusCode());
    body.put("error", status.getReasonPhrase());
    body.put("code", code);
    body.put("message", message != null ? message : "");
    body.put("timestamp", Instant.now().toString());
    return Response.status(status).entity(body).build();
  }
}
