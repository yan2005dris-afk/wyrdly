# Dev/prod up wrappers — rama chore/dev-prod-up-scripts

Base: develop @ 591634b. Objetivo: un comando por perfil, layouts explícitos, cero auto-magia.
- `scripts/dev-up.sh`: dev en raíz (`.env`, `./jwt`, `./vapid`) → bootstrap-local + up + wait healthy + URLs.
- `scripts/prod-up.sh`: prod en `/etc/wyrdly` → preflight (env+keys existen, vars requeridas) + up + smoke.
- Wrappers DELGADOS: reutilizan `bootstrap-local-env.sh` / `bootstrap-prod-env.sh`, no duplican lógica cripto.

## Alcance autorizado
- `scripts/dev-up.sh` (nuevo), `scripts/prod-up.sh` (nuevo), este tracker.
- Docs de 1 línea en LOCAL/PRODUCTION_DEPLOYMENT si hace falta. Nada más.

## Checklist
- [x] T1 Lecturas de reuso (HECHO): bootstrap-prod (provisiona, nunca adivina), required-list de deploy.yml (7+1), patrón detect-CLI de bootstrap-local.
- [x] T2 `scripts/dev-up.sh` (HECHO): bootstrap-local + stale-volume warn (mtime, value-blind) + up + wait + URLs. Executable, `bash -n` OK.
- [x] T3 `scripts/prod-up.sh` (HECHO): preflight loud de /etc/wyrdly (env+keys+7 required), up con ese env-file, wait, smoke /healthz. Nunca inventa secretos. Executable, `bash -n` OK.
- [x] T4 Parcial (HECHO lo no-disruptivo): sintaxis OK en ambos. shellcheck NO disponible en este host (se corre en CI del PR). Test real del path dev DIFERIDO a propósito: implica rebuild+restart del stack que está en uso.
- [x] T5 Docs 1-liner + PR con issue (HECHO): PR #183, Closes #182. (Docs 1-liner innecesario: los headers de los scripts ya documentan uso y layouts.)
- [x] T6 Wave 2 en esta misma rama (HECHO): straddle muerto (APP_PUBLIC_BASE_URL→origen frontend, VITE_BACKEND/WS comentados; chat WS ya era same-origin) + `scripts/lib/check-prod-env.sh` único usado por deploy.yml y prod-up.sh. Verificado: diff resuelto con EXACTO los 3 paths previstos, lib OK en positivo y negativo.
- NOTA-OPS: `/etc/wyrdly/.env` del servidor aún tiene el valor api.* → actualizarlo a mano en el próximo deploy (media rota hasta entonces).

## Criterios de aceptación
- Dev desde cero: `rm .env` + `./scripts/dev-up.sh` levanta todo sin intervención.
- Prod sin `/etc/wyrdly/.env` falla LOUD antes de tocar nada (nunca inventa secretos prod).
- shellcheck limpio en ambos scripts.
- Sin `Co-Authored-By`, commits convencionales.

## Progreso
- Rama creada desde develop. Pendiente: T1.
- Ruta: inline por indisponibilidad de delegación (Task free-tier falla). Sin writers paralelos.

## Next step
- T1 lecturas de reuso.
