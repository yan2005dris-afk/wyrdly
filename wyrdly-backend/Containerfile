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

# Create unprivileged system group and user
RUN addgroup -g 10001 -S quarkus && \
    adduser -u 10001 -S quarkus -G quarkus && \
    chown -R quarkus:quarkus /deployments

# Copy fast-jar artifacts from build stage with non-root ownership
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/lib/ /deployments/lib/
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/*.jar /deployments/
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/app/ /deployments/app/
COPY --chown=quarkus:quarkus --from=build /app/target/quarkus-app/quarkus/ /deployments/quarkus/

USER 10001

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
