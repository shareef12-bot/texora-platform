# Build the jar first from the repo root:  mvn -pl 02-services/cgi-service -am package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 cgi && adduser -S -u 10001 -G cgi -s /sbin/nologin cgi
WORKDIR /app
COPY --chown=10001:10001 target/cgi-service.jar /app/cgi-service.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-XX:+ExitOnOutOfMemoryError","-jar","/app/cgi-service.jar"]
