# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
# Built as part of the full reactor (texora-secops-platform); this Dockerfile
# expects a pre-built jar when run standalone, or is invoked via the root
# multi-module build with `mvn -pl 02-services/sso-service -am package`.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl . -am package -DskipTests || true

# Runtime stage — non-root, minimal
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S texora && adduser -S sso -G texora
WORKDIR /app
COPY --from=build /workspace/target/sso-service.jar /app/sso-service.jar
USER sso
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sso-service.jar"]
