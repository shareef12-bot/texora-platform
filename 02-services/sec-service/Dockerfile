# ============================================================
# SEC Service — non-root production image (B.9)
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create non-root user (uid 1001) per B.9
RUN addgroup -S secops && adduser -S secservice -G secops -u 1001

WORKDIR /app

# Copy the fat JAR built by Spring Boot Maven plugin
COPY target/sec-service-*.jar app.jar

# Set ownership before switching user
RUN chown -R secservice:secops /app

USER 1001

EXPOSE 8087

# Health-check using actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O- http://localhost:8087/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
