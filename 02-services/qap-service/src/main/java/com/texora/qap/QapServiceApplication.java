package com.texora.qap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * QAP (Quality Automation Platform) Service — Step #9 of the Texora Platform.
 *
 * Part A: API and contract test orchestration, test runs, results,
 *         failure → trace-id → deduplicated bug-ticket pipeline.
 * Part B: Selenium UI execution and issue tracker behind adapter interfaces
 *         with loud stubs (see README for production wiring instructions).
 */
@SpringBootApplication
@EnableAsync
public class QapServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QapServiceApplication.class, args);
    }
}
