# ─────────────────────────────────────────────────────────────────────────
# Stage 1 — Native build with Mandrel (GraalVM distribution optimised for
# Quarkus).
#
# The wyrdly-backend Quarkus application is compiled into a self-contained
# native executable (`-runner`) using Mandrel's `native-image` tool. Mandrel
# is the upstream-recommended distribution for Quarkus builds and avoids the
# glibc/musl surprises that plain upstream GraalVM can introduce on RHEL
# (Mandrel) versus Ubuntu (musl) hosts.
#
# Pre-requisites assumed by this Dockerfile:
#   * `pom.xml` declares `<packaging>quarkus</packaging>` and activates the
#     `<id>native</id>` profile (TASK-04 verified this; do not change).
#   * `mvnw` is executable; the `quarkus` user owns the workspace (see below).
# ─────────────────────────────────────────────────────────────────────────
FROM quay.io/quarkus/mandrel-for-jdk-21-rhel8:latest AS build

# Honour outbound HTTP/HTTPS proxies only when the caller actually passes
# them via `--build-arg`. Both UPPER and lower case forms are honoured
# because Maven / Gradle / curl disagree on which one they read. The ARG
# declarations are scoped to this stage, so the runtime stage below does
# NOT inherit them — runtime traffic goes through Cloudflare Tunnel, not
# any outbound proxy.
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG http_proxy
ARG https_proxy
ENV HTTP_PROXY=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    http_proxy=${http_proxy} \
    https_proxy=${https_proxy}

WORKDIR /build

# The `quarkus` user (UID 10001) ships with the Mandrel base image. All
# workspace files are owned by that user so the native build runs
# unprivileged; Mandrel does not require root, and a non-root build avoids
# leaving host-owned artefacts behind in the BuildKit cache.
COPY --chown=quarkus:quarkus pom.xml ./
COPY --chown=quarkus:quarkus .mvn .mvn
COPY --chown=quarkus:quarkus mvnw mvnw ./

# Warm the local Maven cache before the source tree is copied in. This keeps
# dependency resolution out of the (long) native-build step and maximises
# cache reuse across rebuilds that only touch `src/`.
RUN ./mvnw -B -e -ntp -DskipTests dependency:go-offline

COPY --chown=quarkus:quarkus src ./src

# Compile to a native executable.
#
# `-DskipTests` is intentional: the JVM CI lane already runs unit + IT
# suites, and re-running them here would double the wall-clock cost of the
# (already expensive) AOT step. The artefact produced is
# `target/wyrdly-backend-<version>-runner`, an ELF executable ready to
# `execve` without a JVM.
RUN ./mvnw -B -e -ntp -DskipTests package -Dnative

# ─────────────────────────────────────────────────────────────────────────
# Stage 2 — Minimal distroless runtime.
#
# `quarkus-micro-image:21.0` is the official Quarkus runtime base: RHEL-
# based, distroless, ships the `quarkus` user (UID 10001) already. The
# native runner is fully self-contained (no JVM, no shell, no `openssl`, no
# `su-exec`), so we do NOT — and must not — copy any of the JVM-era
# artefacts:
#
#   * `quarkus-app/`, `target/`, `lib/`, `*.jar`  — JVM fast-jar layout
#   * `scripts/`, `init-vapid-keys.sh`           — moved to the
#                                                  `wyrdly-vapid-bootstrap`
#                                                  init-container (TASK-03)
#   * `java -jar`, `su-exec`, `sh -c` wrappers    — no shell present
#
# VAPID keypair generation lives in the `wyrdly-vapid-bootstrap` init-
# container (see compose.yaml) which writes to the same `./vapid` volume
# the backend reads at startup.
# ─────────────────────────────────────────────────────────────────────────
FROM quay.io/quarkus/quarkus-micro-image:21.0

WORKDIR /deployments

# Copy only the native runner. The glob matches the Quarkus default
# artefact name `wyrdly-backend-<version>-runner`. Ownership is mapped to
# UID/GID 10001 (the `quarkus` user) so the runtime can read/exec the
# binary without root and without a `/etc/passwd` lookup.
COPY --from=build --chown=10001:10001 /build/target/wyrdly-backend-*-runner /deployments/runner

# Drop privileges. UID 10001 is the `quarkus` user shipped by the micro
# image. We address it numerically because the distroless image has no
# `getent` / no `/etc/passwd` tools to resolve the name.
USER 10001

EXPOSE 8080

# Configuration is injected via env vars by compose.yaml. The native
# runner reads them natively:
#
#   QUARKUS_HTTP_HOST  — bind address (typically 0.0.0.0)
#   QUARKUS_HTTP_PORT  — listen port (typically 8080)
#   QUARKUS_PROFILE    — Quarkus profile to activate (typically `prod`)
#   JAVA_OPTS_APPEND   — Quarkus passes this through the native runner too,
#                          preserving the JVM-style override path for the
#                          rare cases where we still need to influence
#                          behaviour (logging manager, etc.).
#
# `JAVA_OPTS` is intentionally ABSENT: the native image is a single static
# ELF executable — there is no JVM to fork and consume the variable. Use
# `JAVA_OPTS_APPEND` (or the explicit Quarkus env vars above) instead.
#
# `EXPOSE 8080` matches what the JVM-era Dockerfile declared and what
# `wyrdly-backend`'s healthcheck (`/q/health/live`) probes. The Cloudflare
# Tunnel / Nginx ingress does not change.
ENTRYPOINT ["./deployments/runner"]
