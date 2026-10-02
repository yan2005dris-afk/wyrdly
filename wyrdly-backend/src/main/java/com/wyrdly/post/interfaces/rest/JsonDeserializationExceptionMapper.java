package com.wyrdly.post.interfaces.rest;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps JSON deserialization exceptions to 400 Bad Request responses. This handles cases where
 * Jackson fails to deserialize request bodies due to missing required fields or invalid enum
 * values.
 */
@Provider
@ApplicationScoped
public class JsonDeserializationExceptionMapper implements ExceptionMapper<JsonMappingException> {

  @Override
  public Response toResponse(JsonMappingException exception) {
    String message =
        exception.getOriginalMessage() != null
            ? exception.getOriginalMessage()
            : "Invalid request body";
    return buildResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", message);
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
