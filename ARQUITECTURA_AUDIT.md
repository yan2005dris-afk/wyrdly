# Audit Arquitectura - Solución Chat + Follow

**Fecha:** 2026-09-30  
**Componente:** Validación bidireccional de follow en chat  
**Nivel:** HIGH DETAIL

---

## ✅ FORTALEZAS

### Arquitectura en Capas
```
domain/       → Modelos, repositorios (interfaces), excepciones
application/  → UseCases, DTOs, servicios
infrastructure/ → Adapters persistencia (Neo4j)
interfaces/   → REST, WebSocket
```
**Valoración:** ✅ Excelente. Separación clara de responsabilidades.

### Inyección de Dependencias
- Framework: Jakarta CDI (@Inject, @ApplicationScoped)
- **Punto fuerte:** No hay `new` en lógica de negocio
- Facilita testing y reemplazar implementaciones
**Valoración:** ✅ Excelente.

### Excepciones Custom
- `UsersNotFollowingException` → HTTP 403 automático
- Mapeadas vía `ChatExceptionMappers`
- Flujo limpio error → HTTP response
**Valoración:** ✅ Buena práctica.

### Validación en Modelos
```java
// DirectMessage.java
public void validateContent() { ... }
public void validateRecipient() { ... }
```
**Valoración:** ✅ Encapsulación correcta de reglas de negocio.

---

## ⚠️ PROBLEMAS CRÍTICOS

### 1. ACOPLAMIENTO INTER-MÓDULOS (Crítico)

**Ubicación:** SendMessageUseCaseImpl.java, GetChatHistoryUseCaseImpl.java

```java
// ❌ PROBLEMA: Import desde módulo user
import com.yaga.user.domain.repository.UserProfileRepository;

@Inject
UserProfileRepository userProfileRepository; // Dependencia del módulo 'user'
```

**Impacto:**
- Módulo `chat` depende de módulo `user`
- Si `user` cambia interfaz, `chat` se rompe
- No es escalable para múltiples módulos que validan follow
- Viola principio de independencia modular

**Solución recomendada:**
```
1. Crear interfaz de validación follow en módulo chat:
   FollowValidationPort (application/port/)
   
2. Implementar en módulo user:
   UserFollowValidationAdapter (infrastructure/)
   
3. Inyectar puerto en UseCase (no repositorio específico)
   
Resultado: chat → FollowValidationPort ← user
          (desacoplamiento via puertos)
```

---

### 2. PERFORMANCE EN PRODUCCIÓN (Crítico)

**Ubicación:** SendMessageUseCaseImpl.validateFollowRelationship() líneas 38-46

```java
boolean senderFollowsRecipient = userProfileRepository.isFollowing(senderId, recipientId);
boolean recipientFollowsSender = userProfileRepository.isFollowing(recipientId, senderId);
```

**Problema:** 2 queries Neo4j por CADA mensaje

**Escenario de carga:**
- 100 usuarios activos
- 10 mensajes/segundo promedio
- = **20 queries/segundo solo de validación**
- Cada query a Neo4j ~5-10ms
- = 100-200ms de latencia adicional

**Impacto:** ❌ No escalable. Cuello de botella crítico.

**Solución recomendada:**
```java
// Opción 1: Cache local con TTL
@ApplicationScoped
public class FollowValidationCache {
  private final Map<String, Pair<Boolean, Long>> cache = new ConcurrentHashMap<>();
  private static final long TTL_MS = 5 * 60 * 1000; // 5 min
  
  public boolean areFollowingEachOther(String userId1, String userId2) {
    String key = userId1 + ":" + userId2;
    Pair<Boolean, Long> cached = cache.get(key);
    
    if (cached != null && System.currentTimeMillis() - cached.getRight() < TTL_MS) {
      return cached.getLeft();
    }
    
    boolean result = repository.isFollowing(userId1, userId2);
    cache.put(key, new Pair<>(result, System.currentTimeMillis()));
    return result;
  }
}

// Opción 2: Single Neo4j query (mejor)
// Query: MATCH (a:User {id: $userId1})-[:SIGUE]->(b:User {id: $userId2})
//        AND (b)-[:SIGUE]->(a)
//        RETURN EXISTS(...) AS areMutual
```

---

### 3. SEGURIDAD JWT EN WEBSOCKET (Crítico)

**Ubicación:** ChatWebSocketEndpoint.java líneas 161-182

```java
private String extractUserIdFromToken(String token) {
  try {
    String[] parts = token.split("\\.");
    if (parts.length != 3) return null;
    
    String payload = parts[1];
    byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(payload);
    String decodedPayload = new String(decodedBytes, StandardCharsets.UTF_8);
    
    Pattern pattern = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(decodedPayload);
    
    if (matcher.find()) {
      return matcher.group(1);
    }
  } catch (Exception e) {
    LOGGER.warning("Failed to decode JWT: " + e.getMessage());
  }
  return null;
}
```

**Problemas:**
- ❌ NO valida firma del JWT
- ❌ Parsing manual con regex (frágil, inseguro)
- ❌ No valida expiration
- ❌ No valida issuer (iss)
- ❌ Acepta JWT tamperizado

**Ataque posible:**
```
Atacante: Modifica "sub" en payload sin firma válida
Servidor: Lo acepta porque no valida firma
Resultado: Acceso como usuario falso
```

**Solución recomendada:**
```java
// Usar Quarkus JWT verificación integrada
@Inject
JsonWebToken jwt; // Ya verificado por Quarkus

private String extractUserIdFromToken(String token) {
  try {
    // Parse y verifica automáticamente
    return Jwt.parse(token).getClaim("sub");
  } catch (Exception e) {
    return null; // Token inválido rechazado
  }
}

// O mejor aún: Usar en OnOpen del WebSocket
@OnOpen
public void onOpen(Session session) {
  String token = extractTokenFromQuery(session);
  String userId = extractUserIdFromToken(token); // Validado
  // ...
}
```

---

### 4. MANEJO DE EXCEPCIONES GENÉRICO (Importante)

**Ubicación:** ChatResource.java línea 59, ChatWebSocketEndpoint.java línea 89

```java
// ❌ PROBLEMA: Catch genérico
try {
  // ...
} catch (Exception e) {
  LOGGER.warning("Error: " + e.getMessage());
  return Response.status(500).entity(...).build();
}
```

**Problema:**
- Oculta excepciones específicas (UsersNotFollowingException)
- ExceptionMapper no se ejecuta
- Usuario recibe 500 en lugar de 403 FORBIDDEN
- Difícil debuggear

**Solución:**
```java
// Capturar específicamente
try {
  ChatHistoryPage history = getChatHistoryUseCase.execute(...);
  return Response.ok(history).build();
} catch (UsersNotFollowingException e) {
  // ExceptionMapper se ejecuta automáticamente
  throw e;
}
```

---

## ⚠️ PROBLEMAS MENORES

### 5. ObjectMapper Estático (Menor)

**Ubicación:** ChatWebSocketEndpoint.java línea 30

```java
private static final ObjectMapper objectMapper = new ObjectMapper();
```

**Problema:**
- Compartido entre todas las instancias
- Difícil testear
- Si se configura, afecta globalmente

**Solución:**
```java
@Inject
@Named("chatObjectMapper")
ObjectMapper objectMapper;
```

---

### 6. Logging Insuficiente (Menor)

**Ubicación:** ChatWebSocketEndpoint.java líneas 61, 131

```java
// ❌ Todo en INFO
LOGGER.info("WebSocket opened for user: " + userId);
LOGGER.info("Message sent from " + senderId + " to " + request.getRecipientId());
```

**Problema:**
- No hay audit de accesos denegados (follow validation)
- Difícil monitoreo en producción
- No distingue eventos críticos

**Solución:**
```java
// Audit de validación fallida
if (!isFollowingEachOther(senderId, recipientId)) {
  LOGGER.warning("SECURITY: User " + senderId + 
                 " intentó chatear sin follow con " + recipientId);
  throw new UsersNotFollowingException(...);
}

LOGGER.info("Message sent successfully: " + message.getId());
```

---

### 7. Sin Transacciones Explícitas (Menor)

**Ubicación:** SendMessageUseCaseImpl.java

```java
// ❌ Sin @Transactional
validateFollowRelationship(senderId, recipientId);
directMessageRepository.save(message);
```

**Problema:**
- Si `save()` falla, `validateFollowRelationship()` ya ejecutó
- Posible race condition: follow puede cambiar entre validación y save

**Solución:**
```java
@Transactional
public MessageResponse execute(String senderId, String recipientId, String content) {
  // Atomicidad garantizada
  DirectMessage message = new DirectMessage(...);
  message.validateContent();
  message.validateRecipient();
  validateFollowRelationship(senderId, recipientId);
  directMessageRepository.save(message);
  return new MessageResponse(...);
}
```

---

## 📊 RESUMEN PUNTUACIÓN

| Aspecto | Calificación | Nota |
|---------|-------------|------|
| **Arquitectura** | 9/10 | Capas limpias, buen uso de CDI |
| **Escalabilidad** | 3/10 | ❌ Critical: 2 queries/mensaje, sin cache |
| **Mantenibilidad** | 6/10 | ⚠️ Acoplamiento inter-módulos |
| **Seguridad** | 2/10 | ❌ Critical: JWT sin validar firma |
| **Buenas Prácticas** | 7/10 | Validación en modelos, excepciones custom |
| **Bajo Acoplamiento** | 4/10 | ❌ Critical: módulo chat depende de user |

**PROMEDIO:** 5.2/10 - **Requiere fixes antes de producción**

---

## 🚀 ROADMAP FIXES PRIORITARIOS

### CRÍTICO (Implementar ANTES de producción)
1. ✅ Validación JWT con firma en WebSocket (30 min)
2. ✅ Refactor: Crear FollowValidationPort en módulo chat (1 hora)
3. ✅ Implementar cache para relaciones follow (1.5 horas)
4. ✅ Remover catch genérico → dejar que ExceptionMappers funcione (15 min)

### IMPORTANTE (Próxima release)
5. Agregar @Transactional en SendMessageUseCase
6. Mejorar logging con niveles (WARN para accesos denegados)
7. Inyectar ObjectMapper en lugar de static

### NICE-TO-HAVE
8. Metrics/monitoring de validaciones de follow
9. Circuit breaker para UserProfileRepository
10. Batching de queries de follow

---

## Conclusión

**Estado:** Arquitectura sólida pero **NO LISTA PARA PRODUCCIÓN**

Problemas críticos:
- ❌ Seguridad JWT (validación de firma)
- ❌ Escalabilidad (queries sin cache)
- ❌ Acoplamiento inter-módulos

**Tiempo estimado fixes:** 3-4 horas
**Riesgo actual:** ALTO (producción vulnerable)
