package com.wyrdly.notifications.application.usecase.impl;

import com.wyrdly.notifications.application.dto.SubscribeRequestDto;
import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.application.usecase.SubscribeToPushUseCase;
import com.wyrdly.notifications.domain.exception.InvalidSubscriptionException;
import com.wyrdly.notifications.domain.model.PushSubscription;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SubscribeToPushUseCaseImpl implements SubscribeToPushUseCase {

  private final PushSubscriptionRepositoryPort repository;

  @Inject
  public SubscribeToPushUseCaseImpl(PushSubscriptionRepositoryPort repository) {
    this.repository = repository;
  }

  @Override
  public SubscribeResponseDto subscribe(String userId, SubscribeRequestDto request) {
    validate(request);

    PushSubscription subscription =
        new PushSubscription(
            request.endpoint().trim(),
            request.keys().p256dh().trim(),
            request.keys().auth().trim());
    repository.save(userId, subscription);
    return SubscribeResponseDto.subscribed();
  }

  private void validate(SubscribeRequestDto request) {
    if (request == null || request.endpoint() == null || request.endpoint().isBlank()) {
      throw new InvalidSubscriptionException("endpoint is required");
    }
    if (request.keys() == null) {
      throw new InvalidSubscriptionException("keys is required");
    }
    String p256dh = request.keys().p256dh() == null ? "" : request.keys().p256dh().trim();
    String auth = request.keys().auth() == null ? "" : request.keys().auth().trim();
    if (p256dh.isEmpty()) {
      throw new InvalidSubscriptionException("keys.p256dh is required");
    }
    if (auth.isEmpty()) {
      throw new InvalidSubscriptionException("keys.auth is required");
    }
    // The decoded p256dh must be exactly 65 bytes starting with 0x04 (ANSI X9.62
    // uncompressed EC point for prime256v1). Accepts base64url with or without padding.
    if (!BASE64_OR_BASE64URL_PATTERN.matcher(p256dh).matches()) {
      throw new InvalidSubscriptionException("keys.p256dh must be base64/base64url encoded");
    }
    if (!BASE64_OR_BASE64URL_PATTERN.matcher(auth).matches()) {
      throw new InvalidSubscriptionException("keys.auth must be base64/base64url encoded");
    }
    if (!isValidUncompressedPoint(p256dh)) {
      throw new InvalidSubscriptionException(
          "keys.p256dh must decode to 65 bytes starting with 0x04 (uncompressed EC point)");
    }
  }

  private static final java.util.regex.Pattern BASE64_OR_BASE64URL_PATTERN =
      java.util.regex.Pattern.compile("^[A-Za-z0-9_=-]+$");

  private static boolean isValidUncompressedPoint(String b64) {
    try {
      byte[] raw = java.util.Base64.getUrlDecoder().decode(b64);
      return raw.length == 65 && raw[0] == 0x04;
    } catch (IllegalArgumentException ex) {
      try {
        byte[] raw = java.util.Base64.getDecoder().decode(b64);
        return raw.length == 65 && raw[0] == 0x04;
      } catch (IllegalArgumentException ex2) {
        return false;
      }
    }
  }
}
