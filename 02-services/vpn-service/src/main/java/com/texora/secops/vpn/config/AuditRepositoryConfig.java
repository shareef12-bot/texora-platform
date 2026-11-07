package com.texora.secops.vpn.config;

import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.repository.AuditEventRepository;
import com.texora.secops.vpn.Application;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackageClasses = {Application.class, AuditEventRepository.class})
@EntityScan(basePackageClasses = {Application.class, AuditEvent.class})
public class AuditRepositoryConfig {
}