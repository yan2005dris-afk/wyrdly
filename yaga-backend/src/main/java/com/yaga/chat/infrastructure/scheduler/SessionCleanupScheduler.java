package com.yaga.chat.infrastructure.scheduler;

import com.yaga.chat.infrastructure.websocket.ChatSessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

@ApplicationScoped
public class SessionCleanupScheduler {

  private static final Logger LOGGER = Logger.getLogger(SessionCleanupScheduler.class.getName());
  private static final long CLEANUP_INTERVAL = 5 * 60 * 1000; // 5 minutes

  @Inject ChatSessionRegistry sessionRegistry;

  private Timer cleanupTimer;

  @PostConstruct
  void start() {
    cleanupTimer = new Timer("WebSocketSessionCleanup", true);
    cleanupTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              LOGGER.fine("Starting WebSocket session cleanup");
              sessionRegistry.cleanup();
              LOGGER.fine("WebSocket session cleanup completed");
            } catch (Exception e) {
              LOGGER.warning("Error during session cleanup: " + e.getMessage());
            }
          }
        },
        CLEANUP_INTERVAL,
        CLEANUP_INTERVAL);
    LOGGER.info("WebSocket session cleanup scheduler started (every 5 minutes)");
  }

  @PreDestroy
  void stop() {
    if (cleanupTimer != null) {
      cleanupTimer.cancel();
      LOGGER.info("WebSocket session cleanup scheduler stopped");
    }
  }
}
