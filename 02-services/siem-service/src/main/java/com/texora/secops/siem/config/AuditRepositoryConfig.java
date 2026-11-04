package com.texora.secops.siem.config;

import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.repository.AuditEventRepository;
import com.texora.secops.siem.Application;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registers the shared texora-audit-sdk repository and entity (needed by the SDK's
 * AuditingAspect, used by @Audited) together with SIEM's own repositories and entities.
 * Same pattern as SEC's AuditRepositoryConfig.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = {
    Application.class,
    AuditEventRepository.class
})
@EntityScan(basePackageClasses = {
    Application.class,
    AuditEvent.class
})
public class AuditRepositoryConfig {
}