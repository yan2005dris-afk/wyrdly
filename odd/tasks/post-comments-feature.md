# Guía de Implementación: Comentarios sobre Publicaciones (#122)

- **Feature:** Comentarios sobre Publicaciones (Post Comments)
- **Issue:** #122 (`[HU10 / Post Comments] Implementar comentarios sobre publicaciones`)
- **Estado:** Especificado, blindado y listo para ejecución
- **Ruta de Archivo:** `odd/tasks/post-comments-feature.md`
- **Rama:** `brydyan/122-implementar-comentarios-sobre-publicaciones-postcard-tiene-botón-muerto-y-commentscount-mockeado`
- **Arquitectura:** Clean Architecture / Onion DDD (Quarkus + Neo4j) + React SPA con TanStack Query (ADR-007) y eventos CDI desacoplados

---

## 1. Resumen y Alcance del Feature

Conectar el botón "Comment" del [`PostCard`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend/src/features/social/components/PostCard/PostCard.tsx) y reemplazar el `commentsCount: 0` mockeado por un sistema real de comentarios persistido en Neo4j, con autorización estricta, rate limit en Redis, notificaciones push al autor de la publicación y gestión de estado con TanStack Query conforme a [ADR-007](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/docs/decisions/ADR-007-migrate-social-module-to-tanstack-query.md).

### Alcance Incluido
1. **Backend**:
   - Migración Cypher `V006` con constraints e índices para nodo `(:Comentario)`.
   - Entidad de dominio `Comment`, evento `CommentCreatedEvent` y excepciones `CommentNotFoundException`, `UnauthorizedCommentActionException`.
   - Puerto de persistencia `CommentRepository`.
   - Casos de uso segregados en `application/usecase/`:
     - `CreateCommentUseCase`
     - `ListCommentsByPostUseCase`
     - `DeleteCommentUseCase`
   - Orquestador de aplicación `CommentService` implementando los casos de uso con:
     - Validación de existencia de post (404 si no existe).
     - Regla de autorización para DELETE: permitido solo al **autor del comentario** o al **dueño de la publicación** (403 si es usuario no autorizado).
     - Disparo del evento CDI `CommentCreatedEvent`.
   - Listener de notificaciones `CommentNotificationEventListener` en el bounded context de notificaciones (persiste in-app y despacha push al autor del post, omitiendo auto-comentarios).
   - Recurso REST `CommentResource` (`POST`, `GET`, `DELETE`) con filtro de rate limit en Redis `CommentRateLimitFilter` (10 comentarios/minuto).
   - Mapeo de excepciones en `PostExceptionMappers` (404 y 403).
   - Actualización de `FEED_QUERY` en `Neo4jPostRepositoryAdapter` mediante subquery `COUNT { (p)<-[:EN_POST]-(:Comentario) } AS commentsCount` (sin explosión cartesiana).
   - `PostResponse` con `commentsCount: long` y sobrecarga de constructor retrocompatible, actualizando `PostService.createPost` con `0L`.

2. **Frontend**:
   - Contrato verificado: `@tanstack/react-query: ^5.104.1` está instalado y configurado en `App.tsx` con `QueryClientProvider`.
   - Se adopta la **Opción 1** documentada en [ADR-007](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/docs/decisions/ADR-007-migrate-social-module-to-tanstack-query.md): `useComments` usa TanStack Query de forma aislada sin inflar el scope del PR con la migración de `useFeed`/`useReaction`.
   - Tipos de comentarios y cliente API en `commentsApi.ts`.
   - Hooks TanStack Query en `useComments.ts`: `useComments`, `useCreateComment`, `useDeleteComment` con mutaciones optimistas e invalidación de query.
   - Componentes UI en `features/social/components/CommentSection/`:
     - `CommentSection.tsx` (contenedor colapsable)
     - `CommentInput.tsx` (input con prevención de doble envío / rage-click)
     - `CommentList.tsx` (listado paginado / skeletons)
     - `CommentItem.tsx` (avatar, contenido, timestamp y botón eliminar condicional a permisos)
   - Integración en [`PostCard.tsx`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend/src/features/social/components/PostCard/PostCard.tsx) con control de estado local para apertura de sección y preservación del callback `onCommentClick`.

3. **Pruebas**:
   - Unitarias Mockito de backend (`CommentServiceTest`, `CommentNotificationEventListenerTest`).
   - Endpoint Quarkus (`CommentResourceTest`).
   - Integración Testcontainers Neo4j (`Neo4jCommentRepositoryAdapterIT`).
   - Frontend Vitest (`CommentSection.test.tsx`, `useComments.test.ts`, `PostCard.test.tsx`).

---

## 2. Garantías de Cero Regresiones (Anti-Rotura)

1. **[`PostResponse.java`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend/src/main/java/com/wyrdly/post/application/dto/PostResponse.java)**:
   - Se mantiene el constructor original de 7 parámetros delegando en el de 8 con `0L`. **Ningún test existente rompe**.
   - `PostService.createPost` se actualiza para pasar `0L` al de 8 parámetros.
2. **[`PostCard.tsx`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend/src/features/social/components/PostCard/PostCard.tsx)**:
   - Se mantiene el prop opcional `onCommentClick?: (postId: string) => void` y se invoca al alternar `isCommentsOpen`.
3. **[`feed.ts`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend/src/types/feed.ts)**:
   - `PostApiResponse` recibe `readonly commentsCount?: number` y `mapPostApiResponseToPost` usa `response.commentsCount ?? 0`.

---

## 3. Especificación Backend (Quarkus + Neo4j)

### 3.1. Migración Cypher
**Ruta:** `wyrdly-backend/src/main/resources/neo4j/migrations/V006__add_comment_schema_and_indexes.cypher`

```cypher
// Constraint de unicidad para Comentario
CREATE CONSTRAINT comment_id_unique IF NOT EXISTS
FOR (c:Comentario) REQUIRE c.id IS UNIQUE;

// Índice para consultas por post y orden temporal
CREATE INDEX comment_post_created_at_index IF NOT EXISTS
FOR (c:Comentario) ON (c.postId, c.createdAt);
```

---

### 3.2. Modelo de Dominio, Eventos y Excepciones

#### `Comment.java`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/domain/model/Comment.java`

```java
package com.wyrdly.post.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Comment(
    String id,
    String postId,
    Author author,
    String content,
    Instant createdAt) {

  public Comment {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(postId, "postId must not be null");
    Objects.requireNonNull(author, "author must not be null");
    if (content == null || content.trim().isEmpty()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    if (content.length() > 1000) {
      throw new IllegalArgumentException("content exceeds 1000 characters");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
```

#### `CommentCreatedEvent.java`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/domain/event/CommentCreatedEvent.java`

```java
package com.wyrdly.post.domain.event;

import java.time.Instant;
import java.util.Objects;

public record CommentCreatedEvent(
    String commentId,
    String postId,
    String postAuthorId,
    String commentAuthorId,
    String content,
    Instant createdAt) {

  public CommentCreatedEvent {
    Objects.requireNonNull(commentId, "commentId must not be null");
    Objects.requireNonNull(postId, "postId must not be null");
    Objects.requireNonNull(postAuthorId, "postAuthorId must not be null");
    Objects.requireNonNull(commentAuthorId, "commentAuthorId must not be null");
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
```

#### Excepciones de Dominio
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/domain/exception/CommentNotFoundException.java`
```java
package com.wyrdly.post.domain.exception;

public class CommentNotFoundException extends RuntimeException {
  public CommentNotFoundException(String message) {
    super(message);
  }
}
```

**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/domain/exception/UnauthorizedCommentActionException.java`
```java
package com.wyrdly.post.domain.exception;

public class UnauthorizedCommentActionException extends RuntimeException {
  public UnauthorizedCommentActionException(String message) {
    super(message);
  }
}
```

---

### 3.3. Puerto del Repositorio de Comentarios
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/domain/repository/CommentRepository.java`

```java
package com.wyrdly.post.domain.repository;

import com.wyrdly.post.domain.model.Comment;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {
  Comment save(Comment comment);
  List<Comment> findByPostId(String postId, int page, int pageSize);
  long countByPostId(String postId);
  Optional<Comment> findById(String commentId);
  void deleteById(String commentId);
}
```

---

### 3.4. DTOs de Aplicación

#### `CreateCommentRequest.java`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/application/dto/CreateCommentRequest.java`
```java
package com.wyrdly.post.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
    @NotBlank(message = "El contenido no puede estar vacío")
    @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
    String content) {}
```

#### `CommentResponse.java` (incluye `authorId` top-level para UI)
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/application/dto/CommentResponse.java`
```java
package com.wyrdly.post.application.dto;

import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import java.time.Instant;

public record CommentResponse(
    String id,
    String postId,
    String authorId,
    String content,
    Instant createdAt,
    AuthorDto author) {}
```

#### `CommentListResponseDto.java`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/application/dto/CommentListResponseDto.java`
```java
package com.wyrdly.post.application.dto;

import java.util.List;

public record CommentListResponseDto(
    List<CommentResponse> data,
    long totalCount,
    int page,
    int pageSize) {}
```

---

### 3.5. Casos de Uso Segregados (Onion Architecture)

#### `CreateCommentUseCase.java`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/application/usecase/CreateCommentUseCase.java`
```java
package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;

public interface CreateCommentUseCase {
  CommentResponse createComment(String postId, String userId, CreateCommentRequest request);
}
```

#### `ListCommentsByPostUseCase.java`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/application/usecase/ListCommentsByPostUseCase.java`
```java
package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.CommentListResponseDto;

public interface ListCommentsByPostUseCase {
  CommentListResponseDto getComments(String postId, int page, int pageSize);
}
```

#### `DeleteCommentUseCase.java`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/application/usecase/DeleteCommentUseCase.java`
```java
package com.wyrdly.post.application.usecase;

public interface DeleteCommentUseCase {
  void deleteComment(String postId, String commentId, String userId);
}
```

---

### 3.6. Orquestador de Aplicación: `CommentService`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/application/service/CommentService.java`

```java
package com.wyrdly.post.application.service;

import com.wyrdly.post.application.dto.CommentListResponseDto;
import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.CreateCommentUseCase;
import com.wyrdly.post.application.usecase.DeleteCommentUseCase;
import com.wyrdly.post.application.usecase.ListCommentsByPostUseCase;
import com.wyrdly.post.domain.event.CommentCreatedEvent;
import com.wyrdly.post.domain.exception.CommentNotFoundException;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.exception.UnauthorizedCommentActionException;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.Comment;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.AuthorRepository;
import com.wyrdly.post.domain.repository.CommentRepository;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class CommentService
    implements CreateCommentUseCase, ListCommentsByPostUseCase, DeleteCommentUseCase {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;
  private final AuthorRepository authorRepository;
  private final Event<CommentCreatedEvent> commentCreatedEvent;

  @Inject
  public CommentService(
      CommentRepository commentRepository,
      PostRepository postRepository,
      AuthorRepository authorRepository,
      Event<CommentCreatedEvent> commentCreatedEvent) {
    this.commentRepository = Objects.requireNonNull(commentRepository, "commentRepository");
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository");
    this.authorRepository = Objects.requireNonNull(authorRepository, "authorRepository");
    this.commentCreatedEvent = Objects.requireNonNull(commentCreatedEvent, "commentCreatedEvent");
  }

  @Override
  @Transactional
  public CommentResponse createComment(String postId, String userId, CreateCommentRequest request) {
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

    Author author =
        authorRepository
            .findById(userId)
            .orElseThrow(() -> new PostValidationException("User not found: " + userId));

    String commentId = "cmt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Instant now = Instant.now();

    Comment comment = new Comment(commentId, postId, author, request.content().trim(), now);
    Comment saved = commentRepository.save(comment);

    // Disparar evento de dominio para notificaciones / push
    commentCreatedEvent.fire(
        new CommentCreatedEvent(
            saved.id(), postId, post.userId(), userId, saved.content(), now));

    AuthorDto authorDto =
        new AuthorDto(author.id(), author.username(), author.fullName(), author.avatarUrl());
    return new CommentResponse(
        saved.id(), saved.postId(), author.id(), saved.content(), saved.createdAt(), authorDto);
  }

  @Override
  @Transactional
  public CommentListResponseDto getComments(String postId, int page, int pageSize) {
    if (postRepository.findById(postId).isEmpty()) {
      throw new PostNotFoundException("Post not found: " + postId);
    }
    List<Comment> comments = commentRepository.findByPostId(postId, page, pageSize);
    long total = commentRepository.countByPostId(postId);

    List<CommentResponse> responses =
        comments.stream()
            .map(
                c -> {
                  Author a = c.author();
                  AuthorDto dto =
                      new AuthorDto(a.id(), a.username(), a.fullName(), a.avatarUrl());
                  return new CommentResponse(
                      c.id(), c.postId(), a.id(), c.content(), c.createdAt(), dto);
                })
            .toList();

    return new CommentListResponseDto(responses, total, page, pageSize);
  }

  @Override
  @Transactional
  public void deleteComment(String postId, String commentId, String userId) {
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new CommentNotFoundException("Comment not found: " + commentId));

    // Regla de autorización: Solo el autor del comentario o el dueño del post pueden eliminarlo
    boolean isCommentAuthor = comment.author().id().equals(userId);
    boolean isPostOwner = post.userId().equals(userId);

    if (!isCommentAuthor && !isPostOwner) {
      throw new UnauthorizedCommentActionException(
          "User " + userId + " is not authorized to delete comment " + commentId);
    }

    commentRepository.deleteById(commentId);
  }
}
```

---

### 3.7. Listener de Notificaciones (Bounded Context Notificaciones)
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/notifications/application/listener/CommentNotificationEventListener.java`

Actualizado conforme a la arquitectura de notificaciones en tiempo real SSE integrada en `develop` (#129 / `1ab99e4`). Despacha tanto a SSE (in-app en tiempo real si el usuario tiene pestaña abierta) como a Web Push (notificación nativa en segundo plano).

```java
package com.wyrdly.notifications.application.listener;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.CommentCreatedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class CommentNotificationEventListener {

  private static final Logger LOG =
      Logger.getLogger(CommentNotificationEventListener.class.getName());

  static final String NOTIFICATION_TYPE = "POST_COMMENT";
  static final String TITLE = "Nuevo comentario en tu publicación";
  static final String DEEP_LINK_PREFIX = "/feed#post-";
  static final int MAX_SNIPPET_LENGTH = 140;

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;
  private final UserProfileRepository userProfileRepository;
  private final NotificationBroadcasterPort broadcaster;

  @Inject
  public CommentNotificationEventListener(
      PushDispatcherPort dispatcher,
      NotificationRepository notificationRepository,
      @ResilientNeo4j UserProfileRepository userProfileRepository,
      NotificationBroadcasterPort broadcaster) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
    this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster must not be null");
  }

  public void on(@Observes CommentCreatedEvent event) {
    Objects.requireNonNull(event, "event must not be null");

    // Ignorar auto-comentarios (el autor de la publicación no recibe notificación de su propio comentario)
    if (event.commentAuthorId().equals(event.postAuthorId())) {
      return;
    }

    FollowerSummary authorSummary = resolveActorSummary(event.commentAuthorId());
    String authorName =
        (authorSummary != null
                && authorSummary.fullName() != null
                && !authorSummary.fullName().isBlank())
            ? authorSummary.fullName()
            : null;

    int limit = Math.min(event.content().length(), MAX_SNIPPET_LENGTH);
    String snippet = event.content().substring(0, limit);
    String body = authorName != null ? authorName + " comentó: \"" + snippet + "\"" : "Nuevo comentario: \"" + snippet + "\"";
    String deepLink = DEEP_LINK_PREFIX + event.postId();

    Notification notification =
        new Notification(
            nextId(),
            event.postAuthorId(),
            NOTIFICATION_TYPE,
            event.commentAuthorId(),
            TITLE,
            body,
            deepLink,
            event.postId(),
            false,
            Instant.now());

    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      LOG.log(Level.WARNING, "Failed to persist comment notification", persistError);
    }

    // 1. Difusión reactiva en tiempo real por SSE para pestañas activas
    NotificationDto.ActorDto actorDto = buildActorDto(event.commentAuthorId(), authorSummary);
    NotificationDto ssePayload = NotificationDto.from(notification, actorDto);
    try {
      broadcaster.broadcast(event.postAuthorId(), ssePayload);
    } catch (RuntimeException sseError) {
      LOG.log(Level.FINE, "Failed to broadcast comment notification to SSE", sseError);
    }

    // 2. Despacho asíncrono Web Push para notificaciones nativas en segundo plano
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("postId", event.postId());
    data.put("commentId", event.commentId());
    data.put("commentAuthorId", event.commentAuthorId());

    PushEvent push =
        new PushEvent(event.postAuthorId(), NOTIFICATION_TYPE, TITLE, body, deepLink, data);
    dispatcher.dispatch(push);
  }

  private FollowerSummary resolveActorSummary(String actorId) {
    if (actorId == null) {
      return null;
    }
    try {
      var actors = userProfileRepository.findProfileSummariesByIds(Set.of(actorId));
      return actors.get(actorId);
    } catch (RuntimeException lookupError) {
      LOG.log(Level.WARNING, "Failed to resolve comment author name for " + actorId, lookupError);
      return null;
    }
  }

  private static NotificationDto.ActorDto buildActorDto(String actorId, FollowerSummary actorSummary) {
    if (actorId == null) {
      return null;
    }
    if (actorSummary == null) {
      return NotificationDto.ActorDto.placeholder(actorId);
    }
    return new NotificationDto.ActorDto(
        actorSummary.id(),
        actorSummary.username(),
        actorSummary.fullName(),
        actorSummary.avatarUrl(),
        null);
  }

  private static String nextId() {
    return "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
```

---

### 3.8. Adaptador Neo4j de Comentarios
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/infrastructure/persistence/Neo4jCommentRepositoryAdapter.java`

- Usa `session.executeWrite(...)` y `session.executeRead(...)`.
- Consultas Cypher:
  - **save**:
    ```cypher
    MATCH (u:Usuario {id: $authorId})
    MATCH (p:Post {id: $postId})
    CREATE (u)-[:ESCRIBE]->(c:Comentario {
      id: $id,
      postId: $postId,
      content: $content,
      createdAt: datetime($createdAt)
    })-[:EN_POST]->(p)
    RETURN c.id
    ```
  - **findByPostId**:
    ```cypher
    MATCH (u:Usuario)-[:ESCRIBE]->(c:Comentario {postId: $postId})-[:EN_POST]->(p:Post)
    RETURN c.id AS id, c.postId AS postId, c.content AS content, c.createdAt AS createdAt,
           u.id AS authorId, u.username AS authorUsername,
           u.fullName AS authorFullName, u.avatarUrl AS authorAvatarUrl
    ORDER BY c.createdAt ASC
    SKIP $skip LIMIT $limit
    ```
  - **countByPostId**:
    ```cypher
    MATCH (c:Comentario {postId: $postId})
    RETURN count(c) AS total
    ```
  - **deleteById**:
    ```cypher
    MATCH (c:Comentario {id: $commentId})
    DETACH DELETE c
    ```

---

### 3.9. Optimización de `FEED_QUERY` en `Neo4jPostRepositoryAdapter`
En vez de agregar un `OPTIONAL MATCH` cartesiano con las reacciones, usar subquery `COUNT`:
```cypher
COUNT { (p)<-[:EN_POST]-(:Comentario) } AS commentsCount
```
Esto calcula el grado de relaciones en $O(1)$ sin generar productos cartesianos con los `REACCIONA`.

---

### 3.10. Controlador REST: `CommentResource`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/interfaces/rest/CommentResource.java`

```java
package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.application.dto.CommentListResponseDto;
import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;
import com.wyrdly.post.application.usecase.CreateCommentUseCase;
import com.wyrdly.post.application.usecase.DeleteCommentUseCase;
import com.wyrdly.post.application.usecase.ListCommentsByPostUseCase;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/posts/{postId}/comments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CommentResource {

  private final CreateCommentUseCase createCommentUseCase;
  private final ListCommentsByPostUseCase listCommentsByPostUseCase;
  private final DeleteCommentUseCase deleteCommentUseCase;
  private final JsonWebToken jwt;

  @Inject
  public CommentResource(
      CreateCommentUseCase createCommentUseCase,
      ListCommentsByPostUseCase listCommentsByPostUseCase,
      DeleteCommentUseCase deleteCommentUseCase,
      JsonWebToken jwt) {
    this.createCommentUseCase = Objects.requireNonNull(createCommentUseCase, "createCommentUseCase");
    this.listCommentsByPostUseCase =
        Objects.requireNonNull(listCommentsByPostUseCase, "listCommentsByPostUseCase");
    this.deleteCommentUseCase = Objects.requireNonNull(deleteCommentUseCase, "deleteCommentUseCase");
    this.jwt = Objects.requireNonNull(jwt, "jwt");
  }

  @POST
  @Authenticated
  public Response createComment(
      @PathParam("postId") String postId,
      @Valid CreateCommentRequest request) {
    String userId = jwt.getSubject();
    CommentResponse response = createCommentUseCase.createComment(postId, userId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @GET
  public Response getComments(
      @PathParam("postId") String postId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("pageSize") @DefaultValue("20") int pageSize) {
    CommentListResponseDto response = listCommentsByPostUseCase.getComments(postId, page, pageSize);
    return Response.ok(response).build();
  }

  @DELETE
  @Path("/{commentId}")
  @Authenticated
  public Response deleteComment(
      @PathParam("postId") String postId,
      @PathParam("commentId") String commentId) {
    String userId = jwt.getSubject();
    deleteCommentUseCase.deleteComment(postId, commentId, userId);
    return Response.noContent().build();
  }
}
```

---

### 3.11. Filtro de Rate Limit en Redis: `CommentRateLimitFilter`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/interfaces/rest/CommentRateLimitFilter.java`

```java
package com.wyrdly.post.interfaces.rest;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Provider
@Priority(Priorities.AUTHENTICATION + 200)
public class CommentRateLimitFilter implements ContainerRequestFilter {

  static final long CAPACITY = 10L;
  static final long WINDOW_SECONDS = 60L;

  @Inject RedisDataSource redis;

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    if (!"POST".equalsIgnoreCase(req.getMethod())) {
      return;
    }
    String path = req.getUriInfo().getPath();
    if (path == null || !path.matches("^/?api/posts/[^/]+/comments/?$")) {
      return;
    }

    String userId = currentUserId(req);
    if (userId == null) {
      return;
    }
    String key = "ratelimit:comment:" + userId;

    long count;
    try {
      count = redis.execute("INCR", key).toLong();
      if (count == 1L) {
        redis.execute("EXPIRE", key, Long.toString(WINDOW_SECONDS));
      }
    } catch (Exception e) {
      Log.warnf(e, "Rate-limit cache unavailable for key=%s — failing open", key);
      return;
    }

    if (count > CAPACITY) {
      req.abortWith(
          Response.status(429)
              .header("Retry-After", Long.toString(WINDOW_SECONDS))
              .entity("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many comments. Please wait a moment.\"}")
              .type("application/json")
              .build());
    }
  }

  private static String currentUserId(ContainerRequestContext req) {
    var sec = req.getSecurityContext();
    if (sec == null || sec.getUserPrincipal() == null) {
      return null;
    }
    if (sec.getUserPrincipal() instanceof JsonWebToken jwt) {
      String sub = jwt.getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }
    return sec.getUserPrincipal().getName();
  }
}
```

---

### 3.12. Mapeo de Excepciones REST en `PostExceptionMappers`
**Ruta:** `wyrdly-backend/src/main/java/com/wyrdly/post/interfaces/rest/PostExceptionMappers.java`

Agregar a la clase existente:
```java
  @ServerExceptionMapper
  public Response handleCommentNotFound(CommentNotFoundException ex) {
    return buildResponse(Response.Status.NOT_FOUND, "COMMENT_NOT_FOUND", ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleUnauthorizedCommentAction(UnauthorizedCommentActionException ex) {
    return buildResponse(Response.Status.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }
```

---

### 3.13. Actualización en `PostService.java`
En `PostService.createPost`, invocar explícitamente el constructor de 8 parámetros pasando `0L` al final:
```java
    return new PostResponse(
        saved.id(),
        saved.content(),
        saved.mediaUrl(),
        saved.createdAt(),
        new AuthorDto(author.id(), author.username(), author.fullName(), author.avatarUrl()),
        new PostResponse.ReactionCounts(0, 0, 0),
        null,
        0L);
```

---

## 4. Especificación Frontend (React + TanStack Query)

### 4.1. Tipos de Comentarios
**Ruta:** `wyrdly-frontend/src/types/comments.ts`

```typescript
import type { UserProfileSummary, ISO8601Timestamp } from "./domain";

export interface Comment {
  readonly id: string;
  readonly postId: string;
  readonly authorId: string;
  readonly content: string;
  readonly createdAt: ISO8601Timestamp;
  readonly author: UserProfileSummary;
}

export interface CommentListResponse {
  readonly data: readonly Comment[];
  readonly totalCount: number;
  readonly page: number;
  readonly pageSize: number;
}
```

---

### 4.1.1. Soporte de Notificaciones de Comentarios en UI
Con la integración del stream SSE de `develop` (#129), el frontend recibe eventos en tiempo real. Para visualizarlos:

1. **[`wyrdly-frontend/src/features/notifications/types/index.ts`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend/src/features/notifications/types/index.ts)**:
   Agregar `"POST_COMMENT"` a `NotificationType`:
   ```typescript
   export type NotificationType =
     | "POST_LIKE"
     | "POST_LOVE"
     | "POST_CELEBRATE"
     | "POST_BOOST"
     | "GRAPH_FOLLOW"
     | "CHAT_MESSAGE"
     | "POST_COMMENT";
   ```

2. **[`wyrdly-frontend/src/features/notifications/components/NotificationItem/NotificationItem.tsx`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend/src/features/notifications/components/NotificationItem/NotificationItem.tsx)**:
   Agregar a `TYPE_CONFIG`:
   ```typescript
     POST_COMMENT: {
       badgeClass: styles.badgeChat,
       icon: <MessageSquare className="w-2.5 h-2.5" />,
     },
   ```

---

### 4.2. Cliente API
**Ruta:** `wyrdly-frontend/src/api/commentsApi.ts`

```typescript
import { apiClient } from "./axios";
import type { Comment, CommentListResponse } from "../types/comments";

export const commentsApi = {
  async getComments(postId: string, page = 1, pageSize = 20): Promise<CommentListResponse> {
    const res = await apiClient.get<CommentListResponse>(
      `/api/posts/${encodeURIComponent(postId)}/comments?page=${page}&pageSize=${pageSize}`
    );
    return res.data;
  },

  async createComment(postId: string, content: string): Promise<Comment> {
    const res = await apiClient.post<Comment>(
      `/api/posts/${encodeURIComponent(postId)}/comments`,
      { content }
    );
    return res.data;
  },

  async deleteComment(postId: string, commentId: string): Promise<void> {
    await apiClient.delete(
      `/api/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`
    );
  },
};
```

---

### 4.3. Hooks TanStack Query
**Ruta:** `wyrdly-frontend/src/features/social/hooks/useComments.ts`

```typescript
// TODO(hu-migrate-social-to-tanstack): see ADR-007
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { commentsApi } from "../../../api/commentsApi";
import type { Comment, CommentListResponse } from "../../../types/comments";

export function useComments(postId: string, isEnabled = true) {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["comments", postId],
    queryFn: () => commentsApi.getComments(postId),
    enabled: isEnabled && Boolean(postId),
  });

  const createMutation = useMutation({
    mutationFn: (content: string) => commentsApi.createComment(postId, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["comments", postId] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (commentId: string) => commentsApi.deleteComment(postId, commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["comments", postId] });
    },
  });

  return {
    ...query,
    comments: query.data?.data ?? [],
    totalCount: query.data?.totalCount ?? 0,
    createComment: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    deleteComment: deleteMutation.mutateAsync,
    isDeleting: deleteMutation.isPending,
  };
}
```

---

### 4.4. Componentes UI (`CommentSection`)

Directorio: `wyrdly-frontend/src/features/social/components/CommentSection/`

1. **`CommentInput.tsx`**:
   - Textarea con contador de caracteres (0/1000).
   - Botón "Comentar" deshabilitado si está vacío, excede límite o `isCreating === true` (evita rage-click).
2. **`CommentItem.tsx`**:
   - Renderiza Avatar, nombre y username con enlace al perfil.
   - Botón borrar visible solo si `currentUserId === comment.authorId || currentUserId === postAuthorId`.
3. **`CommentList.tsx`**:
   - Lista de `CommentItem` o skeletons si está cargando.
   - Mensaje vacío si no hay comentarios.
4. **`CommentSection.tsx`**:
   - Wrapper que integra input y lista con `useComments(postId)`.
5. **Integración en [`PostCard.tsx`](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend/src/features/social/components/PostCard/PostCard.tsx)**:
   - Estado: `const [isCommentsOpen, setIsCommentsOpen] = useState(false);`
   - El botón Comment hace `setIsCommentsOpen(prev => !prev)` y llama `onCommentClick?.(post.id)`.
   - Render condicional `{isCommentsOpen && <CommentSection postId={post.id} postAuthorId={post.author.id} />}`.

---

## 5. Plan de Pruebas Detallado

### 5.1. Backend Unit Tests (Mockito)
- **`CommentServiceTest.java`**:
  - `createCommentSuccess()`
  - `createCommentFailsWhenPostNotFound()` $\rightarrow$ lanza `PostNotFoundException`
  - `deleteCommentSucceedsForCommentAuthor()`
  - `deleteCommentSucceedsForPostAuthor()`
  - `deleteCommentThrowsUnauthorizedForOtherUser()` $\rightarrow$ lanza `UnauthorizedCommentActionException`
  - `deleteCommentThrowsNotFoundWhenMissing()` $\rightarrow$ lanza `CommentNotFoundException`
- **`CommentNotificationEventListenerTest.java`**:
  - `dispatchesPushWhenAuthorIsDifferent()`
  - `doesNotDispatchPushForSelfComment()`

### 5.2. Backend REST Tests (`@QuarkusTest`)
- **`CommentResourceTest.java`**:
  - `POST /api/posts/{postId}/comments` retorna 201 Created.
  - `GET /api/posts/{postId}/comments` retorna 200 con paginación.
  - `DELETE /api/posts/{postId}/comments/{commentId}` retorna 204 para autor / 403 para ajeno.

### 5.3. Backend Integration Tests (Testcontainers)
- **`Neo4jCommentRepositoryAdapterIT.java`**:
  - `savePersistsCommentWithCorrectRelationships()`: verifica nodos `(:Comentario)` y relaciones `[:ESCRIBE]`, `[:EN_POST]`.
  - `findByPostIdReturnsOrderedComments()`: valida orden ascendente por `createdAt` y paginación.
  - `countByPostIdReflectsAllComments()`: valida conteo exacto de comentarios del post.
  - `deleteByIdDetachesAndDeletes()`: valida borrado atómico con `DETACH DELETE`.

### 5.4. Frontend Vitest Tests
- `useComments.test.ts`: llamada a API, invalidación y estados.
- `CommentSection.test.tsx`: renderizado, input y submit.
- `PostCard.test.tsx`: apertura de panel al hacer clic en botón de comentarios.

---

## 6. Checklist de Verificación para el Agente Ejecutor

- [ ] Aplicar migración `V006__add_comment_schema_and_indexes.cypher`.
- [ ] Backend:
  - `./mvnw spotless:apply`
  - `./mvnw test -Dtest=CommentServiceTest,CommentResourceTest,CommentNotificationEventListenerTest,FeedServiceTest,PostServiceTest`
  - Suite completa: `./mvnw test` (cero regresiones, 251+ tests OK).
- [ ] Frontend:
  - `pnpm format:check`
  - `pnpm lint`
  - `pnpm test`
- [ ] Commits convencionales atómicos sin co-autorías de IA.
