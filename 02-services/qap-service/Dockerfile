# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

# Build without tests (tests run in CI separately)
RUN apk add --no-cache maven && \
    mvn -B package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user — security requirement (Shared Standards §D.3)
RUN addgroup -S qap && adduser -S qap -G qap
USER qap

WORKDIR /app

COPY --from=build /workspace/target/qap-service-*.jar app.jar

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
