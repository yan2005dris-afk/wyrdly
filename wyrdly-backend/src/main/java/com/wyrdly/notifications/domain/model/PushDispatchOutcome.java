package com.wyrdly.notifications.domain.model;

/** Result of dispatching a {@link PushEvent} to the browser. */
public enum PushDispatchOutcome {
  /** Push Service accepted the message (HTTP 201). */
  OK,
  /** No subscription stored for the recipient — nothing to do. */
  NO_SUBSCRIPTION,
  /** Push Service returned 404 or 410; subscription has been cleaned up. */
  SUBSCRIPTION_GONE,
  /** Push Service returned a 4xx that is not 404/410 — likely a payload/header bug. */
  CLIENT_ERROR,
  /** Push Service returned 5xx after retries — transient failure. */
  SERVER_ERROR,
  /** Unexpected exception (network, encryption, signing). */
  FAILED
}
