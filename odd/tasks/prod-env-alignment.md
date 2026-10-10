# Feature: Production Env Alignment (CI · compose · .env · application.properties)

## Objective
Alinear todo el sistema de configuración asumiendo que la salida es producción:

- `compose.yaml`: defaults seguros para prod (sin abrir datos seed, sin CORS permisivo por default, cookie secure activo).
- `.env.prod` (NUEVO): plantilla con cada variable y el **valor esperado en producción** comentado línea por línea.
- `.env.dev`: quick-start local con secciones claras y referencia cruzada al template prod.
- `application.properties`: bloque `%prod.` explícito que falla cerrado (CORS, AF cookie, JWT issuer, public base URL, seed disabled).
- `vite.config.ts` + `Dockerfile` del frontend: alineados con los build args prod (`VITE_BACKEND_URL`, `VITE_WS_URL`).
- `.github/workflows/deploy.yml`: validar contrato de vars requeridas antes de `docker compose up`.
- `docs/deployment/PRODUCTION_DEPLOYMENT.md` (NUEVO): guía paso a paso.
- `docs/deployment/LOCAL_DEPLOYMENT.md`: reflejar que el seed hay que activarlo explícito.

## Scope & Constraints
- Cero secretos en el repo (`.env` real sigue en `.gitignore`).
- Defaults safe-by-default; el local dev debe setear sus overrides en `.env`.
- Conventional commits (`chore(env): ...`, `docs(deploy): ...`, `feat(backend): ...`).
- No romper `docker compose up` local sin `.env`: la app debe arrancar con un `.env` mínimo documentado.
- Mantener compatibilidad con `WYRDLY_SEED_ENABLED=true` para dev cuando se setee explícito.

## Checklist & Tasks

- [ ] Task 1: Endurecer `compose.yaml` con defaults prod-safe y comentarios esperados por variable.
- [ ] Task 2: Crear `.env.prod` (NUEVO) con todas las vars y valor prod esperado como comentario.
- [ ] Task 3: Crear `.env.dev` (era `.env.example`) con secciones claras (Neo4j, RustFS, Redis, Backend, Frontend, Cloudflare) y referencias al template prod.
- [ ] Task 4: Refactorizar `application.properties` con bloque `%prod.` explícito que falla cerrado y comentarios por sección.
- [ ] Task 5: Alinear `vite.config.ts` y `wyrdly-frontend/Dockerfile` con los build args prod esperados.
- [ ] Task 6: Endurecer `.github/workflows/deploy.yml` con validación de vars requeridas antes del `docker compose up`.
- [ ] Task 7: Crear `docs/deployment/PRODUCTION_DEPLOYMENT.md` (NUEVO) con guía paso a paso.
- [ ] Task 8: Actualizar `docs/deployment/LOCAL_DEPLOYMENT.md` para reflejar que el seed hay que activarlo explícito.
- [ ] Task 9: Verificación: `docker compose config` con `.env.prod`, build local backend + frontend, lint.