package com.texora.secops.sec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Texora SecOps — Security Server (SEC).
 *
 * <p>Central security control plane for the Texora SecOps platform. Every other
 * service calls this synchronously for policy decisions. SEC-BR-001 and SEC-BR-002
 * are both Critical priority.</p>
 *
 * <p>Key invariants enforced by this service:</p>
 * <ul>
 *   <li>DEFAULT DENY — any evaluation that does not reach EXPLICIT_ALLOW returns DENY.</li>
 *   <li>NO SECRET MATERIAL — only vault paths and KMS key references transit this service.</li>
 *   <li>DUAL CONTROL — critical operations require N-of-M approval with separation of duties.</li>
 *   <li>TAMPER-EVIDENT AUDIT — hash-chained audit events verified on a schedule.</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        LOGGER.info("Starting Texora SecOps Security Server (SEC)");
        SpringApplication.run(Application.class, args);
    }
}
