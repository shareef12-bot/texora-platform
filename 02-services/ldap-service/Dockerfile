# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper and root pom first (cache layer)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Copy parent + platform pom hierarchy so the multi-module build resolves
COPY ../../pom.xml ../../pom.xml 2>/dev/null || true

# Copy service source
COPY src ./src

# Build — skip tests here; tests run in CI before the image is built
RUN ./mvnw -B -q package -DskipTests --no-transfer-progress

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — non-root, minimal image
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Security: run as non-root user
RUN addgroup -S ldapsvc && adduser -S ldapsvc -G ldapsvc

WORKDIR /app

COPY --from=build /workspace/target/ldap-service-*.jar app.jar

# Health check via actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:${LDAP_SERVER_PORT:-8087}/actuator/health | grep -q '"status":"UP"' || exit 1

USER ldapsvc

EXPOSE 8087

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
