# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy only pom files first (better layer caching)
COPY pom.xml .

# Copy the pre-built fat JAR produced by 'mvn package' in CI
COPY target/texora-sec-stub-*.jar app.jar

# Explode the JAR for layered image (smaller layers, faster re-deploys)
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Patch OS packages to pick up latest security fixes (must run as root, before USER switch)
RUN apk update && apk upgrade --no-cache

# Security: non-root user (standard B.9)
RUN addgroup -S secops && adduser -S secops -G secops
USER secops

WORKDIR /app

# Copy exploded layers in dependency-update order
COPY --from=builder /build/dependencies/          ./
COPY --from=builder /build/spring-boot-loader/    ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/           ./

# Health-check via Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

EXPOSE 8080

# JVM tuning for containers:
# -XX:+UseContainerSupport  — respect cgroup memory/CPU limits
# -XX:MaxRAMPercentage=75   — leave 25% for OS/off-heap
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]