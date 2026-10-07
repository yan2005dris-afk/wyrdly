package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.domain.model.PushEvent;

/**
 * Output port for dispatching a Web Push notification. Domain events in other modules observe
 * themselves into this port instead of depending on the concrete {@code PushDispatcherImpl}, which
 * keeps the notifications infrastructure an interchangeable detail of the application layer.
 *
 * <p>Implementations are expected to be fire-and-forget: the call should return to the caller
 * promptly, with all I/O and VAPID signing running on a background executor.
 */
public interface PushDispatcherPort {

  /**
   * Hands the event off to the Web Push pipeline. The recipient's active subscription is resolved,
   * the payload is encrypted, a VAPID JWT is signed, and the request is POSTed to the browser's
   * Push Service. Errors are logged and counted in metrics; they never propagate back to the
   * caller, so listeners do not need to wrap this in a try/catch.
   */
  void dispatch(PushEvent event);
}
