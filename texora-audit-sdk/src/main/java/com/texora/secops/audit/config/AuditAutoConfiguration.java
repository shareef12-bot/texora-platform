package com.texora.secops.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.audit.aspect.AuditingAspect;
import com.texora.secops.audit.kafka.AuditKafkaPublisher;
import com.texora.secops.audit.repository.AuditEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Auto-configuration for the Audit SDK.
 *
 * <p>Requires the following properties in each service:
 * <pre>
 * spring.application.name=my-service          # becomes serviceName for topic routing
 * spring.kafka.bootstrap-servers=kafka:9092
 * </pre>
 *
 * <p>Creates a dedicated async executor {@code auditTaskExecutor} so that audit
 * Kafka publishing never contends with the request-handling thread pool.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableAsync
@EnableScheduling
public class AuditAutoConfiguration {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Bean
    public AuditingAspect auditingAspect(AuditEventRepository repository,
                                          AuditKafkaPublisher kafkaPublisher,
                                          ObjectMapper objectMapper) {
        return new AuditingAspect(repository, kafkaPublisher, objectMapper, serviceName);
    }

    @Bean
    public AuditKafkaPublisher auditKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                    AuditEventRepository repository,
                                                    ObjectMapper objectMapper) {
        return new AuditKafkaPublisher(kafkaTemplate, repository, objectMapper, serviceName);
    }

    /**
     * Dedicated thread pool for async audit Kafka publishing.
     * Sized conservatively — audit events are low-volume but must not be lost.
     */
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
