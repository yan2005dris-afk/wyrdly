package com.wyrdly.notifications.application.dto;

/** Response payload for subscription lifecycle endpoints. */
public record SubscribeResponseDto(String status) {
  public static SubscribeResponseDto subscribed() {
    return new SubscribeResponseDto("SUBSCRIBED");
  }

  public static SubscribeResponseDto unsubscribed() {
    return new SubscribeResponseDto("UNSUBSCRIBED");
  }
}
