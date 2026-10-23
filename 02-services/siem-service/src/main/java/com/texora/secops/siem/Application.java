package com.texora.secops.siem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SOC / SIEM Log Server.
 *
 * <p>Architecturally distinct from every other Texora SecOps service: this service is not a
 * producer of audit events but the platform-wide consumer of them. Every Kafka audit topic
 * (dc/ldap/sso/ftp/cgi/vpn/sec.audit.events, plus the five business-product topics) converges
 * here for ingestion, normalization, correlation, and alerting.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
