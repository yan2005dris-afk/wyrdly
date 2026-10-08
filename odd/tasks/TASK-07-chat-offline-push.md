# Guía de Implementación: [HU11-2.4] Hook Push para Chat Offline (#67)

- **Task ID:** `TASK-07` / `BE-PUSH-2.4` (Sub-issue de #15 / Issue #67)
- **Estado:** Implementado y verificado (18/18 tests específicos + 244/244 suite completa)
- **Ruta de Archivo:** `odd/tasks/TASK-07-chat-offline-push.md`
- **Rama:** `brydyan/67-hu11-24-featpush-hook-en-chatservice-para-chat_message-offline`
- **Arquitectura:** Clean Architecture / Onion DDD (ADR-002) mediante Evento CDI y Observer desacoplado en el módulo de Notificaciones

---

## 1. Contexto y Decisión Arquitectónica

La issue #67 menciona originalmente `ChatService.onMessage(...)`. Sin embargo, en el backend de Wyrdly:
- No existe `ChatService`.
- El flujo está dividido en:
  - [`ChatWebSocketEndpoint`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend/src/main/java/com/wyrdly/chat/interfaces/websocket/ChatWebSocketEndpoint.java) (capa de interfaces WS).
  - [`SendMessageUseCaseImpl`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend/src/main/java/com/wyrdly/chat/application/usecase/SendMessageUseCaseImpl.java) (capa de aplicación/dominio).
  - [`ChatSessionRegistry`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend/src/main/java/com/wyrdly/chat/infrastructure/websocket/ChatSessionRegistry.java) (registro `@ApplicationScoped` de sesiones activas).

**Decisión acordada:** 
Implementar mediante **Patrón Evento CDI + Observer** ubicando el listener en el bounded context de Notificaciones (`com.wyrdly.notifications.application.listener`), idéntico a [`UserFollowNotificationEventListener`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend/src/main/java/com/wyrdly/notifications/application/listener/UserFollowNotificationEventListener.java) y [`PostReactionNotificationEventListener`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend/src/main/java/com/wyrdly/notifications/application/listener/PostReactionNotificationEventListener.java).

---

## 2. Componentes Implementados

### 2.1. Evento de Dominio: `DirectMessageSentEvent`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/chat/domain/event/DirectMessageSentEvent.java`

```java
package com.wyrdly.chat.domain.event;

import java.time.Instant;
import java.util.Objects;

public record DirectMessageSentEvent(
    String messageId,
    String senderId,
    String recipientId,
    String content,
    Instant sentAt) {

  public DirectMessageSentEvent {
    Objects.requireNonNull(messageId, "messageId must not be null");
    Objects.requireNonNull(senderId, "senderId must not be null");
    Objects.requireNonNull(recipientId, "recipientId must not be null");
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(sentAt, "sentAt must not be null");
  }
}
```

---

### 2.2. Disparador de Evento en: `SendMessageUseCaseImpl`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/chat/application/usecase/SendMessageUseCaseImpl.java`

- Inyección:
  ```java
  @Inject Event<DirectMessageSentEvent> messageSentEvent;
  ```
- En `execute(...)`, tras persistir el mensaje:
  ```java
  directMessageRepository.save(message);

  messageSentEvent
      .fireAsync(
          new DirectMessageSentEvent(
              message.getId(),
              message.getSenderId(),
              message.getRecipientId(),
              message.getContent(),
              message.getSentAt()))
      .exceptionally(
          ex -> {
            Log.warnf(ex, "DirectMessageSentEvent observer failed: messageId=%s", message.getId());
            return null;
          });
  ```

---

### 2.3. Registro de Sesiones: `ChatSessionRegistry`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/chat/infrastructure/websocket/ChatSessionRegistry.java`

- Mantiene mapeo concurrente de sesiones activas (`isUserOnline`, `register`, `unregister`, `broadcast`).

---

### 2.4. Listener Asíncrono en Notificaciones: `ChatMessagePushEventListener`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/notifications/application/listener/ChatMessagePushEventListener.java`

- Desacoplado: observa `@ObservesAsync DirectMessageSentEvent`.
- Ignora auto-mensajes (`senderId.equals(recipientId)`).
- Verifica estado online con `sessionRegistry.isUserOnline(...)` (con tolerancia a fallos/fallback offline).
- Persiste notificación in-app en `NotificationRepository` (para centro de notificaciones) con resiliencia `try/catch`.
- Trunca snippet a 140 caracteres.
- Despacha `PushEvent` asíncrono con `PushDispatcherPort`.

---

## 3. Tests Unitarios

1. **`ChatMessagePushEventListenerTest`** (`wyrdly-backend/src/test/java/com/wyrdly/notifications/application/listener/ChatMessagePushEventListenerTest.java`):
   - `doesNotDispatchPushWhenRecipientIsOnline()`
   - `doesNotDispatchPushWhenSenderIsRecipient()`
   - `dispatchesPushWhenRecipientIsOfflineAndSubscriptionExists()`
   - `dispatchesPushWhenSessionRegistryThrows()`
   - `truncatesSnippetTo140Characters()`
   - `usesFallbackTitleWhenSenderProfileNotFound()`
   - `persistsNotificationBeforeDispatchingPush()`
   - `continuesDispatchingPushWhenNotificationSaveThrows()`
2. **`SendMessageUseCaseImplTest`** (`wyrdly-backend/src/test/java/com/wyrdly/chat/application/usecase/SendMessageUseCaseImplTest.java`):
   - Verifica disparo asíncrono del evento `DirectMessageSentEvent`.
3. **`ChatSessionRegistryTest`** (`wyrdly-backend/src/test/java/com/wyrdly/chat/infrastructure/websocket/ChatSessionRegistryTest.java`):
   - Verifica registro/desregistro, tracking de usuario online y broadcast.

---

## 4. Checklist de Verificación

- [x] Compilar y formatear: `cd wyrdly-backend && ./mvnw spotless:apply`
- [x] Ejecutar tests específicos:
  ```bash
  cd wyrdly-backend && ./mvnw test -Dtest=ChatMessagePushEventListenerTest,SendMessageUseCaseImplTest,ChatWebSocketEndpointTest,ChatSessionRegistryTest
  ```
- [x] Verificar que no haya regresiones en suite completa: `cd wyrdly-backend && ./mvnw test` (244/244 tests OK)
- [x] Commit convencional listo:
  `feat(push): emit CHAT_MESSAGE push notification when recipient offline`
  *(Sin etiquetas de co-autoría ni referencias a IA)*
