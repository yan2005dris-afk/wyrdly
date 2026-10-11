# Feature: Migración a GraalVM Native Build del Backend de Wyrdly

> Issue origen: #180 `[infra] Activar build nativa GraalVM/Mandrel en wyrdly-backend`.
> Branch: `feat/graalvm-native-migration` (basada en `develop` @ `f349466`).
> Responsable: orchestrator (el-Gentleman) + subagents de bounded implementation.

## Objetivo

Migrar el backend `wyrdly-backend` de una build JVM estándar (Eclipse Temurin 21 + `quarkus-run.jar`) a una build nativa generada por GraalVM/Mandrel, embebida en la imagen mínima `quarkus-micro-image:21.0`, sin perder ninguna de las features existentes (HU11 inclusive).

El backend ya tiene el profile `<id>native</id>` activo en `pom.xml` (`<quarkus.native.enabled>true</quarkus.native.enabled>`, `<packaging>quarkus</packaging>`). La diferencia entre **"configurado"** y **"activado"** es lo que esta feature cierra.

## Problema

- El `wyrdly-backend/Dockerfile` actual produce un `quarkus-run.jar` y corre bajo JRE (`eclipse-temurin:21-jre-alpine` + `java -jar`). Pago: arranque ~1-3 s, ~250-400 MB RSS, GC pauses en producción.
- El script `scripts/init-vapid-keys.sh` (HU11 bootstrap de claves VAPID) corre **dentro del container del backend como root**, baja privilegios con `su-exec`, y luego levanta la JVM. En una imagen **native distroless** (`quarkus-micro-image:21.0`) no hay `apk`, no hay `su-exec`, no hay `bash`, no hay `openssl` por defecto.
- Ningún job de CI compila en modo nativo hoy (todos usan `temurin:21` con `mvn package` regular). El deploy llega al runner self-hosted pero **no valida** que el binario nativo arranque antes de promote.

## Por qué

Wyrdly ya está desplegado en producción con su `compose.yaml` y su `deploy.yml`. La activación nativa es la mejora de infraestructura pendiente con mayor ROI (memoria, latencia de arranque, superficie de ataque reducida). Es además **una condición habilitante** para escalar horizontalmente barato y para reducir el cold start de Cloudflare Tunnel cuando el contenedor se reinicia.

Resolver esta feature **antes** de empezar #181 (VAPID garantías) tiene sentido porque #181 va a tocar el ciclo de subscripción, y queremos confirmar que el ciclo de subscripción que YA existe no se rompió con el cambio de runtime.

## Alcance Autorizado

1. **Dockerfile del backend multi-stage con Mandrel + `quarkus-micro-image`**.
   - Stage build: `quay.io/quarkus/mandrel-for-jdk-21-rhel8` (o equivalente mantenido, validación previa en T2).
   - Build command: `mvn package -Dnative -DskipTests`.
   - Stage runtime: `quay.io/quarkus/quarkus-micro-image:21.0` (UID 10001 `quarkus` ya viene listo).
   - EntryPoint: `["./deployments/runner", "-Dquarkus.http.host=0.0.0.0"]` (sin `su-exec`, sin `java -jar`).
2. **Separar el bootstrap VAPID a un init-container** (`wyrdly-vapid-bootstrap`).
   - Service nuevo en `compose.yaml` con `eclipse-temurin:21-jre-alpine` (mantiene `openssl`/`bash`/`su-exec`).
   - Reusa el script `scripts/init-vapid-keys.sh` existente sin cambios.
   - Volume compartido `./vapid:/deployments/vapid:rw`.
   - El backend `depends_on: wyrdly-vapid-bootstrap { condition: service_completed_successfully }`.
3. **`pom.xml`**: verificar y, si hace falta, completar el profile `native` con `quarkus.native.resources.includes` y `quarkus.native.args` para que las extensiones problemáticas (`smallrye-jwt`, `quarkus-neo4j`, `bucket4j-redis`, `software.amazon.awssdk:s3`) cierren la compilación AOT sin warnings de clases no reflectables.
4. **`compose.yaml`**: actualizar service `wyrdly-backend` para usar el Dockerfile nuevo (ya está) y añadir el service `wyrdly-vapid-bootstrap`. Healthcheck del backend sigue siendo `GET /q/health/live`.
5. **`backend-ci.yml`**: añadir job `native-build` que use `container: image: quay.io/quarkus/mandrel-for-jdk-21-rhel8` y verifique que `target/wyrdly-backend-*-runner` existe, es ejecutable, y arranca `/q/health/live` en <500 ms. **No rompe** los jobs existentes (`format`, `unit-test`, `coverage`, `integration-test`, `build` JVM).
6. **`deploy.yml`**: queda igual (ya hace `docker compose up -d --build --remove-orphans`). Solo se beneficia del nuevo Dockerfile.
7. **Documentación**: agregar bloque "Native build local" al `WEB_PUSH.md` o un `docs/build/native-build.md` explicando cómo correr `mvn package -Dnative` con Mandrel (vía Docker) y qué esperar del runtime.

## Restricciones

- **No tocar HU11** ni el módulo de notificaciones. El binario nativo debe bootear igual que el JVM con todos los endpoints de VAPID y los 6 listeners (`application/listener/`).
- **Mantener** el script `scripts/init-vapid-keys.sh` intacto (es init-container, sigue usando openssl).
- **Mantener** el usuario unprivileged `quarkus` (UID 10001) en runtime — la native image ya lo trae por convención de Quarkus.
- **Conventional Commits** sin co-autoría IA.
- Cada task cierra con un work-unit commit en la branch; pushes y PR se gestionan después por el usuario.
- **Tiempo de CI**: el job `native-build` puede sumar 5-10 min al `backend-ci.yml`; documentado en T5.

## Tareas

- [ ] **TASK-01**: Crear branch `feat/graalvm-native-migration` desde `develop` y este feature doc. (Commit: inicial)
- [ ] **TASK-02**: Reescribir `wyrdly-backend/Dockerfile` a multi-stage Mandrel + `quarkus-micro-image` con entrypoint nativo. Quitar el bootstrap VAPID y todo el setup `su-exec`/Alpine del runtime. (Commit: TBD)
- [ ] **TASK-03**: Adaptar `compose.yaml`: añadir service `wyrdly-vapid-bootstrap` (Alpine + `init-vapid-keys.sh`), volume `./vapid` compartido, backend `depends_on` con `condition: service_completed_successfully`. (Commit: TBD)
- [ ] **TASK-04**: Validar y completar `wyrdly-backend/pom.xml` profile `native`: agregar/quitar properties de reflexión según haga falta. Confirmar `quarkus.package.type=fast-jar` cuando NO se esté buildeando nativo. (Commit: TBD)
- [ ] **TASK-05**: Agregar job `native-build` al `.github/workflows/backend-ci.yml` con `container: quay.io/quarkus/mandrel-for-jdk-21-rhel8`, comando `mvn package -Dnative -DskipTests`, y un smoke-test contra el runner binario (`./target/*-runner &` + `wget http://127.0.0.1:8080/q/health/live`). **No modificar** los otros jobs. (Commit: TBD)
- [ ] **TASK-06**: Docs: agregar `docs/build/native-build.md` con instrucciones locales (Docker Mandrel, smoke test, troubleshooting `reflect-config`). (Commit: TBD)
- [ ] **TASK-07**: Verificación end-to-end de la branch:
  - `mvn spotless:check` (formato).
  - `mvn package -DskipTests` (JVM regular; confirma que no se rompió el camino viejo).
  - CI pasa todos los jobs nuevos incluyendo `native-build`.
  - Documentar startup time + RSS mem antes/después.
- [ ] **TASK-08**: Cerrar feature con el commit hash final enlazado en este doc y el resumen en `mem_session_summary`.

## Evidencia de Verificación

Pendiente de T7. Se completará con:

- Hash del commit final de la rama.
- Logs del job `native-build` verde en CI.
- Comparación `startup time` y `RSS mem` antes/después del switch.
- Confirmación de `/q/health/live` <500 ms en la imagen nativa local.

## Apéndice: Decisiones arquitectónicas

### Decisión D1 — Init-container vs bootstrap en Java dentro del backend

**Opción elegida: init-container `wyrdly-vapid-bootstrap`.**

| Aspecto | Init-container (Alpine) | Bootstrap Java en backend |
|---|---|---|
| Script existente `init-vapid-keys.sh` | Se reusa **sin tocar** | Hay que reimplementar en Java |
| Separation of concerns | Clear (bootstrap ≠ app) | Bootstrap acoplado al startup del backend |
| Reversibilidad | Cambiar el script no toca el backend | Cambiar el bootstrap toca el ciclo de vida del servidor |
| Verificabilidad | `service_completed_successfully` da éxito binario | Falla de bootstrap enmascarable con logs |
| Tamaño imagen runtime | `quarkus-micro-image:21.0` limpio | Podría mantenerse Alpine con tamaño mayor |
| Costo | +1 service en compose, +5 s de bootstrap en cold start | Más simple operacionalmente |

La complejidad de reimplementar el bootstrap en Java (parseo DER PKCS#8, manipuleo de puntos EC, encoding base64url con padding, verificación de longitudes) **no se justifica** frente a reusar el script openssl ya probado en HU11. Init-container es la respuesta correcta.
