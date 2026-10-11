# Lean env files — rama chore/lean-env-required-only

Base: develop @ 8c062a6. Objetivo: `.env.prod` (53 vars) y `.env.dev` (51 vars) quedan con solo las ~10 requeridas activas; el resto pasa a bloque `ADVANCED` comentado. Cero cambio de comportamiento: toda var comentada debe tener default en `compose.yaml` (`:-`) o `application.properties`.

## Alcance autorizado
- `.env.prod`, `.env.dev` (reordenar/comentar líneas; NUNCA cambiar valores)
- Este tracker. Nada más.

## Restricción de secretos
- Las herramientas de lectura bloquean `*.env`. Todo se hace value-blind: solo nombres de vars vía `rg -o`, transformación con script que no imprime valores, verificación por sets de nombres + `numstat`. El diff completo lo revisa el humano en el PR.

## Checklist
- [x] T1 Clasificar (2026-10-11, value-blind: solo nombres vía `rg -o`). Cruce: refs `${VAR:?}` en `compose.yaml` + bloque `%prod` sin default en `application.properties` + lista required de `deploy.yml` → coinciden exacto en 7.
  - REQUIRED activas (14): `NEO4J_PASSWORD`, `RUSTFS_ACCESS_KEY`, `RUSTFS_SECRET_KEY`, `CLOUDFLARE_TUNNEL_TOKEN` (condicional), `CORS_ORIGINS`, `APP_PUBLIC_BASE_URL`, `JWT_ISSUER`, `VAPID_SUBJECT`, `QUARKUS_PROFILE`, `JWT_KEYS_DIR`, `VAPID_KEYS_DIR`, `CLOUDFLARE_ENABLED`, `WYRDLY_SEED_ENABLED`, `VAPID_GENERATE_IF_MISSING`.
  - ADVANCED a comentar (~39 prod / ~37 dev): `NEO4J_*` resto, `RUSTFS_*` resto, `REDIS_*`, `BACKEND_*`, `AUTH_COOKIE_*`, `JWT_*_LOCATION`, `PUSH_*`, `QUARKUS_LOG_LEVEL`, `FRONTEND_*`, `VITE_*` (en prod `VITE_BACKEND_URL/WS_URL` son peso muerto: solo las usa el proxy de dev).
  - Regla del script: comentar SOLO si existe default (`:-` en compose o default en properties/código); si no, queda activa.
  - ADVERTENCIA para el revisor del PR: si algún tuning tiene valor custom distinto del default en `/etc/wyrdly/.env`, comentarlo lo devuelve al default — revisar el diff con ese ojo.
- [x] T2 Reescribir `.env.prod` (HECHO): 53 → 21 activas + 32 en bloque ADVANCED comentado. Restauradas 7 con tuning custom (HEAP×2, PUSH×2, VITE_BACKEND/WS, REDIS_MAXMEMORY). Valores intactos.
- [x] T3 Reescribir `.env.dev` (HECHO): 51 → 24 activas + 27 en ADVANCED. Restauradas 8 custom (AUTH_COOKIE_SECURE, POOL, HEAP_MAX, LOG_LEVEL, REDIS_BIND, REDIS_LOGLEVEL, BACKEND_PORT, FRONTEND_PORT).
- [x] T4 Validar (HECHO): set de nombres idéntico, sintaxis OK, `compose config` resuelto con 0/130 hojas distintas en ambos archivos.
- [x] T5 PR a develop con issue linkeada (HECHO): PR #172, Closes #171.
- LECCIÓN: comparar hashes entre llamadas es inválido (env del shell difiere); toda comparación antes/después va en la MISMA llamada. Así se evitó borrar tuning custom real.

## Criterios de aceptación
- `git status` limpio salvo los dos `.env` + tracker.
- Ningún valor modificado (verificable: mismo set de nombres, diff solo mueve/comenta).
- Ninguna var activa eliminada; ninguna comentada sin default.

## Progreso
- Rama creada desde develop. Pendiente: T1.
- Ruta: inline por indisponibilidad de delegación (Task free-tier falla); batches acotados. Sin writers paralelos.

## Next step
- T1: clasificación value-blind.
