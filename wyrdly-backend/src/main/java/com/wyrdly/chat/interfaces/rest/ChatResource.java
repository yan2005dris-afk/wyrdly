package com.wyrdly.chat.interfaces.rest;

import com.wyrdly.chat.application.dto.ChatHistoryPage;
import com.wyrdly.chat.application.usecase.GetChatHistoryUseCase;
import com.wyrdly.chat.infrastructure.websocket.ChatSessionRegistry;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ChatResource {

  private static final Logger LOGGER = Logger.getLogger(ChatResource.class.getName());

  private final GetChatHistoryUseCase getChatHistoryUseCase;
  private final ChatSessionRegistry sessionRegistry;
  private final JsonWebToken jwt;

  @Inject
  public ChatResource(
      GetChatHistoryUseCase getChatHistoryUseCase,
      ChatSessionRegistry sessionRegistry,
      JsonWebToken jwt) {
    this.getChatHistoryUseCase = getChatHistoryUseCase;
    this.sessionRegistry = sessionRegistry;
    this.jwt = jwt;
  }

  @GET
  @Path("/{targetUserId}/status")
  @Authenticated
  public Response getUserStatus(@PathParam("targetUserId") String targetUserId) {
    boolean online = sessionRegistry.isUserOnline(targetUserId);
    return Response.ok(new UserStatusResponse(targetUserId, online)).build();
  }

  @GET
  @Path("/{recipientId}/history")
  @Authenticated
  public Response getChatHistory(
      @PathParam("recipientId") String recipientId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("pageSize") @DefaultValue("50") int pageSize) {
    String userId = jwt.getSubject();

    int validatedPageSize = (pageSize < 1 || pageSize > 100) ? 50 : pageSize;
    int validatedPage = (page < 1) ? 1 : page;

    ChatHistoryPage history =
        getChatHistoryUseCase.execute(userId, recipientId, validatedPage, validatedPageSize);

    LOGGER.info(
        "Retrieved chat history for user: "
            + userId
            + " with "
            + recipientId
            + " page: "
            + validatedPage);
    return Response.ok(history).build();
  }

  public record UserStatusResponse(String userId, boolean isOnline) {}
}
