package com.texora.secops.ftp.config;

import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.repository.AuditEventRepository;
import com.texora.secops.ftp.Application;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registers the shared texora-audit-sdk repository and entity (needed by the SDK's AuditingAspect,
 * used by @Audited in FtpAuditTrail) together with FTP's own repositories and entities.
 * FTP's own AuditEvent entity and AuditEventRepository keep working under explicit names
 * (FtpAuditEvent / ftpAuditEventRepository) so they do not clash with the SDK's.
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