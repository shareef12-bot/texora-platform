package com.texora.secops.sso.config;

import com.texora.secops.audit.AuditSdk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * sso-service manages its own audit entity/table (SsoAuditEvent /
 * SsoAuditEventRepository) rather than the shared audit module's generic
 * AuditEvent table. AuditAutoConfiguration is therefore excluded (see
 * Application.java) since its auditingAspect/auditKafkaPublisher beans
 * require com.texora.secops.audit.repository.AuditEventRepository, which
 * this service intentionally does not provide. Only the entity-agnostic
 * AuditSdk publish primitive is needed here — re-declared manually below.
 */
@Configuration
@EnableAsync
public class AuditSdkConfig {

    @Bean
    public AuditSdk auditSdk(KafkaTemplate<String, String> kafkaTemplate) {
        return new AuditSdk(kafkaTemplate);
    }

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-kafka-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}