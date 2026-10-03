package com.wyrdly.notifications.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Browser-pushed subscription payload (W3C Push API). Aligned with the contract in
 * docs/api-contracts/API_CONTRACT.md §HU11.2.
 */
public record SubscribeRequestDto(
    @NotBlank(message = "endpoint is required")
        @Pattern(
            regexp = "^https://.*",
            message = "endpoint must be an https URL (Push Service URL)")
        String endpoint,
    @NotNull(message = "keys is required") @Valid SubscriptionKeysDto keys) {

  /**
   * Cryptographic keys negotiated by the browser via the Push API. {@code p256dh} is the client's
   * ECDH public key (base64url, 65 bytes uncompressed point per ANSI X9.62) and {@code auth} is the
   * 16-byte shared authentication secret (base64url).
   */
  public record SubscriptionKeysDto(
      @NotBlank(message = "keys.p256dh is required") String p256dh,
      @NotBlank(message = "keys.auth is required") String auth) {}
}
