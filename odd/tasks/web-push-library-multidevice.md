# Feature: web-push-library-multidevice

## Objective
Migrar el delivery Web Push a librería probada (`nl.martijndwars:web-push`) y a modelo multi-device (N suscripciones por usuario), sin romper las que andan.

## Why
Crypto propia = riesgo de mantenimiento; triple único por usuario = un dispositivo pisa al otro. El proyecto de referencia usa librería + nodos por endpoint.

## Scope
- `wyrdly-backend`: dependencia `web-push` + BouncyCastle, adapter sobre `PushDispatcherPort`/`PushGatewayClientPort`, conversión PKCS#8 -> scalar crudo o regeneración con runbook de rotación VAPID.
- Neo4j: modelo `(:Usuario)-[:TIENE_SUSCRIPCION]->(:SuscripcionPush)` (o equivalente), límite por usuario, DELETE por endpoint.
- `wyrdly-frontend`: subscribe/unsubscribe por endpoint, owner por endpoint, tests.
- Docs: runbook de rotación + matriz de verificación.

## Constraints
- Rama `refactor/web-push-library-multidevice` desde `develop@c99a933`.
- Nada a `develop` sin PR verde; rotación VAPID avisa y re-suscribe.
- Route: inline degradado (subagent denegado en este runtime); triggers documentados por tarea.

## Checklist
- [x] T1: Inventario backend (crypto, dispatcher, repo, tests) + decisión formato de clave (route: inline)
- [x] T2: Adapter con librería + tests backend (route: inline)
- [x] T3: Modelo multi-device Neo4j + migración de la triple actual (route: inline)
- [ ] T4: Frontend por endpoint + tests (528 base) (route: inline)
- [ ] T5: Runbook rotación + verificación E2E (route: inline)

## Authorized scope
Autorizado por usuario: rama nueva para el refactor. Sin cambios de producto fuera de push.

## Acceptance
- `mvn test -Dtest=PushDispatcher*` verde, 528 vitest verdes, lint+prettier limpios.
- Dos browsers mismo usuario reciben push a la vez.
- Gateway `ok` medido por endpoint.

## Progress
- 2026-10-12: rama creada desde develop, doc inicial.
- 2026-10-12: T1+T2 hechos. web-push 5.1.1 + bcprov. WebPushLibrarySender convierte PKCS#8->scalar en runtime (sin rotación). Dispatcher conserva rate-limit/métricas/retry; `failed` vs `network_error` clasificados. 174 tests notificaciones verdes.
- 2026-10-12: T3 hecho. Nodos :SuscripcionPush + V104 (validada en IT), fan-out por dispositivo, gone limpia solo el endpoint, DELETE ?endpoint=. Fanout conserva 1 sub por follower. 182 tests verdes.

## Next step
T1 inventario.
