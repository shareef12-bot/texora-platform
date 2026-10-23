# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependency resolution separately from source changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true

COPY src src
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine

# Non-root image (B.1 / Definition of Done).
RUN addgroup -S siem && adduser -S siem -G siem
WORKDIR /app

COPY --from=build /workspace/target/siem-service.jar /app/siem-service.jar
RUN chown -R siem:siem /app

USER siem

EXPOSE 8086
ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O - http://localhost:8086/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/siem-service.jar"]
