package com.texora.secops.starter.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
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
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing
public class JpaAuditingConfig {
    // Spring Data auditing is activated by the annotation alone.
    // The AuditingEntityListener on BaseEntity picks this up automatically.
}
