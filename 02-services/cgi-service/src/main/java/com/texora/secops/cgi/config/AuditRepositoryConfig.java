package com.texora.secops.cgi.config;

import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.repository.AuditEventRepository;
import com.texora.secops.cgi.Application;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The audit SDK (AuditingAspect) needs its own AuditEventRepository and AuditEvent entity. Spring Boot only scans
 * the application package, so the SDK packages are added here (same as the other services).
 */
@Configuration
@EnableJpaRepositories(basePackageClasses = {Application.class, AuditEventRepository.class})
@EntityScan(basePackageClasses = {Application.class, AuditEvent.class})
public class AuditRepositoryConfig {
}