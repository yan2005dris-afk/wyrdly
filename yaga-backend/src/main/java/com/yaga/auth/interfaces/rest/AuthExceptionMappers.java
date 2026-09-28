package com.yaga.auth.interfaces.rest;

import com.yaga.auth.domain.exception.InvalidCredentialsException;
import com.yaga.auth.domain.exception.InvalidTokenException;
import com.yaga.auth.domain.exception.UserAlreadyExistsException;
import com.yaga.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@ApplicationScoped
public class AuthExceptionMappers {

  @ServerExceptionMapper
  public Response handleUserAlreadyExists(UserAlreadyExistsException ex) {
    return buildResponse(Response.Status.CONFLICT, ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleInvalidCredentials(InvalidCredentialsException ex) {
    return buildResponse(Response.Status.UNAUTHORIZED, ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleInvalidToken(InvalidTokenException ex) {
    return buildResponse(Response.Status.UNAUTHORIZED, ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleUserNotFound(UserNotFoundException ex) {
    return buildResponse(Response.Status.NOT_FOUND, ex.getMessage());
  }

  private Response buildResponse(Response.Status status, String message) {
    Map<String, Object> body =
        Map.of(
            "status", status.getStatusCode(),
            "error", status.getReasonPhrase(),
            "message", message != null ? message : "",
            "timestamp", Instant.now().toString());
    return Response.status(status).entity(body).build();
  }
}
