package com.yaga.post.interfaces.rest;

import com.yaga.post.domain.exception.PostValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;

@Provider
class PostValidationExceptionMapper implements ExceptionMapper<PostValidationException> {

  @Override
  public Response toResponse(PostValidationException exception) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(
            new ErrorResponseDto(
                Instant.now(),
                Response.Status.BAD_REQUEST.getStatusCode(),
                "Bad Request",
                exception.getMessage(),
                "POST /api/posts"))
        .build();
  }

  record ErrorResponseDto(
      Instant timestamp, int status, String error, String message, String path) {}
}
