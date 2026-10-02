package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.exception.PostValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
