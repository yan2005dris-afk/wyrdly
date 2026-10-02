# Feature: Chat Bidireccional en Tiempo Real (WebSocket & Historial REST) y Conectividad Frontend

## Objetivo
Corregir los defectos arquitecturales, eliminar código muerto de `yaga-backend`, subsanar la vulnerabilidad de validación JWT, unificar el modelo de datos Neo4j según PRD HU10 (`(:Usuario)-[:ENVIA]->(:Mensaje)-[:DIRIGIDO_A]->(:Usuario)`), desacoplar el bounded context `user` de `chat`, migrar las pruebas al backend oficial y conectar la funcionalidad en tiempo real con el Frontend (`ChatPage.tsx`).

## Problema
El PR #53 introdujo código duplicado bajo una carpeta `yaga-backend`, no validó criptográficamente las firmas JWT en el handshake del WebSocket (usando un split de string manual inseguro), utilizó etiquetas y relaciones en Neo4j inconsistentes con el PRD y las migraciones (`:Message` vs `:Mensaje`), acopló directamente `FollowUserUseCaseImpl` con `chat`, omitió pruebas en `wyrdly-backend` e incumplió los códigos de error del PRD (`4401` en lugar de `1001`). Además, el frontend mantiene mocks desconectados del backend.

## Por qué
El módulo de chat es un componente crítico de comunicación directa en la plataforma Wyrdly. La persistencia en grafo y el streaming bidireccional sobre WebSocket deben seguir estrictamente la arquitectura hexagonal, los estándares de seguridad SmallRye JWT de Quarkus y proveer una experiencia fluida e integrada en la SPA de React.

## Alcance Autorizado
1. **Limpieza & Unificación del Repositorio:**
   - Eliminar definitivamente el directorio obsoleto `yaga-backend/`.
   - Limpiar dependencias no utilizadas en `wyrdly-backend/pom.xml` (`jjwt`).
2. **Seguridad & WebSocket Handshake:**
   - Validar tokens JWT en `@ServerEndpoint("/ws/chat")` utilizando `JWTParser` de SmallRye JWT.
   - Enviar código de cierre `4401` ante token inválido o ausente.
   - Usar evento estándar `NEW_MESSAGE` para entrega en tiempo real.
3. **Persistencia en Grafo Neo4j (PRD HU10):**
   - Corregir `Neo4jDirectMessageRepositoryAdapter` para persistir `(:Usuario)-[:ENVIA]->(:Mensaje)-[:DIRIGIDO_A]->(:Usuario)` con `id`, `content`, `createdAt`.
   - Alinear la migración `V002__add_message_indexes.cypher` para indexar `:Mensaje` sobre `createdAt`.
4. **Desacoplamiento de Bounded Contexts:**
   - Remover la dependencia directa de `user` hacia `chat` (`FollowValidationPort`).
   - Mantener las interfaces de consulta de seguimiento sin inversión de control indebida.
5. **Migración de Pruebas a `wyrdly-backend`:**
   - Migrar y ejecutar los tests unitarios e integrados de REST y WebSocket dentro de `wyrdly-backend/src/test/`.
6. **Conectividad Frontend (SPA React):**
   - Implementar servicio / hook de WebSocket y API de historial REST para `ChatPage.tsx` con soporte para mensajes en tiempo real.

## Tareas

- [x] **TASK-01**: Limpieza de `yaga-backend/` y dependencias JJWT innecesarias en `pom.xml`.
- [x] **TASK-02**: Corrección del modelo de persistencia Neo4j (`:Mensaje`, `[:ENVIA]`, `[:DIRIGIDO_A]`, `createdAt`) e índices en migraciones.
- [x] **TASK-03**: Refactor de seguridad con `JWTParser` y corrección de código de cierre `4401` y evento `NEW_MESSAGE` en `ChatWebSocketEndpoint`.
- [x] **TASK-04**: Desacoplamiento arquitectural del módulo `user` respecto a `chat`.
- [x] **TASK-05**: Migración y validación de pruebas unitarias/integración de chat en `wyrdly-backend`.
- [x] **TASK-06**: Conectividad en Frontend: servicio REST de historial, cliente WebSocket en `features/chat`, carga de seguidos reales e integración de navegación desde perfil a `ChatPage.tsx`.

## Evidencia de Verificación
- Backend: 133 pruebas pasando (0 fallos, 0 errores, 0 saltadas).
- Frontend: 223 pruebas en 46 suites pasando (0 fallos).
- Linting y formateo: Spotless (Java), ESLint y Prettier en verde.
- Build: Vite production bundle generado exitosamente.
