# Build the jar first from the repo root:  mvn -pl 02-services/dc-service -am package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S dc && adduser -S dc -G dc
WORKDIR /app
COPY target/dc-service.jar /app/dc-service.jar
USER dc
EXPOSE 8085
ENV DC_SERVER_PORT=8085 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/dc-service.jar"]
