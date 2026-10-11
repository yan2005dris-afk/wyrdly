# Feature: Web Push hardening — 6 garantías faltantes + 3 adicionales

## Objetivo

Cerrar las brechas funcionales y de seguridad que la issue
[#181](https://github.com/yan2005dris-afk/wyrdly/issues/181) identifica
contra el repo de referencia `red-social-distribuida`, partiendo del
diagnóstico confirmado en el review previo:

- Backend acepta cualquier endpoint push (incluido `http://localhost`).
- Frontend no valida shape de clave VAPID pública.
- Frontend no chequea `isSecureContext` ni `'caches' in window`.
- Frontend no detecta cambio de sesión → suscripciones quedan apuntando
  al user anterior (push entregado al usuario equivocado).
- Frontend no persiste estado de suscripción en Cache API.
- Frontend no usa `navigator.locks.request` para serializar enable/disable.
- Frontend no maneja rotación VAPID de clave.
- Frontend no limpia estado del browser (`clearLocal()`) en error path.
- `disablePush` no ataja cuando la suscripción guardada pertenece a otro user.

## Por qué

El review de evidencia (commits 58–73 de HU11 más `sw.test.ts`,
`useWebPush.test.ts`, `PushDispatcherImplTest`, `MessageEncryptorTest`,
`VapidJwtSignerTest`) muestra que la **infraestructura** está cubierta
por unidad pero **la cadena E2E nunca se validó contra un push gateway
real**: el test E2E actual mockea `showNotification` desde la página.
Las defensas que el referencia trae y wyrdly no, son las que evitan los
3 síntomas típicos que el equipo reporta:

| Síntoma reportado | Defensa que falta (issue #181) |
|---|---|
| `InvalidAccessError` silencioso al suscribir | #2 — assert de 65 bytes + prefijo `0x04` |
| Push llega al usuario equivocado tras login/logout | #4 — `assertSession(userId, token)` |
| Falla sin error claro en HTTP/túnel | #3 — `isSecureContext` check |

Sumado a esto: el backend hoy acepta `http://localhost:9999/mock-push`
como endpoint válido (documentado en el runbook §4.A), lo que en
producción habilita data exfiltration vía webhook arbitrario.

## Alcance Autorizado

### PR 1 — Seguridad + correctness (rama `feat/push-hardening-1-security`)

- **Backend `PushEndpointValidator`**: re-implementar (no copiar) el
  filtro del referencia, **descartando las dos líneas muertas** del
  original (`uri.getPort() == 0` y `uri.getPort() > 65535`).
  Rechaza: scheme ≠ `https`, host null/localhost/IPv6, TLD `.local`/`.internal`,
  userInfo/fragment presentes, dominio con etiqueta inválida
  (RFC 5890), longitud > 253, menos de 2 labels.
- **Backend `SubscribeToPushUseCaseImpl`**: invocar el validator antes
  de persistir; actualizar `SubscribeToPushUseCaseImplTest` para cubrir
  los nuevos casos (HTTPS válido, `http://`, `localhost`, `.local`, IPv6,
  userInfo, fragment, URI malformada).
- **Frontend `content/push-copy.ts`** (directorio `content/` nuevo):
  mensajes de error y estado (`unsupported`, `denied`, `browserError`,
  `changedSession`).
- **Frontend `lib/push-cache.ts`** (directorio `lib/` nuevo): helpers
  `getOwner`, `setOwner`, `clearLocal` (cierra notificaciones,
  borra cache, `pushManager.unsubscribe()`).
- **Frontend `lib/push-session.ts`**: `assertSession(userId, token)`
  contra `useAuthStore.getState()`.
- **Frontend `types/index.ts`**: nuevo `PushErrorCode` union.
- **Frontend `useWebPush.ts`** refactor mayor:
  - Multi-punto `assertSession` antes/después de cada operación crítica.
  - Estado de subscripción persistido en `caches.open("wyrdly-push-v1")`
    con `"/__wyrdly_push_state__"` = `{userId, endpoint}`.
  - Detección de rotación VAPID: si `applicationServerKey` de la
    suscripción existente difiere de la actual, `clearLocal()` +
    re-subscribe.
  - `clearLocal()` en cada path de error.
  - `pushError()` translation helper.
- **Frontend `useWebPush.test.ts`**: cobertura para los puntos de arriba.

### PR 2 — UX en dev/prod (rama `feat/push-hardening-2-ux`)

Branched off PR 1.

- **Frontend `useWebPush.ts::base64UrlToUint8Array`**: assert que el
  resultado tiene `length === 65 && bytes[0] === 0x04` antes de
  retornar. Si falla, lanzar `PushErrorCode.INVALID_VAPID_KEY`.
- **Frontend `useWebPush.ts::detectSupport`**: agregar
  `window.isSecureContext === true` y `'caches' in window` como
  precondiciones.
- **Frontend `PushPermissionBanner.tsx`**: aceptar prop opcional
  `reason: "unsupported" | "denied" | "insecure-context"` y mostrar
  mensaje específico cuando `insecure-context`.
- **Frontend tests**: cubrir clave VAPID malformada, contexto no seguro,
  Cache API ausente, mensaje del banner en cada estado.

### PR 3 — Polish (rama `feat/push-hardening-3-polish`)

Branched off PR 2.

- **Frontend `useWebPush.ts::subscribe/unsubscribe`**: envolver el
  cuerpo en `navigator.locks.request("wyrdly-push", action)` (con
  fallback si `navigator.locks` no existe).
- **Frontend `useWebPush.ts::unsubscribe`**: si el `owner` guardado
  tiene `userId !== userId actual`, retornar `true` sin tocar backend.
- **Frontend tests**: dos `subscribe()` concurrentes → solo se ejecuta
  uno; `unsubscribe()` cross-user → no-op.

## Tareas adicionales (post-stack)

### `feat(profile): add WebPushSettingsCard as a Settings tab on ProfilePage`

- [x] **`TASK-12`** — Componente `features/profile/components/WebPushSettingsCard/` con
      badge `Habilitado / Deshabilitado / Bloqueado por el navegador / No disponible`,
      toggle `Habilitar / Deshabilitar` y copy de razón via `pushCopy`.
- [x] **`TASK-13`** — Nuevo tab `Settings` en `ProfileHeaderCard` visible solo
      cuando `isCurrentUser`. `ProfileTabId` extendido con `"settings"`.
- [x] **`TASK-14`** — `ProfilePage` renderiza `<WebPushSettingsCard />` cuando
      `activeTab === "settings" && isCurrentUser`.
- [x] **`TASK-15`** — Tests (5 casos: default disabled, after enable, disable
      llama backend, denied, unsupported).

El usuario pidió "esto del permiso de web push en perfil pon ahi la
aceptacion como configuracion de web push enable o disable". El banner
del MainLayout sigue existiendo para el primer opt-in; esta card es la
configuracion persistente para habilitar/deshabilitar a posteriori.

## Convenciones

- Conventional Commits en español consistente con el módulo `push` previo:
  `feat(push): ...`, `test(push): ...`, `refactor(push): ...`.
- Branch por PR, nombrado `feat/push-hardening-{1,2,3}-{security,ux,polish}`.
- Work-unit commits: PR 1 se desglosa en al menos 4 commits
  (validator backend + integración, push-copy+types, push-cache+push-session
  helpers, useWebPush refactor + tests).
- Tests: backend usa JUnit + AssertJ, frontend Vitest + Testing Library.
- Spotless + ESLint verdes antes de cada commit.
- Sin copiar código del referencia: solo el patrón.
- Push, PR creation y merge quedan a decisión del usuario.

## Tareas

### PR 1
- [ ] `TASK-01` — Backend: `PushEndpointValidator` + `PushEndpointValidatorTest`.
- [ ] `TASK-02` — Backend: invocar validator en `SubscribeToPushUseCaseImpl`,
       actualizar `SubscribeToPushUseCaseImplTest`.
- [ ] `TASK-03` — Frontend: `content/push-copy.ts` + `types/index.ts::PushErrorCode`.
- [ ] `TASK-04` — Frontend: `lib/push-cache.ts` + `lib/push-session.ts`.
- [ ] `TASK-05` — Frontend: refactor `useWebPush.ts` con assertSession,
       Cache API, clearLocal, rotación VAPID, pushError.
- [ ] `TASK-06` — Frontend: actualizar `useWebPush.test.ts`.

### PR 2
- [ ] `TASK-07` — `base64UrlToUint8Array`: assert 65+0x04 + tests.
- [ ] `TASK-08` — `detectSupport`: `isSecureContext` + `'caches' in window` + tests.
- [ ] `TASK-09` — `PushPermissionBanner`: prop `reason` + tests.

### PR 3
- [ ] `TASK-10` — `navigator.locks.request` en subscribe/unsubscribe + tests.
- [ ] `TASK-11` — `unsubscribe` cross-user no-op + tests.

## Evidencia de Verificación

- Backend: `mvn -pl wyrdly-backend test` verde, incluyendo
  `PushEndpointValidatorTest` y `SubscribeToPushUseCaseImplTest`.
- Frontend: `pnpm -F @wyrdly/frontend test useWebPush push-cache push-session`
  verde, sin warnings.
- Frontend build: `pnpm -F @wyrdly/frontend build` OK.
- Spotless: `mvn -pl wyrdly-backend spotless:check` verde.
- ESLint: `pnpm -F @wyrdly/frontend lint` verde.

## Riesgos

- **El refactor de `useWebPush` es grande**. Mitigación: tareas
  TASK-04 → TASK-05 → TASK-06 en commits separados para mantener
  diffs revisables.
- **Tests E2E siguen mockeados**. Este feature no incluye validación
  contra push gateway real; queda para un PR separado (issue #176
  menciona esto tangencialmente).
- **Wyrdly no tiene session en `getSession()` al estilo del referencia**.
  `assertSession` usa `useAuthStore.getState()` (Zustand) que expone
  `{user, token}` sincrónicamente — equivalente en este stack.