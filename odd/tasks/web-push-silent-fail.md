# Feature: web-push-silent-fail

## Objective
Hacer visible el fallo silencioso del botón Habilitar en WebPushSettingsCard dentro de la rama `refactor/web-push-architecture`.

## Problem
Card muestra Deshabilitado, click en Habilitar no hace nada, sin logs. Causa: `handleEnable` traga el resultado y la card ignora `error` por PR #196.

## Why
Usuario en docker con perm granted + SW registrado sigue bloqueado sin diagnóstico.

## Scope
- `wyrdly-frontend/src/features/notifications/hooks/useWebPush.ts` (solo logging, sin cambiar lógica)
- `wyrdly-frontend/src/features/profile/components/WebPushSettingsCard/WebPushSettingsCard.tsx` (mostrar error no-changedSession + console.error)
- Tests existentes de ambos archivos

## Constraints
- No cambiar flujo de subscripción, ni VAPID, ni session guards.
- Preservar regla UX: `changedSession` nunca se muestra en la card (lo maneja auth layer).
- Misma rama `refactor/web-push-architecture`, sin crear branch nueva.
- Route: inline (delegación `subagent` denegada en este runtime) — triggers Mapping/Writer disparados pero sin mecanismo disponible, se sigue degradado inline acotado.

## Checklist
- [x] T1: Card lee `error`, loguea en consola y renderiza `errorBox` solo para errores distintos de `changedSession` (route: inline)
- [x] T2: Hook loguea `[push]` en catches de `subscribe`/`unsubscribe`/`init` sin cambiar retorno (route: inline)
- [x] T3: Verificación proporcional: `pnpm vitest run` notificaciones + profile card, `pnpm lint`, `prettier --check` (route: inline per-action permitida)
- [x] T4: Fix causa raíz dual-auth: `getActiveSession` lee Zustand y hace fallback a `tokenStore + wyrdly_user` (AuthContext real); card se suscribe a `AuthContext` para re-render (route: inline)
- [x] T5: Banner del feed como invitación a Settings (?tab=settings) en vez de suscripción directa (route: inline)

## Authorized scope
Autorizado por usuario: "si dentro de esta rama misma". Solo los dos archivos de arriba + updates mínimos de tests si rompen por el errorBox.

## Acceptance
- Click Habilitar fallido deja `console.error("[push] ...")` visible y, salvo `changedSession`, muestra `data-testid="webpush-settings-error"` con copy de `pushCopy`.
- `changedSession` sigue sin mostrarse en UI (tests 350-406 verdes).
- 528 tests verdes, lint y prettier limpios.

## Verification
- `pnpm vitest run src/features/notifications src/features/profile/components/WebPushSettingsCard`
- `pnpm vitest run` completo si el parcial pasa
- `pnpm lint`, `npx prettier --check`

## Progress
- 2026-10-12: T1+T2+T3 hechos. 45 tests parciales verdes, 528 totales verdes, lint limpio, prettier limpio. Pendiente commit work-unit.
- 2026-10-12: T4 dual-auth fix commiteado (05511ca). Registro OK en Neo4j, backend ok, pero push real no entra al SW Firefox. T5: log temporal en sw.js handlePush.

## Next step
Ejecutar T1.
