package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.domain.model.PushEvent;

/**
 * Input port for dispatching a single Web Push event. Implementations are expected to run
 * asynchronously (the caller does not wait for HTTP I/O to complete).
 */
public interface PushDispatcherPort {
  /**
   * Fire-and-forget dispatch. The caller returns immediately; any failure is logged and surfaced
   * through Micrometer counters (\`wyrdly.push.dispatch{type,result}\`).
   */
  void dispatch(PushEvent event);
}
