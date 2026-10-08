package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.model.PushSubscription;
import java.util.concurrent.CompletableFuture;

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

  /**
   * Same as {@link #dispatch(PushEvent)} but with the recipient's subscription already resolved,
   * skipping the per-recipient lookup. Intended for fan-outs where the audience query returns the
   * subscriptions in bulk.
   *
   * <p>The returned future always completes normally once the push has been handled (delivered,
   * cleaned up, failed or rejected because the dispatcher is saturated), so callers can use it for
   * back-pressure without handling errors.
   */
  CompletableFuture<Void> dispatchTo(PushSubscription subscription, PushEvent event);
}
