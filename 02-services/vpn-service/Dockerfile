# Runtime image only: build with `mvn -pl 02-services/vpn-service -am package` first.
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system --gid 10001 vpn && useradd --system --uid 10001 --gid vpn --no-create-home --shell /usr/sbin/nologin vpn
WORKDIR /app
COPY target/vpn-service.jar /app/vpn-service.jar
USER 10001:10001
EXPOSE 8088
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","/app/vpn-service.jar"]
