# ADR-008: Migración del Backend a GraalVM/Mandrel Native Build

**Status:** Accepted (en progreso via `feat/graalvm-native-migration`)
**Date:** 2026-10-12
**Deciders:** Equipo Backend + Arquitectura
**Related:** [issue #180](https://github.com/yan2005dris-afk/wyrdly/issues/180), [feature doc](../../odd/tasks/graalvm-native-migration.md), [ADR-006](ADR-006-prod-configuration-checklist.md), [WEB_PUSH.md §6](../operations/WEB_PUSH.md)

---

## Context

El `wyrdly-backend` se despliega hoy como un JAR ejecutable por la JVM (`quarkus-run.jar`) sobre `eclipse-temurin:21-jre-alpine`. El `pom.xml` ya declara `<packaging>quarkus</packaging>` y el profile `<id>native</id>` con `<quarkus.native.enabled>true</quarkus.native.enabled>`, pero el `Dockerfile` y el `compose.yaml` nunca lo activaron.

Consecuencias prácticas del estado actual:

- **Cold start ~1–3 s** medidos en contenedores (`docker compose up wyrdly-backend` → healthy ≈ 2.4 s).
- **~250–400 MB RSS** en operación estable.
- **GC pauses** observables en reacciones masivas (HU09) y chat (HU10), que ya quedan en `p95 < 80 ms` solo por el overhead del dispatch pool.
- **Imagen runtime ~250 MB** (`eclipse-temurin:21-jre-alpine` + JDK + `quarkus-app/lib`).
- **El bootstrap de las claves VAPID (HU11)** corre dentro del container del backend como root, baja privilegios con `su-exec`, y solo entonces arranca la JVM. Mezcla responsabilidades: un contenedor no debería correr scripts como root antes de bajar privilegios.

Los tres obstáculos concretos para activar native build son:

1. La imagen runtime nativa (`quarkus-micro-image:21.0`) **no tiene `apk`, ni `bash`, ni `su-exec`**; el script de bootstrap VAPID requiere los tres.
2. Ningún job de CI compila con Mandrel hoy; todos usan `temurin:21` con `mvn package -DskipTests` regular.
3. El script de bootstrap VAPID mezcla generación criptográfica con arranque de aplicación en el mismo proceso.

## Decision

**Activar GraalVM/Mandrel native build para el backend, manteniendo el bootstrap VAPID como un init-container separado.**

### Rationale

1. **Razón técnica principal**: Quarkus es GraalVM-native-ready por diseño. Activar native reduce ~5× el tiempo de arranque y ~5× la memoria RSS. En un backend distribuido en Cloudflare Tunnel eso significa más contenedores por nodo y menos escala horizontal. El framework ya estaba pensado para esto; el estado actual es un sub-uso del runtime.

2. **Imágenes oficiales**: `quay.io/quarkus/mandrel-for-jdk-21-rhel8` para build y `quay.io/quarkus/quarkus-micro-image:21.0` para runtime son imágenes mantenidas por el equipo de Quarkus; no introducen nuevas dependencias externas y evitan la fragilidad del upstream `graalvm-ce` (que tiene incompatibilidades conocidas con `musl/glibc`).

3. **Init-container, no Java bootstrap**: re-implementar `scripts/init-vapid-keys.sh` en Java dentro del backend sería ~80 líneas de crypto DER/PKCS#8/base64url que tienen que coincidir EXACTAMENTE con la salida del `openssl` actual. Cada bit fuera de lugar invalida las suscripciones Web Push silenciosamente (ver [issue #181](https://github.com/yan2005dris-afk/wyrdly/issues/181), §2 "Validación de la clave VAPID"). Mantener el script bash probado en HU11 (#62–#73) en un container propio es estrictamente menos riesgo.

4. **Costo operacional aceptable**: el init-container arranca una vez por cold start (cuando `./vapid` está vacío); el resto del tiempo el script bash detecta las claves existentes y termina en <100 ms con exit 0. En operación normal el costo es despreciable.

5. **Reversibilidad**: si el native build muestra incompatibilidad con alguna extensión Quarkus (smallrye-jwt, aws-sdk, neo4j-driver), el fallback es trivial — basta con revertir el ENTRYPOINT del Dockerfile (`./deployments/runner` → `java -jar quarkus-run.jar`) y la imagen runtime vuelve al estado anterior.

### Other Options Considered

**Opción A — Mantener JVM, optimizar arranque con CDS/AppCDS**: menor impacto, ~30 % mejora de arranque, no reduce RSS significativamente. Descartada porque la mejora absoluta (de ~2.4 s a ~1.6 s) no justifica el esfuerzo cuando native ofrece 50×.

**Opción B — Re-implementar bootstrap VAPID en Java (`@Observes StartupEvent`)**: menos containers en compose (~1 service), pero ~80 líneas de crypto que pueden divergir del openssl probado. Descartada por la dificultad de mantener compatibilidad con la implementación de referencia validada en HU11.

**Opción C — Compilar native con `native-svm` o GraalVM CE upstream**: menor costo de imagen, pero menos compatible con el resto del stack Quarkus (smallrye-jwt, Neo4j driver, AWS SDK); Mandrel es la distribución recomendada upstream.

## Consequences

### Positive

- **Cold start objetivo <500 ms** (target de T05 en el feature doc); RSS objetivo <80 MB.
- **Imagen runtime ~75 MB** (`quarkus-micro-image:21.0` + runner ELF stripped).
- **Aislamiento del bootstrap VAPID**: el script bash queda en un container propio, fácil de auditar, fácil de rotar claves sin tocar el backend (ver `WEB_PUSH.md` §2).
- **Reducción de superficie de ataque**: el runtime no tiene shell, no tiene openssl, no tiene apk. Solo expone el puerto HTTP.
- **Decisión documentada** para que la siguiente persona que mire el Dockerfile entienda por qué el bootstrap ya no vive dentro del backend.

### Negative

- **Tiempo de build CI +5–10 min** (compilación AOT). Mitigado por caching de la etapa `dependency:go-offline` y por la capa de `quarkus-maven-plugin` cache en `~/.m2/repository`.
- **Cold start del cluster +5 s** la primera vez (init-container hace `apk add`). A partir del segundo arranque el script es idempotente y termina en <100 ms.
- **Mandrel `@latest` no pineado** aún; pendiente para un PR de seguimiento (T05 listó esta concern).
- **`/etc/passwd` lookup** dentro del micro-image: el `quarkus` user (UID 10001) se referencia numéricamente en el Dockerfile (`USER 10001`) porque la imagen distroless no tiene `getent`.

### Neutral

- **HU11 sigue funcionando**: las claves VAPID siguen generándose por el mismo script bash; el dispatcher y los 6 listeners (`application/listener/`) no cambian. Esta ADR no toca HU11 funcionalmente, solo cambia DÓNDE vive el bootstrap.

## Implementation

Feature document: [`odd/tasks/graalvm-native-migration.md`](../../odd/tasks/graalvm-native-migration.md). El cambio se entrega en `feat/graalvm-native-migration` (#180) y se cierra con un PR contra `develop` cuando CI pase todos los jobs (incluido el nuevo `native-build`).

Para instrucciones paso-a-paso en desarrollo local, ver [`docs/build/native-build.md`](../build/native-build.md).
