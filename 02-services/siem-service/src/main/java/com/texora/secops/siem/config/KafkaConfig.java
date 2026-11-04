package com.texora.secops.siem.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring for two distinct purposes, kept deliberately separate:
 * <ul>
 *   <li>Consumption of the 12 upstream audit topics, done exclusively inside
 *       {@code MultiProtocolIngestionAdapter} (B.4) via the manual-ack container factory below,
 *       so a message is only committed once {@code IngestionOrchestrationService} has durably
 *       recorded it (buffered, retried, or DLQ'd) - never before (Requirement 1).</li>
 *   <li>Production of SIEM's own audit events to {@code siem.audit.events}, used by
 *       {@code audit.AuditEventPublisher} via the auto-configured {@code KafkaTemplate}.</li>
 * </ul>
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String SIEM_AUDIT_TOPIC = "siem.audit.events";

    @Bean
    public ConsumerFactory<String, String> ingestionConsumerFactory(KafkaProperties kafkaProperties,
                                                                      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put("spring.deserializer.key.delegate.class", StringDeserializer.class);
        props.put("spring.deserializer.value.delegate.class", StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> ingestionKafkaListenerContainerFactory(
            ConsumerFactory<String, String> ingestionConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(ingestionConsumerFactory);
        // Manual ack: IngestionOrchestrationService must durably record BUFFERED/RETRIED/DLQ
        // before we acknowledge, so a broker/consumer crash never silently loses an event.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);
        return factory;
    }

    /** Best-effort declarative topic creation for local/dev clusters with auto-create disabled;
     *  production topic provisioning is expected to be managed by platform infra-as-code. */
    @Bean
    public NewTopic siemAuditTopic() {
        return TopicBuilder.name(SIEM_AUDIT_TOPIC)
                .partitions(3)
                .replicas(3)
                .build();
    }
}
