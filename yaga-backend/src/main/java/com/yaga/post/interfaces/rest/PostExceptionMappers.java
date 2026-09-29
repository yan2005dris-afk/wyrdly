package com.yaga.post.interfaces.rest;

import com.yaga.post.domain.exception.PostValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@ApplicationScoped
public class PostExceptionMappers {

  @ServerExceptionMapper
  public Response handlePostValidation(PostValidationException ex) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(
            Map.of(
                "status",
                400,
                "error",
                "Bad Request",
                "message",
                ex.getMessage() != null ? ex.getMessage() : "",
                "timestamp",
                Instant.now().toString()))
        .build();
  }
}
