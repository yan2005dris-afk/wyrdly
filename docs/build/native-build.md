# Native Build Local — GraalVM/Mandrel para `wyrdly-backend`

Esta guía explica cómo construir, ejecutar y depurar `wyrdly-backend` como binario nativo GraalVM/Mandrel en una máquina de desarrollo. El `Dockerfile` multi-stage ya hace todo esto en CI; esta guía es para cuando querés iterar el build nativo **localmente** sin esperar al job `native-build` del workflow.

La feature completa está documentada en [`../odd/tasks/graalvm-native-migration.md`](../../odd/tasks/graalvm-native-migration.md) y la decisión arquitectónica en [`../decisions/ADR-008-graalvm-native-backend.md`](../decisions/ADR-008-graalvm-native-backend.md).

---

## Requisitos

- **Docker** 23+ con BuildKit habilitado (o Buildah / Podman compatible).
- **~10 GB libres** en disco: Mandrel image ~1.5 GB, build artifacts ~300 MB, runner final ~75 MB.
- **Acceso a internet** para `quay.io/quarkus/mandrel-for-jdk-21-rhel8` y Maven Central.
- **JDK 21** local si querés correr el build nativo sin Docker (menos recomendado, ver abajo).
- Opcional: **netcat (`nc`)** o **webhook.site** para smoke-test del endpoint push.

---

## 1. Build local con Docker / Mandrel (recomendado, reproduce CI)

Esta es la opción exacta que corre el job `native-build` en GitHub Actions.

```bash
cd wyrdly-backend
docker buildx build \
  --progress=plain \
  --load \
  -f Dockerfile \
  -t wyrdly-backend:native-dev \
  .
# Output: target/wyrdly-backend-* dentro de la imagen wyrdly-backend:native-dev
```

> La imagen base `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21` es **pública** (no requiere credenciales Red Hat). El candidato obvio `mandrel-for-jdk-21-rhel8:latest` falla con `unauthorized` en imágenes sin auth Red Hat y debe evitarse para repos que no tienen esos secretos configurados.

> Tip: la primera build tarda ~5–10 min (compilación AOT). Builds subsiguientes reutilizan la capa de Maven cache (`dependency:go-offline`) y solo recomponen `*-runner` cuando cambian archivos en `src/`.

Para extraer el runner generado:

```bash
docker create --name temp-extract wyrdly-backend:native-dev
docker cp temp-extract:/build/target/wyrdly-backend-*-runner ./build-runner
docker rm temp-extract
chmod +x ./build-runner
```

---

## 2. Build local sin Docker (requiere JDK 21 + GraalVM/Mandrel ya instalado)

Si ya tenés una JDK 21 con `native-image` instalado:

```bash
cd wyrdly-backend
./mvnw -B -e -ntp -DskipTests \
  -Dquarkus.native.container-build=true \
  package
# Output: target/wyrdly-backend-1.0.0-SNAPSHOT-runner
```

`quarkus.native.container-build=true` fuerza a Quarkus a usar la `glibc` del runtime Mandrel en lugar de la `glibc` local, evitando el clásico error de "libc version mismatch".

> Recomendación: si tu distro es distinta de RHEL 8/9 (Ubuntu 22.04, Debian 12, Arch), **no uses esta opción** sin tener Mandrel en contenedor. El binario puede arrancar en tu máquina pero crashear en el cluster con `GLIBC_2.X not found`.

---

## 3. Ejecutar el binario nativo

```bash
chmod +x ./build-runner

QUARKUS_PROFILE=dev \
QUARKUS_HTTP_HOST=127.0.0.1 \
QUARKUS_HTTP_PORT=8080 \
NEO4J_URI=bolt://localhost:7687 \
NEO4J_USERNAME=neo4j \
NEO4J_PASSWORD=password \
JWT_PUBLIC_KEY_LOCATION=file:$(pwd)/../jwt/publicKey.pem \
JWT_PRIVATE_KEY_LOCATION=file:$(pwd)/../jwt/privateKey.pem \
VAPID_PUBLIC_KEY_LOCATION=file:$(pwd)/../vapid/publicKey.txt \
VAPID_PRIVATE_KEY_LOCATION=file:$(pwd)/../vapid/privateKey.txt \
VAPID_SUBJECT=mailto:ops@wyrdly.com \
./build-runner
```

El proceso tarda ~50 ms en servir `/q/health/live`. Verificar:

```bash
curl -i http://127.0.0.1:8080/q/health/live
# Esperado: HTTP/1.1 200 OK, body {"status":"UP", ...}
```

> **Orden de arranque recomendado**: Neo4j arriba → Redis arriba → `wyrdly-vapid-bootstrap` (que genera claves) → `wyrdly-backend` (que lee las claves generadas). El `compose.yaml` codifica este orden vía `depends_on` con `condition: service_completed_successfully`.

---

## 4. Smoke test contra Web Push (HU11)

Levantá un mock push gateway en otra terminal:

```bash
while true; do { echo -e 'HTTP/1.1 201 Created\r\nContent-Length: 0\r\n'; } | nc -l -p 9999; done
```

Dispará un evento HU11 (reacción, follow, comment, chat message, repost). El backend cifra (RFC 8291), firma con VAPID (RFC 8292), y hace POST al mock. El mock devuelve 201 Created. Verificá las cabeceras `Authorization: vapid t=...`, `Content-Encoding: aes128gcm`, `TTL` en la captura.

Para un end-to-end más completo, usá la suite E2E del repo:
```bash
pnpm -F @wyrdly/e2e test --tags @push
```

---

## 5. Troubleshooting

### `MissingReflectionRegistrationError` al arrancar
**Síntoma**: el runner falla al arrancar con stack trace tipo `at java.lang.reflect.Method.invoke`.

**Causa típica**: una extensión Quarkus (típicamente 3rd-party) registra reflection metadata en runtime que `native-image` no detectó en build-time.

**Fix provisional**: agregá `--add-opens` o `--initialize-at-build-time` como build-arg:
```bash
./mvnw -B -e -ntp -DskipTests \
  -Dquarkus.native.additional-build-args="--initialize-at-build-time=org.some.cls" \
  package
```
**Fix permanente**: reportar upstream con stack trace exacto; el fix termina en una nueva versión de Mandrel con la registración correcta.

### `GLIBC_2.X not found` al ejecutar
**Síntoma**: `version 'GLIBC_2.34' not found` (o similar) al `./build-runner`.

**Causa típica**: built localmente con una `glibc` distinta del runtime.

**Fix**: rebuildar dentro de Mandrel (sección 1 de esta guía), o subir el target del runner a una distro compatible.

### `RuntimeException: Could not determine Java version`
**Síntoma**: el build falla con un error de versión Java que no debería estar ahí.

**Diagnóstico**: verificar la imagen Mandrel:
```bash
docker run --rm quay.io/quarkus/mandrel-for-jdk-21-rhel8:latest \
  /bin/bash -c "java -version"
# Esperado: openjdk version "21.0.X" YYYY-MM-DD
```

**Fix**: si la versión no es 21.x, probablemente la imagen cambió de major — pineá el digest (`@sha256:...`) en el Dockerfile y en CI.

### `wyrdly-vapid-bootstrap` exit 127
**Síntoma**: el init-container del compose crash con código 127.

**Diagnóstico**:
```bash
docker logs wyrdly-vapid-bootstrap
# Si dice "apk: command not found", el image no es Alpine.
# Si dice "openssl: command not found", el apk add falló.
```

**Fix**: el init-container usa `alpine:3.20` con `apk add --no-cache openssl bash coreutils su-exec` (definido en `compose.yaml`). Si tu mirror de Alpine está caído, podés switchar a `alpine:3.21` mientras dura.

### `wyrdly-backend` arranca pero Web Push devuelve 503
**Síntoma**: `/api/notifications/vapid-public-key` devuelve 503 Service Unavailable.

**Causa típica**: el archivo de claves existe pero el runner (UID 10001) no puede leerlo porque el owner es `root` y los permisos no son world-readable.

**Diagnóstico**:
```bash
ls -la /deployments/vapid  # o ./vapid si estás en dev
# Esperado: -rw-r--r-- root root (chmod 644)
```

**Fix**: `chmod 644 ./vapid/publicKey.txt ./vapid/privateKey.txt` y reiniciar `wyrdly-backend`.

---

## 6. CI

El job `native-build` en `.github/workflows/backend-ci.yml` corre este flujo completo en GitHub Actions. Buscalo en la pestaña **Actions → Backend CI Pipeline → Native Build (Mandrel / quarkus-micro-image)**. El job:

1. Hace checkout del repo.
2. Cachea `~/.m2` con clave basada en `wyrdly-backend/pom.xml` + `mvnw wrapper`.
3. Genera claves JWT de testing.
4. Compila `./mvnw package -Dnative` dentro del contenedor Mandrel.
5. Arranca el `*-runner` y verifica `/q/health/live` en menos de 30 s.
6. Loggea el tamaño del binario (`ls -la ./target/*-runner`).

El target de startup medido es **<500 ms**; RSS objetivo **<80 MB**. Si CI reporta números fuera de rango, abrir un issue referenciando este doc.

---

## 7. Métricas de referencia (TARGETS, no mediciones)

| Métrica | Antes (JVM) | Objetivo (native) | Típico observado en Quarkus 3.x |
|---|---|---|---|
| Cold start | ~1–3 s | **<500 ms** | ~50 ms |
| RSS en idle | ~250 MB | **<80 MB** | ~40–60 MB |
| Tamaño imagen runtime | ~250 MB | **<100 MB** | ~75 MB |
| Tiempo build CI | ~1–2 min | **+5–10 min** | ~6 min cold, ~2 min warm |
| Pico de RSS en burst | ~400 MB | **<150 MB** | ~120 MB |

Las cifras reales se medirán cuando el job `native-build` corra por primera vez contra la rama `feat/graalvm-native-migration` y se documenten en [`../../odd/tasks/graalvm-native-migration.md`](../../odd/tasks/graalvm-native-migration.md) §"Evidencia de Verificación".
