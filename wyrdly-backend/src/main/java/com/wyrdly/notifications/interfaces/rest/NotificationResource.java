package com.wyrdly.notifications.interfaces.rest;

import com.wyrdly.notifications.application.dto.SubscribeRequestDto;
import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
import com.wyrdly.notifications.application.dto.VapidPublicKeyResponseDto;
import com.wyrdly.notifications.application.usecase.SubscribeToPushUseCase;
import com.wyrdly.notifications.application.usecase.UnsubscribeFromPushUseCase;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * REST surface for the Web Push subscription lifecycle.
 *
 * <ul>
 *   <li>{@code GET /api/notifications/vapid-public-key} — public, returns the server's VAPID public
 *       key so the frontend can subscribe.
 *   <li>{@code POST /api/notifications/subscribe} — JWT-protected, persists the browser's {@link
 *       com.wyrdly.notifications.domain.model.PushSubscription} on the {@code :Usuario} node.
 *   <li>{@code DELETE /api/notifications/subscribe} — JWT-protected, clears the user's
 *       subscription.
 * </ul>
 */
@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

  private final VapidKeyProvider vapidKeyProvider;
  private final SubscribeToPushUseCase subscribeUseCase;
  private final UnsubscribeFromPushUseCase unsubscribeUseCase;
  private final JsonWebToken jwt;

  @Inject
  public NotificationResource(
      VapidKeyProvider vapidKeyProvider,
      SubscribeToPushUseCase subscribeUseCase,
      UnsubscribeFromPushUseCase unsubscribeUseCase,
      JsonWebToken jwt) {
    this.vapidKeyProvider = vapidKeyProvider;
    this.subscribeUseCase = subscribeUseCase;
    this.unsubscribeUseCase = unsubscribeUseCase;
    this.jwt = jwt;
  }

  @GET
  @Path("/vapid-public-key")
  public Response getVapidPublicKey() {
    return Response.ok(new VapidPublicKeyResponseDto(vapidKeyProvider.getPublicKey())).build();
  }

  @POST
  @Path("/subscribe")
  @Consumes(MediaType.APPLICATION_JSON)
  @Authenticated
  public Response subscribe(@Valid SubscribeRequestDto request) {
    String userId = currentUserId();
    SubscribeResponseDto response = subscribeUseCase.subscribe(userId, request);
    return Response.ok(response).build();
  }

  @DELETE
  @Path("/subscribe")
  @Authenticated
  public Response unsubscribe() {
    String userId = currentUserId();
    SubscribeResponseDto response = unsubscribeUseCase.unsubscribe(userId);
    return Response.ok(response).build();
  }

  private String currentUserId() {
    if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      // @Authenticated would normally reject this before we get here; defensive guard.
      throw new IllegalStateException("Authenticated request is missing subject claim");
    }
    return jwt.getSubject();
  }
}
