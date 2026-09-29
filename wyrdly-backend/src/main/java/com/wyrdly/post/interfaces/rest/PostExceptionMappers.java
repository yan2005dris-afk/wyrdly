package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.exception.PostValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@ApplicationScoped
public class PostExceptionMappers {

  @ServerExceptionMapper
  public Response handlePostValidation(PostValidationException ex) {
    return buildResponse(Response.Status.BAD_REQUEST, ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handlePostPersistence(PostPersistenceException ex) {
    return buildResponse(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
  }

  private Response buildResponse(Response.Status status, String message) {
    return Response.status(status)
        .entity(
            Map.of(
                "status",
                status.getStatusCode(),
                "error",
                status.getReasonPhrase(),
                "message",
                message != null ? message : "",
                "timestamp",
                Instant.now().toString()))
        .build();
  }
}
