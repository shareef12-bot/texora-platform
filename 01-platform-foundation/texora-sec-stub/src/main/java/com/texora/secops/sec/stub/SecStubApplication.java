package com.texora.secops.sec.stub;

import com.texora.secops.sec.stub.config.StubProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Runnable SEC API stub.
 *
 * <p>Start with:
 * <pre>
 *   java -jar texora-sec-stub.jar
 *   # or:
 *   mvn spring-boot:run -pl texora-sec-stub
 * </pre>
 *
 * <p>Default port: 8080. Override with {@code --server.port=8090}.
 *
 * <p>Admin API (dev only) at {@code /api/v1/policies/stub/rules}.
 *
 * <p><strong>This stub is NOT for production.</strong> It has no authentication.
 * Deploy it only in dev/test environments. The real SEC service replaces it in staging/prod.
 */
@SpringBootApplication
@EnableConfigurationProperties(StubProperties.class)
public class SecStubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecStubApplication.class, args);
    }
}
