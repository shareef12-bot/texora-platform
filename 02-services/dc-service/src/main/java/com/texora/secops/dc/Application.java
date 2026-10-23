package com.texora.secops.dc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Domain Controller (DC) service - the identity CONTROL PLANE.
 *
 * <p>DC does NOT implement LDAP bind/search. Directory operations are delegated to the LDAP Directory
 * service via Kafka (topic dc.sync.requested). Per ADR D-2, HRMS owns employee identity; dc_user is a
 * projection of HRMS data.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
