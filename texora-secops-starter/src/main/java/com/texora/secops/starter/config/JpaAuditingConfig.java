package com.texora.secops.starter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enables Spring Data JPA auditing for {@code @CreatedDate} and
 * {@code @LastModifiedDate} fields declared on {@link com.texora.secops.starter.domain.BaseEntity}.
 *
 * <p>Services must set the base package for their own repositories:
 * <pre>{@code
 *   @EnableJpaRepositories(basePackages = "com.texora.secops.myservice.repository")
 * }</pre>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // Spring Data auditing is activated by the annotation alone.
    // The AuditingEntityListener on BaseEntity picks this up automatically.
}
