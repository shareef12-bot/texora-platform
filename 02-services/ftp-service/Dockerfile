# Build the jar first:  mvn -pl 02-services/ftp-service -am package   (from the platform root)
FROM eclipse-temurin:21-jre-jammy

# Non-root (B.1). Staging directory is owner-only.
RUN groupadd --system --gid 10001 ftp \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin ftp \
 && mkdir -p /var/lib/ftp-staging /app \
 && chown 10001:10001 /var/lib/ftp-staging \
 && chmod 700 /var/lib/ftp-staging

COPY target/ftp-service-*-SNAPSHOT.jar /app/app.jar

USER 10001:10001
EXPOSE 8084
ENV FTP_SERVER_PORT=8084 \
    FTP_STAGING_DIR=/var/lib/ftp-staging
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
