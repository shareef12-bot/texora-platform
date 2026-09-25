package com.texora.secops.sso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.texora.secops.audit.config.AuditAutoConfiguration;
/**
 * SSO / IAM Service — Texora SecOps platform, build order #1.
 * Single point of authentication trust: every other service validates
 * SSO-issued tokens and performs no credential checks of its own.
 */
@SpringBootApplication(exclude = { AuditAutoConfiguration.class })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
