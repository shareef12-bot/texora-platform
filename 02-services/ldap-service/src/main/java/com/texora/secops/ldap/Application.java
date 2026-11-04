package com.texora.secops.ldap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LDAP Directory Service — Texora SecOps Platform (Service #6 of 8).
 *
 * <p>This Spring Boot application is the control/management plane for the LDAP
 * directory layer. It does NOT implement the LDAP wire protocol; all directory
 * engine access is isolated behind {@link com.texora.secops.ldap.sync.client.LdapEngineAdapter}.
 *
 * <p><b>IMPORTANT — Stub mode:</b> The LDAP engine product has not yet been
 * selected (see ADR-D5). {@link com.texora.secops.ldap.sync.client.StubLdapEngineAdapter}
 * is active until the real adapter is wired. Every call it receives is logged
 * at WARN level so engineers can see that the engine is not real.
 *
 * <p>Local dev port: {@code 8087} (override with {@code --server.port=8087} or
 * the {@code LDAP_SERVER_PORT} environment variable).
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
