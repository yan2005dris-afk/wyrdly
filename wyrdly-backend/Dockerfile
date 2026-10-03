# Stage 1: Build Quarkus Application
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy POM and download dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code and build package
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Minimal Java Runtime (Rootless / Unprivileged)
FROM docker.io/library/eclipse-temurin:21-jre-alpine

WORKDIR /deployments

ENV LANGUAGE='en_US:en'
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# openssl + bash + coreutils + su-exec are required by the VAPID keypair
# bootstrap script (scripts/init-vapid-keys.sh). The script must run as root
# so it can write to the mounted volume; su-exec then drops privileges to
# the unprivileged `quarkus` user for the JVM.
RUN apk add --no-cache openssl bash coreutils su-exec

# Create unprivileged system group and user
RUN addgroup -g 10001 -S quarkus && \
    adduser -u 10001 -S quarkus -G quarkus && \
    chown -R quarkus:quarkus /deployments

# Copy fast-jar artifacts from build stage with non-root ownership
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/lib/ /deployments/lib/
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/*.jar /deployments/
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/app/ /deployments/app/
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/quarkus/ /deployments/quarkus/

# VAPID keypair bootstrap script (HU11 / TASK-01 / #62). Idempotent: skips if
# keys already exist at /deployments/vapid (mounted as a volume in compose).
COPY --chown=quarkus:quarkus scripts/ /deployments/scripts/
RUN chmod +x /deployments/scripts/init-vapid-keys.sh

USER 10001

EXPOSE 8080

# Bootstrap VAPID keys before starting the JVM. The volume mount
# ./vapid:/deployments/vapid (declared in compose.yaml) persists them between
# restarts; the script regenerates if the volume is empty. We intentionally
# run the script as root (so it can write to the mount) and drop privileges
# via su-exec before launching the JVM.
ENTRYPOINT ["sh", "-c", "/deployments/scripts/init-vapid-keys.sh /deployments/vapid && exec su-exec quarkus java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
