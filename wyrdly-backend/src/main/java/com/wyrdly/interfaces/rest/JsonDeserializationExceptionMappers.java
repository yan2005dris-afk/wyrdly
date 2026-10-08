package com.wyrdly.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Turns request bodies rejected by the strict JSON policy ({@code StrictObjectMapperCustomizer})
 * into a 400 with the same {@code status/error/message/timestamp} body used across the codebase.
 * Without it, Quarkus answers these with an empty 400 in prod. Messages only name the offending
 * field: parser details stay in the log.
 */
@ApplicationScoped
public class JsonDeserializationExceptionMappers {

  private static final Logger LOG = Logger.getLogger(JsonDeserializationExceptionMappers.class);

  /** Unknown fields, wrong types, null primitives and invalid enum values. */
  @ServerExceptionMapper
  public Response handleMismatchedInput(MismatchedInputException ex) {
    LOG.debugf("Rejected request body: %s", ex.getOriginalMessage());
    String field = fieldPath(ex);
    String message;
    if (ex instanceof UnrecognizedPropertyException unknown) {
      message = "Unknown field '" + unknown.getPropertyName() + "'";
    } else if (field.isEmpty()) {
      message = "Invalid request body";
    } else {
      message = "Invalid value for field '" + field + "'";
    }
    return badRequest(message);
  }

  /**
   * Quarkus wraps malformed JSON (syntax errors, truncated body) in a 400 {@link
   * WebApplicationException}. Every other one keeps the response Quarkus built (405, 415...): being
   * the most specific mapper, this also stops a broad {@code RuntimeException} mapper from turning
   * them into a 500.
   */
  @ServerExceptionMapper
  public Response handleWebApplication(WebApplicationException ex) {
    if (ex.getCause() instanceof JsonProcessingException parseError) {
      LOG.debugf("Malformed request body: %s", parseError.getOriginalMessage());
      return badRequest("Malformed JSON request body");
    }
    return ex.getResponse();
  }

  private static String fieldPath(JsonMappingException ex) {
    return ex.getPath().stream()
        .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
        .collect(Collectors.joining("."))
        .replace(".[", "[");
  }

  private static Response badRequest(String message) {
    Response.Status status = Response.Status.BAD_REQUEST;
    Map<String, Object> body =
        Map.of(
            "status", status.getStatusCode(),
            "error", status.getReasonPhrase(),
            "message", message,
            "timestamp", Instant.now().toString());
    return Response.status(status).entity(body).build();
  }
}
